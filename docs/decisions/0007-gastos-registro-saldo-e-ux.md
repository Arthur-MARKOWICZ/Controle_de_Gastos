# ADR-007: Registro de gastos, saldo acumulável e UX de lançamento rápido

## Status

Proposto — aguarda aceite. Análise pré-implementação solicitada para a feature de **Gastos**.

## Data

2026-08-28

## Contexto

A fundação técnica (`docs/ideas/fundacao-tecnica.md:8-17`) exige:

* Registrar gastos manualmente com pouco esforço (mobile rápido, web para histórico/relatórios).
* Saldo não utilizado acumula para o mês seguinte; dinheiro não alocado é separado das verbas.
* Saldo negativo é permitido com alerta, sem bloquear o registro.
* Verbas têm natureza (`limite de gasto` | `meta de aporte` | `compromisso fixo`) e podem ser compartilhadas (participantes lançam, só o criador abastece/convida/encerra).
* Histórico com autoria, auditoria, autorização por proprietário/participante e `Money` como `BigDecimal` escala 2 (`Money.java:8-96`).

Estado atual do esqueleto:

* `backend/src/main/java/br/com/controlegastos/envelopes/domain/MonthlyAllocationPlan.java:1-46` e `EnvelopeBalance.java:1-36` modelam aporte/gasto em memória, sem persistência de verba nem de movimentação.
* `backend/src/main/java/br/com/controlegastos/income/IncomeService.java:1-85` já grava renda por `YearMonth` em `America/Sao_Paulo` e expõe `IncomeChangeConstraint.java:1-11` para que `envelopes` impeça reduzir renda abaixo da soma-base.
* `backend/src/main/java/br/com/controlegastos/money/Money.java:50-58` proíbe arredondamento (`RoundingMode.UNNECESSARY`) e `ledger`/`envelopes`/`audit` existem apenas como `ApplicationModule` (`ledger/package-info.java:1-2`).
* `web/src/app/dashboard.tsx:1-52` e `web/src/app/page.module.css:1-259` renderizam dados demonstrativos com design tokens reais (`globals.css:1-11` — `--accent:#146c43`, `--background:#f4f5f1`) e sem dependência de backend real.
* `web/src/auth/auth-client.ts:102-116` já centraliza refresh com `Navigator.locks` e `credentials: include`; `web/src/auth/auth-context.tsx:23-27` sync entre abas via `BroadcastChannel`.
* `mobile/shared/src/commonMain/kotlin/br/com/controlegastos/app/VerbasApp.kt:48-213` usa `Material3 lightColorScheme` com os mesmos tokens, mas `AuthGateway` é `UnavailableAuthGateway` — lançamento real ainda não existe.

Pergunta central: **onde e como modelar `gasto` para respeitar monólito modular, BRL exato, autorização, LGPD e lançamento rápido em web + mobile sem introduzir infraestrutura nova?**

---

## Análise da estrutura existente

### Backend — oportunidades e restrições (`backend/build.gradle.kts:1-29`)

* Stack confirmada: Java 25, Spring Boot 4.1, Modulith 2.1, JPA/Flyway/PostgreSQL 18. Proibido introduzir microserviços, fila ou cache distribuído sem ADR (`docs/decisions/0001-monorepo-e-monolito-modular.md:1-40`).
* `Money` já normaliza `NUMERIC(19,2)` e serialização deve ser string decimal + `currency:BRL` (`docs/decisions/0006-valores-monetarios-decimais-e-renda-mensal.md:21-34`). Qualquer `double` para dinheiro quebra a regra.
* `V1__create_identity_tables.sql:1-40` mostra padrão Flyway: PK UUID, `TIMESTAMPTZ`, índices por `user_id`. O mesmo padrão deve ser seguido para `envelope`/`ledger_entry`.
* `IncomeService` prova que `CurrentUser` + `Clock` + `ZoneId America/Sao_Paulo` é o padrão para derivar dono/mês/ator — gasto deve seguir igual, nunca receber `ownerId` do cliente.
* `docs/privacy/inventario-de-dados.md:13-16` classifica renda/verbas/movimentações como dado pessoal com retenção até exclusão da conta e exige autorização por recurso.

### Web (`web/package.json:1-16` — Next 16.2, React 19, vitest 4)

* Estrutura atual é `src/app` + `src/auth` sem `src/components`. Não há `react-query`/`swr` instalado, nem `zod`. `page.test.tsx:1-23` testa apenas `Dashboard` estático.
* `globals.css` define design system minimalista (verde floresta `#146c43`, serif Georgia para títulos, `--border:#d9ddd6`). `page.module.css` evita gradientes/roxos/padding exagerado — já foge do "AI aesthetic" descrito na skill.
* Layout é **mobile-first** real: `page.module.css:227-252` tem breakpoints 64rem/48rem/30rem e trata `prefers-reduced-motion`.

### Mobile (`mobile/shared/src/commonMain/kotlin/br/com/controlegastos/app/VerbasApp.kt:62-94`)

* Estado é `AuthState` (`Loading|Anonymous|Expired|Authenticated`) controlado por `AuthSessionController`. Lançamento rápido é um `Button` sem ação (`VerbasApp.kt:173`).
* Alvo validável é Android; iOS é código compartilhado não testado (`docs/decisions/0004-kmp-android-primeiro.md`). Toda UX deve degradar graceful se `AuthGateway` falhar.

---

## Decisão recomendada

### 1. Backend — modelo de domínio (fonte única)

**1.1. Separação de responsabilidades entre módulos:**

```
envelopes : define verba, natureza, participantes, valor-base e cálculo de alocação
ledger    : registra movimentações (gastos/aportes manuais), calcula saldo e emite eventos
sharing   : reexporta leitura de participantes de envelopes (sem duplicar tabela)
audit     : consome ApplicationEvent de ledger para trilha
```

*Motivo:* `MonthlyAllocationPlan` já deriva `unallocated` da renda; `EnvelopeBalance.spend()` só subtrai. Se `envelopes` também persistisse gastos, haveria acoplamento circular com `income` e perda de trilha auditável. `ledger` como append-only preserva histórico e permite saldo negativo com alerta.

**1.2. Entidades JPA (Flyway V2/V3):**

```sql
-- V2__create_envelope_tables.sql
CREATE TABLE envelope (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
  name VARCHAR(80) NOT NULL,
  purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('LIMIT','GOAL','FIXED')),
  base_amount NUMERIC(19,2) NOT NULL CHECK (base_amount >= 0),
  created_at TIMESTAMPTZ NOT NULL,
  archived_at TIMESTAMPTZ,
  version BIGINT NOT NULL
);
CREATE INDEX envelope_owner_idx ON envelope(owner_id);

CREATE TABLE envelope_participant (
  envelope_id UUID NOT NULL REFERENCES envelope(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
  added_at TIMESTAMPTZ NOT NULL,
  added_by UUID NOT NULL,
  PRIMARY KEY (envelope_id, user_id)
);

-- V3__create_ledger_tables.sql
CREATE TABLE ledger_entry (
  id UUID PRIMARY KEY,
  envelope_id UUID NOT NULL REFERENCES envelope(id) ON DELETE CASCADE,
  owner_id UUID NOT NULL, -- denormalizado para checagem rápida por recurso
  author_id UUID NOT NULL REFERENCES user_account(id),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  kind VARCHAR(12) NOT NULL CHECK (kind IN ('EXPENSE','CONTRIBUTION')),
  occurred_at DATE NOT NULL, -- data informada pelo usuário, sem hora
  description VARCHAR(140),   -- opcional, evita LGPD por excesso
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ledger_envelope_occurred_idx ON ledger_entry(envelope_id, occurred_at);
CREATE INDEX ledger_owner_idx ON ledger_entry(owner_id);
```

*Por quê:*
* `UUID` alinha com `user_account.id` e `MonthlyIncomeId` (nunca e-mail como chave — `docs/privacy/requisitos-de-seguranca.md`).
* `NUMERIC(19,2)` + `MoneyJpaConverter` já existente reaproveita conversão.
* `description` opcional e limitada a 140c cumpre minimização (`docs/privacy/inventario-de-dados.md:15`).
* `occurred_at` como `DATE` evita ambiguidade de fuso; ordenação e saldo mensal usam `YearMonth` como em `IncomeService.BUSINESS_ZONE`.
* Sem `balance` materializado — saldo é **derivado**: `saldo(mês) = saldo_anterior + aportes_verba - gastos` + `carry`. O carry é computado via query de soma; snapshot mensal só se `EXPLAIN ANALYZE` provar necessidade (evita complexidade prematura).

**1.3. Regras de domínio (TDD primeiro):**

```java
// envelopes/domain/Envelope.java (aggregate)
public Envelope rename(String name) // 1..80, trim, não vazio
public void archive(Instant now)    // só owner, idempotente
public boolean canBeSeenBy(UUID userId)
public boolean canSpend(UUID userId) // owner || participant

// ledger/domain/LedgerEntry.java
public static LedgerEntry expense(EnvelopeId, Money amount, LocalDate occurredAt, String description, UUID authorId)
// valida Money > 0, escala 2, occurredAt <= today(Sao_Paulo) + 1 dia (tolerância), description nullable

// ledger/application/LedgerService.java
@Transactional
public LedgerEntry registerExpense(UUID envelopeId, Money amount, LocalDate occurredAt, String description)
// 1. resolve CurrentUser 2. carrega Envelope 3. checa canSpend 4. persiste entry 5. publica EnvelopeSpentEvent
// NUNCA bloqueia se saldo ficar negativo — apenas retorna EnvelopeBalance com isNegative=true para o caller decidir alerta

@Transactional(readOnly=true)
public EnvelopeBalance balanceOf(UUID envelopeId, YearMonth month)
// soma ledger_entry no mês + carry recursivo (ou iterativo por meses)
```

*Por quê não bloquear:* requisito explícito "permitir saldo negativo com alerta, sem impedir registro" (`fundacao-tecnica.md:14`). Bloqueio seria divergência de ADR.

**1.4. Autorização e LGPD:**

* Toda query filtra por `owner_id = currentUser OR EXISTS participant`. Nunca confiar em `envelopeId` vindo do cliente sem checar dono/participante.
* `audit` consome `EnvelopeSpentEvent` e grava `who, envelopeId, amount, occurredAt, createdAt` sem descrição completa se sensível.
* Logs não registram `amount` detalhado nem `description` — apenas `envelopeId` e `authorId` hash.

**1.5. Contrato REST (OpenAPI em `docs/api/openapi.yaml`):**

```
POST   /api/v1/envelopes                 { name, purpose, baseAmount } -> 201 { id, ... }
GET    /api/v1/envelopes?month=2026-08   -> [{ id, name, purpose, baseAmount, available, isNegative, role }]
POST   /api/v1/envelopes/{id}/entries    { kind:"EXPENSE", amount:{amount:"120.00",currency:"BRL"}, occurredAt:"2026-08-27", description? } -> 201
GET    /api/v1/envelopes/{id}/entries?month=2026-08&kind=EXPENSE
GET    /api/v1/ledger/balance?month=2026-08 -> { income:{amount,currency}, allocated, unallocated, envelopes:[...] }
Errors: 400 ProblemDetail { code:"envelope.not_found"|"amount.invalid_scale"|"allocation.exceeds_income" } sem stack
```

*Strings decimais* com duas casas obrigatórias, `currency:BRL` — igual a `IncomeSnapshot` e `docs/api/README.md:12-13`.

**1.6. Validação de renda:**

`envelopes` implementa `IncomeChangeConstraint.minimumIncomeFor(ownerId, month)` retornando `SUM(base_amount) WHERE archived_at IS NULL`. Assim `IncomeService.requireAllowedAmount` já impede reduzir renda abaixo das verbas-base sem criar dependência inversa.

---

### 2. Web — UI de lançamento rápido (Next.js)

**2.1. Arquitetura de componentes (colocação):**

```
src/components/
  EnvelopeCard/
    EnvelopeCard.tsx        # apresentação pura (props: envelope, onRegisterExpense)
    EnvelopeCard.test.tsx
    useEnvelopeBalance.ts   # hook de formatação BRL (Intl.NumberFormat pt-BR)
  ExpenseForm/
    ExpenseForm.tsx         # Dialog acessível + validação
    ExpenseForm.test.tsx
    useExpenseForm.ts       # estado local, máscara BRL, submit
  BalanceSummary/
    BalanceSummary.tsx      # reutiliza layout de dashboard.tsx:32-34
src/hooks/
  useEnvelopes.ts           # Server State: fetch via AuthClient.request()
  useLedger.ts
```

*Por quê colocation:* skill exige `TaskList/TaskList.tsx` + `use-task-list.ts`. Evita `components/` gigante por camada técnica.

**2.2. Padrões — composição, não configuração:**

```tsx
// Bom: composição
<Dialog open={open} onClose={close}>
  <DialogHeader><DialogTitle>Registrar gasto</DialogTitle></DialogHeader>
  <ExpenseForm envelope={envelope} onSuccess={close} />
</Dialog>

// Evitar: <ExpenseDialog envelopeId="..." showHeader showFooter variant="large" />
```

**2.3. Estado — o mais simples que funciona:**

* `useState` para `amount`, `occurredAt`, `description` dentro de `ExpenseForm`.
* URL state (`searchParams ?month=2026-08`) para filtros/compartilhável — `useEnvelopes(month)` lê da URL.
* Server state via `AuthClient.request()` (já tem `Authorization` + `refresh` + `BroadcastChannel`); **não** adicionar `tanstack-query` agora — sem cache complexo no corte. Migrar para `SWR` só se polling ou deduplicação provar necessário (ADR futuro).
* Otimismo: `onMutate` atualiza `available` localmente e reverte em `onError` — sem biblioteca, apenas `setEnvelopes(prev => ...)`.

**2.4. Design system — anti-AI-aesthetic:**

* Reusar tokens `globals.css:1-11` (`--accent`, `--surface`, `--border`, `--focus`). Nada de roxo/indigo, gradientes pesados ou `rounded-2xl` sem hierarquia — já usado `0.375rem`/`8px` em `page.module.css:29,148`.
* Tipografia: `h1` uma vez por página (Visão geral), `h2` por seção (Suas verbas), `h3` por verba — igual a `dashboard.tsx:39`.
* Espaçamento em escala `0.25rem` (`gap:0.75rem`, `padding:1.5rem`) — nunca `13px`.
* Cores semânticas: `text-primary` → `var(--foreground)`, `bg-surface` → `var(--surface)`. Contraste 4.5:1 já atendido (verde `#146c43` sobre branco).
* Estados vazios/erro esqueleto: `aria-busy`, `role="status"` e `role="alert"` — seguir `page.tsx:9` (`aria-busy="true"`).

**2.5. Acessibilidade WCAG 2.1 AA (obrigatório):**

```tsx
<button aria-label="Registrar gasto em Combustível" onClick={open}>Registrar gasto</button>
<label htmlFor="expense-amount">Valor (BRL)</label>
<input id="expense-amount" inputMode="decimal" aria-describedby="amount-help" />
<progress aria-label={`Progresso de ${envelope.name}`} value={pct} max={100} />
// Dialog: trap focus, Esc fecha, foco retorna ao trigger, aria-modal
```

* Keyboard: Tab navega, Enter/Space ativa, Esc fecha.
* Não usar `div onClick` — usar `<button>`.
* Validar com `axe-core` em `pnpm test` (adicionar `@axe-core/react` em dev).

**2.6. Responsivo — mobile-first Tailwind-like mas com CSS Modules:**

* Reaproveitar breakpoints existentes (`page.module.css:227-252`). `ExpenseForm` em `Dialog` vira `bottom-sheet` em `≤48rem` (`position:fixed; inset:auto 0 0 0; border-radius:1rem 1rem 0 0`).
* Testar 320, 768, 1024, 1440 — skill checklist.

**2.7. Máscara e validação BRL:**

* Input `type="text" inputMode="decimal"` com máscara `pt-BR` (vírgula) mas envio como `amount:"120.00"` (ponto) — `Intl.NumberFormat('pt-BR')` para exibição, `Money.brl(string)` para validação (escala 2, sem arredondar).
* `occurredAt` default `today` em `America/Sao_Paulo` via `new Date().toLocaleDateString('en-CA',{timeZone:'America/Sao_Paulo'})`.
* Erros de API exibidos via `ProblemDetail.code` → mensagem humana sem expor `amount` em log.

---

### 3. Mobile — KMP Android (iOS preservado)

* `shared/src/commonMain/kotlin/br/com/controlegastos/app/ExpenseContract.kt` espelha web: `data class ExpenseDraft(amount:String, occurredAt:String, description:String?)`.
* `AuthGateway` ganha `postExpense(envelopeId, draft)` e `getBalances(month)` — implementado com `Ktor` e token em memória (mesmo padrão de `AuthSessionController`).
* Tela `Dashboard` ganha `FloatingActionButton` "Registrar gasto" + `ModalBottomSheet` com `OutlinedTextField` e `DatePicker` (Material3).
* Estado: `mutableStateOf` + `LaunchedEffect` + `rememberCoroutineScope` — sem `ViewModel` extra no corte; extrair quando >200 linhas (skill red flag).

---

### 4. Transversal — privacidade, auditoria, testes

* Atualizar `docs/privacy/inventario-de-dados.md` com `ledger_entry`.
* Cada mutação publica `ApplicationEvent` consumido por `audit` e `notifications` (alerta de saldo negativo) — sem broker.
* TDD: `LedgerServiceTest` com `Testcontainers PostgreSQL` (igual a `backend/src/test`), `ExpenseForm.test.tsx` com `@testing-library/react` + `vitest`, `AuthSessionControllerTest` para mobile.

---

## Alternativas consideradas — vantagens e desvantagens

| Alternativa | Vantagem | Desvantagem | Por que não foi escolhida agora |
|---|---|---|---|
| **A1. Saldo materializado em `envelope_balance` mensal** (trigger/coluna) | Leitura O(1), bom para relatórios pesados | Duplica verdade, precisa backfill, race em concorrência, migrações mais complexas | Rejeitada no corte; saldo derivado é simples e correto. Materializar só se `p95 >200ms` em `EXPLAIN` |
| **A2. Event Sourcing puro (tabela de eventos + projeção)** | Histórico imutável perfeito, replay | Over-engineering para VPS única, curva de aprendizado, sem suporte nativo Spring Modulith | Rejeitada; append-only `ledger_entry` já é event log suficiente |
| **A3. `ledger` como sub-tabela de `envelopes` (FK sem módulo próprio)** | Menos boilerplate | Quebra limite Modulith, impede `sharing`/`audit` consumirem eventos, acopla renda/verbas/gastos | Rejeitada; viola `AGENTS.md: Preserve a arquitetura de monólito modular` |
| **A4. Bloquear gasto quando negativo** | Sensação de "controle" | Viola requisito explícito (`fundacao-tecnica.md:14`), gera fricção no lançamento rápido, exige desbloqueio manual | Rejeitada — alerta + `isNegative` é a direção confirmada |
| **A5. `double`/`long centavos` no JSON** | Familiar para alguns clientes | Perde precisão/quebra ADR-006, exige arredondamento, `Money.java:88` proíbe | Rejeitada — string decimal é contrato |
| **A6. Web com `tanstack-query` + `zod` desde o dia 1** | Cache, deduplicação, validação runtime | +30kB bundle, API ainda é simples (CRUD + mês), `AuthClient` já tem refresh/lock | Adiar; adicionar quando houver polling, paginação ou validação compartilhada web/mobile |
| **A7. Formulário em página dedicada `/gastos/novo` em vez de Dialog** | URL compartilhável, sem trap focus | Quebra fluxo "lançamento rápido" (2 taps viram navegação + volta), mobile precisa de bottom-sheet | Rejeitada para o corte; página pode existir como fallback acessível, mas Dialog/bottom-sheet é primário |
| **A8. Notificação push imediata para todo gasto** | Feedback instantâneo | Spam, custo Firebase/APNs ainda indefinido (`fundacao-tecnica.md:86`), LGPD exige opt-in | Rejeitada; notificar só para `isNegative` ou verba compartilhada, com consentimento |
| **A9. `occurred_at` como `TIMESTAMPTZ`** | Precisão de hora | Complexidade de fuso, usuário pensa em "dia" não "instante", desalinhado com `MonthlyIncomeId` (YearMonth) | Rejeitada; `DATE` + `BUSINESS_ZONE` alinha com renda mensal |
| **A10. Máscara BRL com `type="number"`** | Nativo | Locales diferentes enviam `,` vs `.`, `step` quebra em pt-BR, impossível garantir duas casas sem arredondar | Rejeitada; `type="text" inputMode="decimal"` + `Intl.NumberFormat` é correto |

---

## Consequências

* `backend` ganha 2 migrações Flyway, 2 entidades, 1 serviço e 1 evento — sem nova dependência infra.
* `web` ganha 3 componentes colocados, 1 dialog acessível e testes `vitest` + `axe` — sem nova lib no corte.
* `mobile` ganha 1 `Contract` + 1 `bottom-sheet` — Android validável, iOS preservado.
* Toda nova coluna pessoal exige atualizar `docs/privacy/inventario-de-dados.md` e `docs/decisions/` se mudar contrato público.
* `make check` (`backend ./gradlew test` + `web pnpm test` + `pnpm lint`) deve passar antes de merge — `AGENTS.md`.

## Passos de implementação (TDD)

1. `Money` + `EnvelopeBalance` já têm testes — estender com `LedgerEntry` (escala, futuro, descrição longa).
2. `LedgerServiceTest` com `Testcontainers`: registra gasto, checa `isNegative`, checa autorização participante vs estranho, checa `IncomeChangeConstraint` não quebrado.
3. `envelopes` expõe `IncomeChangeConstraint` e `GET /envelopes`.
4. `web`: `ExpenseForm.test.tsx` cobre máscara, submit, erro 400, foco e `role="progressbar"` negativo (vermelho + ícone, não só cor).
5. `mobile`: `ExpenseSheetTest` cobre validação e cancelamento.
6. Atualizar `docs/api/openapi.yaml` e `README.md` (comandos).

## Verificação (skill checklist)

* [ ] Backend renderiza sem `Money` com escala >2; `POST /entries` retorna `201` e `balance.isNegative` correto.
* [ ] Web: Tab navega todo fluxo, Dialog trap focus, `axe` sem violações, 320/768/1024/1440 OK.
* [ ] Mobile: `Registrar gasto` acessível via TalkBack, `contentDescription` em progresso.
* [ ] LGPD: `ledger_entry` no inventário, logs sem `amount`/`description`, exportação inclui entradas do titular.

## Fontes

* `docs/ideas/fundacao-tecnica.md:1-87`
* `docs/decisions/0001-monorepo-e-monolito-modular.md:1-40` e `0006-valores-monetarios-decimais-e-renda-mensal.md:1-79`
* `backend/src/main/java/br/com/controlegastos/money/Money.java:1-97`
* `backend/src/main/java/br/com/controlegastos/income/IncomeService.java:1-85`
* `web/src/app/dashboard.tsx:1-52`, `globals.css:1-59`, `page.module.css:1-259`
* `mobile/shared/src/commonMain/kotlin/br/com/controlegastos/app/VerbasApp.kt:1-213`
* Spring Modulith — https://docs.spring.io/spring-modulith/reference/
* WCAG 2.1 AA — https://www.w3.org/WAI/WCAG21/quickref/
* Next.js App Router — https://nextjs.org/docs/app/getting-started/installation
