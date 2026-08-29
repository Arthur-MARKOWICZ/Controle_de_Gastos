# Plano — Verbas no front-end web com integração ao backend

> Conectar a web (Next.js) às regras de verbas já modeladas no backend (monólito modular Spring Modulith) e substituir o dashboard demonstrativo por dados reais, mantendo BRL exato, autorização por recurso, LGPD e lançamento rápido.

- **Data:** 2026-08-28
- **Status:** Proposto — pronto para execução
- **Alvo web validável:** Next.js 16.2 + React 19 (Android validável no mobile, iOS preservado mas não testado sem macOS/Xcode)
- **Fonte de verdade:** `docs/ideas/fundacao-tecnica.md`, `docs/decisions/0006-valores-monetarios-decimais-e-renda-mensal.md`, `docs/decisions/0007-gastos-registro-saldo-e-ux.md`, `AGENTS.md`

---

## 1. Objetivo

Entregar na web o fluxo completo de verbas com dados reais:

1. Listar verbas do mês com saldo disponível, natureza e alerta de saldo negativo.
2. Criar/editar/arquivar verba com `baseAmount` em BRL (duas casas, sem arredondamento).
3. Registrar gasto em uma verba (permitir saldo negativo com alerta, sem bloquear).
4. Exibir resumo de renda do mês: `renda`, `já reservado`, `não alocado` e `uso %`.
5. Navegar por mês (`?month=YYYY-MM` em `America/Sao_Paulo`) e manter estado compartilhável via URL.
6. Tudo atrás de autenticação (`identity` com JWT curto + refresh opaco) e respeitando proprietário/participante.

Fora de escopo neste corte: microserviços/filas/cache distribuído, integração bancária, offline completo, multi-moeda, notificações push, relatórios avançados (`reporting`).

---

## 2. Premissas confirmadas

- Monólito modular preservado — `envelopes`, `ledger`, `income`, `identity`, `sharing`, `audit` como `ApplicationModule` (`backend/src/main/java/br/com/controlegastos/envelopes/package-info.java:1`, `ledger/package-info.java:1`).
- Dinheiro sempre `BigDecimal` escala 2 + `NUMERIC(19,2)` + JSON como string decimal (`docs/decisions/0006-valores-monetarios-decimais-e-renda-mensal.md:21-29`, `backend/src/main/java/br/com/controlegastos/shared/money/Money.java:84-96`).
- Autorização por recurso: `owner_id` e `envelope_participant` — nunca confiar em `ownerId` vindo do cliente (`docs/privacy/inventario-de-dados.md:13-16`).
- TDD obrigatório para regra de negócio (`AGENTS.md`).
- Web mantém design tokens atuais (`web/src/app/globals.css:1-11` — `--accent:#146c43`, `--surface:#ffffff`, `--border:#d9ddd6`, `--background:#f4f5f1`) e CSS Modules sem introduzir Tailwind/roxo/gradientes pesados.

---

## 3. O que já está implementado no backend — verbas (para o front consumir)

### 3.1 Núcleo monetário compartilhado

- `Money` imutável `BRL` (`backend/src/main/java/br/com/controlegastos/shared/money/Money.java:8-96`):
  - Construtores só por `String` ou `BigDecimal`; `setScale(2, UNNECESSARY)` — valores com >2 casas lançam `IllegalArgumentException`.
  - `PRECISION=19, SCALE=2`, `toPlainString()` para serialização, `add`/`subtract`/`compareTo`/`isNegative`.
  - Converter JPA `MoneyJpaConverter` já existente para `NUMERIC(19,2)`.

### 3.2 Módulo `envelopes` — domínio puro (sem persistência/REST ainda)

- `BaseAllocation` (`backend/src/main/java/br/com/controlegastos/envelopes/domain/BaseAllocation.java:6-17`): `record(envelopeId, Money amount)` com validação de `amount >= 0` e `envelopeId` não vazio.
- `MonthlyAllocationPlan` (`backend/src/main/java/br/com/controlegastos/envelopes/domain/MonthlyAllocationPlan.java:7-46`):
  - `create(Money monthlyIncome, List<BaseAllocation>)` calcula `unallocated = income - sum(allocations)`.
  - Lança `AllocationExceedsIncomeException` se `sum > income` e expõe `excess` para mensagem 409.
  - Construtor canônico valida `unallocated` consistente — evita estado inconsistente mesmo via reflexão/JPA.
- `AllocationExceedsIncomeException` (`backend/src/main/java/br/com/controlegastos/envelopes/domain/AllocationExceedsIncomeException.java:5-17`).
- `EnvelopeBalance` (`backend/src/main/java/br/com/controlegastos/envelopes/domain/EnvelopeBalance.java:6-36`):
  - `startWith(Money)`, `allocate(Money)`, `spend(Money)`, `isNegative()` — gasto não bloqueia negativo, apenas sinaliza.
  - Saldo acumulável: `saldo(mês seguinte) = saldo(atual) + aportes - gastos` (carry implícito no domínio, ainda sem query materializada).
- Testes de domínio existentes e verdes:
  - `backend/src/test/java/br/com/controlegastos/envelopes/domain/MonthlyAllocationPlanTest.java:13-51` — soma, rejeição por excesso, rejeição de base negativa.
  - `backend/src/test/java/br/com/controlegastos/envelopes/domain/EnvelopeBalanceTest.java:11-28` — carry e gasto acima do saldo com `isNegative=true`.

### 3.3 Módulo `income` — já persistido e integrado

- `IncomeService` (`backend/src/main/java/br/com/controlegastos/income/application/IncomeService.java:24-108`): renda por `YearMonth` em `BUSINESS_ZONE = America/Sao_Paulo`, histórico append-only idempotente, deriva `ownerId` de `AuthenticationService.currentUserId()`.
- `IncomeChangeConstraint` (`backend/src/main/java/br/com/controlegastos/income/application/IncomeChangeConstraint.java:9-11`): `@FunctionalInterface Money minimumIncomeFor(UUID ownerId, YearMonth month)` — ponto de extensão para `envelopes` impedir redução de renda abaixo de `SUM(base_amount)` sem dependência circular.
- `IncomeRevision` (`backend/src/main/java/br/com/controlegastos/income/domain/IncomeRevision.java:16-77`): `income_revision` com `Money` + `effective_month` + `actor_user_id`.
- Contrato REST já publicado em `docs/api/openapi.yaml:71-125` (`PUT /income`, `GET /income?month=`, `GET /income/history` com `ProblemDetail`).

### 3.4 O que ainda NÃO existe (gap que este plano fecha)

| Camada | Faltante | Por que importa para o front |
|---|---|---|
| `envelopes` | Entidade `envelope` + `envelope_participant`, Flyway `V2`, serviço, `IncomeChangeConstraint` concreto, controller | Sem isso `GET /envelopes` não existe |
| `ledger` | Entidade `ledger_entry`, Flyway `V3`, serviço `registerExpense`/`balanceOf`, eventos | Sem isso `POST /envelopes/{id}/entries` e saldo real não existem |
| `sharing`/`audit` | Consumo de `EnvelopeSpentEvent` | Logs/auditoria de quem gastou onde |
| `docs/api/openapi.yaml` | Endpoints de verbas/ledger | Front não tem contrato tipado |
| `web` | Cliente API tipado, hooks, componentes reais | Dashboard atual é mock (`web/src/components/Dashboard/Dashboard.tsx:3-7`) |

> O plano assume que `ADR-007` (`docs/decisions/0007-gastos-registro-saldo-e-ux.md`) será aceito e que as migrações seguirão o padrão de `V1__create_identity_tables.sql` (PK `UUID`, `TIMESTAMPTZ`, índice por `owner_id`).

---

## 4. Estado atual do front-end web

- Stack: `web/package.json:12-16` — Next 16.2.11, React 19.2.4, vitest 4.1.11, jsdom, eslint 9, `pnpm`.
- Estrutura: `web/src/app` (App Router) + `web/src/auth` + `web/src/components/Dashboard` + `web/src/components/AuthScreen`. Sem `src/hooks`/`src/lib` dedicados ainda.
- Auth: `web/src/auth/auth-client.ts:21-147` centraliza `register/login/restore/refresh/request` com `credentials: include`, `Bearer` + `Navigator.locks` (`withBrowserLock`) e `BroadcastChannel` em `auth-context.tsx:23-27` para sync entre abas. `request(path, init, retry, notifyExpiration)` já faz refresh automático em 401.
- Dashboard: `web/src/components/Dashboard/Dashboard.tsx:15-51` e `Dashboard.module.css:1-259` renderizam dados estáticos (`envelopes`/`activities` mock) com layout mobile-first real (breakpoints 64/48/30rem, `prefers-reduced-motion`). `page.tsx:7-17` alterna `AuthScreen` ↔ `Dashboard` por `useAuth()`.
- Design tokens: `globals.css:1-11` (`--accent`, `--surface`, `--border`, `--focus`). Tipografia Georgia para `h1/h2`, escala de espaçamento `0.25rem`. Sem `tanstack-query`/`zod` instalados — `AuthClient.request` é a camada de server state atual.
- Testes web: `page.test.tsx:1-23` cobre apenas render estático; `auth-client.test.ts` cobre refresh/lock.

---

## 5. Arquitetura alvo — front ↔ back

### 5.1 Contrato REST proposto (a adicionar em `docs/api/openapi.yaml`)

```
POST   /api/v1/envelopes
  Body: { name: string(1..80), purpose: "LIMIT"|"GOAL"|"FIXED", baseAmount: { amount:"120.00", currency:"BRL" } }
  -> 201 { id, ownerId, name, purpose, baseAmount, createdAt, archivedAt?, version }
  Errors: 400 amount.invalid_scale | name.invalid | purpose.invalid, 401, 409 allocation.exceeds_income { excess }

GET    /api/v1/envelopes?month=2026-08
  -> 200 [{ id, name, purpose, baseAmount, available:{amount,currency}, isNegative:boolean, role:"OWNER"|"PARTICIPANT", envelopeBalanceLastUpdatedAt }]

GET    /api/v1/envelopes/{id}
  -> 200 { ... } | 404 envelope.not_found (sem vazar existência se não autorizado)

PATCH  /api/v1/envelopes/{id}  { name? , baseAmount? }  -> 200
POST   /api/v1/envelopes/{id}/archive                  -> 204 (idempotente, só owner)

POST   /api/v1/envelopes/{id}/entries
  Body: { kind:"EXPENSE", amount:{amount,currency}, occurredAt:"2026-08-27", description?:string(0..140) }
  -> 201 { id, envelopeId, kind, amount, occurredAt, description, authorId, createdAt }
  Errors: 400 amount.invalid_scale | amount.must_be_positive | occurredAt.in_future, 403 not_participant

GET    /api/v1/envelopes/{id}/entries?month=2026-08&kind=EXPENSE&page=0&size=20
  -> 200 { items:[...], page, size, hasNext }

GET    /api/v1/ledger/summary?month=2026-08
  -> 200 { income:{amount,currency,effectiveFrom}, allocated:{amount,currency}, unallocated:{amount,currency}, usagePct:number, envelopes:[...] }
```

Regras transversais (já valem para `income`):

- `amount` sempre string com exatamente duas casas + `currency:"BRL"` (`docs/api/README.md:3-14`).
- Erros em `application/problem+json` com `code` estável, sem stack, sem `amount`/`description` em logs.
- Autorização: `owner_id = currentUser OR EXISTS (envelope_participant WHERE user_id = currentUser)` — 404 genérico se não autorizado.
- `occurredAt` como `DATE` (sem hora), default `today` em `America/Sao_Paulo`; validação tolera `today+1` para skew de cliente.
- Paginação `page>=0, 1<=size<=100` igual a `income/history`.

### 5.2 Fluxo de integração (sequência)

```
[Browser] -- AuthClient.request("/api/v1/envelopes?month=2026-08") --> [Caddy] --> [Spring Security: Bearer JWT]
  -> IncomeService.find(YearMonth) + EnvelopeRepository.findVisibleByOwnerOrParticipant(ownerId) + LedgerService.balanceOf(envelopeId, month)
  -> MonthlyAllocationPlan.create(income, baseAllocations) valida não alocado
  -> JSON: [{ available, isNegative, role }]
  <- AuthClient trata 401 com refresh (locks) e BroadcastChannel sync
```

- Derivação de `ownerId` sempre do `AuthenticationService.currentUserId()` — front nunca envia `ownerId`.
- `allocation.exceeds_income` (409) exibido como mensagem humana: "Renda de R$ 5.000,00 não cobre R$ 5.500,00 em verbas (excesso R$ 500,00)".
- Saldo negativo: `isNegative=true` renderiza alerta visual (ícone + texto) além de cor — não só cor (WCAG).

### 5.3 Estrutura de pastas alvo (colocation, sem camada técnica global)

```
web/src/
  lib/
    api.ts                 # wrapper tipado sobre AuthClient.request + helpers de Money/BRL
    money.ts               # formatBRL(string) -> "R$ 1.200,00", parseBRLInput(string) -> "1200.00", validateAmount
    dates.ts               # todaySaoPaulo(): YYYY-MM-DD, parseMonthParam()
  hooks/
    useEnvelopes.ts        # GET /envelopes?month, estado, loading, error, mutate
    useLedgerSummary.ts    # GET /ledger/summary?month
    useEnvelopeEntries.ts  # GET /envelopes/{id}/entries
  components/
    Dashboard/             # existente, passa a receber props reais (income, envelopes, summary)
    EnvelopeCard/
      EnvelopeCard.tsx     # apresentação pura (progress, isNegative, role)
      EnvelopeCard.test.tsx
    EnvelopeForm/
      EnvelopeForm.tsx     # Dialog acessível para criar/editar verba
      useEnvelopeForm.ts
      EnvelopeForm.test.tsx
    ExpenseForm/
      ExpenseForm.tsx      # Dialog/bottom-sheet para registrar gasto
      useExpenseForm.ts
      ExpenseForm.test.tsx
    BalanceSummary/
      BalanceSummary.tsx   # Renda / Já reservado / Não alocado / Uso %
    ui/
      Dialog.tsx           # Dialog acessível reutilizável (trap focus, Esc, aria-modal)
      Field.tsx            # Label + input + aria-describedby
```

> Por que colocation: cada componente com seu hook/teste/CSS Module evita `components/` gigante por camada (exigência da skill `frontend-ui-engineering`).

---

## 6. Design system e UX — anti AI-aesthetic

- Reusar tokens `globals.css` e `Dashboard.module.css` existentes: `--accent` para ação primária, `--surface` para cards, `--border` para separadores, `--focus` para ring. Nada de roxo/indigo, gradientes pesados ou `rounded-2xl` sem hierarquia — manter `0.375rem`/`0.25rem`.
- Tipografia: 1× `h1` por página (Visão geral), `h2` por seção (Suas verbas), `h3` por verba.
- Espaçamento em escala `0.25rem` (`gap:0.75rem`, `padding:1.5rem`), nunca `13px`.
- Estados: `aria-busy`/`role="status"` para loading, `role="alert"` para erro, `progress` com `aria-label`, `aria-describedby` para ajuda de campo.
- Responsivo mobile-first:
  - `≤64rem`: sidebar vira top-nav horizontal (já em `Dashboard.module.css:227-233`).
  - `≤48rem`: `Dialog` vira `bottom-sheet` (`position:fixed; inset:auto 0 0 0; border-radius:1rem 1rem 0 0`).
  - Testar 320 / 768 / 1024 / 1440 (checklist da skill).
- Máscara BRL:
  - Input `type="text" inputMode="decimal"` com vírgula na exibição (`Intl.NumberFormat('pt-BR')`) e ponto no envio (`"120.00"`).
  - `occurredAt` default `todaySaoPaulo()` via `toLocaleDateString('en-CA',{timeZone:'America/Sao_Paulo'})`.
  - Validação: escala 2, `>0`, `occurredAt <= today+1`, descrição 0..140.

---

## 7. Plano de execução em fases (TDD, incrementais, cada fase mergeável)

| Fase | Objetivo | Entregas | Critério de pronto |
|---|---|---|---|
| **0 — Contrato e fundação** | Congelar contrato e preparar web | Atualizar `docs/api/openapi.yaml` com envelopes/ledger, criar `web/src/lib/money.ts` + `dates.ts` + testes, adicionar tipos `Envelope`, `LedgerEntry`, `Summary` | `openapi.yaml` valida, `pnpm test` verde |
| **1 — Backend leitura mínima** | Tornar `GET /envelopes?month` e `GET /ledger/summary?month` reais | Flyway `V2__create_envelope_tables.sql` + `V3__create_ledger_tables.sql`, entidades, `EnvelopeService` + `IncomeChangeConstraint` concreto, `LedgerService.balanceOf`, controllers, `ApplicationEvent` | `backend ./gradlew test` com Testcontainers cobre `canSee/canSpend`, `MonthlyAllocationPlan` + `EnvelopeBalance` integrados, `make check` verde |
| **2 — Web lista real** | Substituir mock por dados reais | `lib/api.ts` tipado sobre `AuthClient.request`, `hooks/useEnvelopes` + `useLedgerSummary`, `Dashboard` passa a receber `envelopes/summary` reais, `EnvelopeCard` com `isNegative` e `role` | Lista reflete renda real, `isNegative` com ícone+texto, loading/erro/vazio acessíveis, sem regressão visual |
| **3 — Criar/editar/arquivar verba** | Fechar ciclo de configuração web | `EnvelopeForm` + `Dialog`, `POST/PATCH /envelopes`, `POST /envelopes/{id}/archive`, validação 409 `allocation.exceeds_income` com `excess` | Criar verba atualiza `não alocado` imediatamente, excesso mostra valor em BRL, arquivar é idempotente e só owner vê ação |
| **4 — Registrar gasto** | Lançamento rápido | `ExpenseForm` (Dialog/bottom-sheet), `POST /envelopes/{id}/entries`, `GET /entries?month`, atualização otimista de `available` com rollback em erro | Gasto acima do saldo grava e mostra alerta negativo, participante consegue gastar, estranho recebe 404/403 sem vazar existência |
| **5 — Mês navegável e integração renda** | Histórico mensal consistente | `?month` na URL (`useSearchParams`), `todaySaoPaulo`, `GET /income?month` + `summary`, `GET /income/history` linkado | Trocar mês atualiza verbas + saldos + renda sem reload, mês sem renda mostra estado vazio orientando configurar renda |
| **6 — Acessibilidade, testes e LGPD** | Fechar qualidade | `axe-core` em `pnpm test`, testes de teclado/trap focus, `prefers-reduced-motion`, atualização de `docs/privacy/inventario-de-dados.md`, verificação de logs sem PII | `axe` sem violações, Tab/Esc/foco OK, 320–1440 OK, inventário atualizado, `make check` verde |

> Cada fase começa com teste falhando (backend: JUnit 5 + AssertJ + Testcontainers; web: vitest + Testing Library) e termina com `make check` verde (`AGENTS.md`).

---

## 8. Detalhe das fases — tarefas e decisões técnicas

### Fase 0 — Contrato e fundação (1–2 dias)

- [ ] Estender `docs/api/openapi.yaml` com `Envelope`, `EnvelopeSummary`, `LedgerEntry`, `LedgerSummary` usando `amount: string pattern ^\d{1,17}\.\d{2}$` + `currency: BRL` (igual a `Income`).
- [ ] Criar `web/src/lib/money.ts`:
  ```ts
  export const formatBRL = (plain: string) => new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(Number(plain));
  export const parseBRLInput = (ptBR: string) => ptBR.replace(/\./g,'').replace(',','.');
  export const isValidBRL = (plain: string) => /^\d+(\.\d{2})$/.test(plain);
  ```
  Testes: `10.999` inválido, `0.00` válido, `99999999999999999.99` limite, `Number` nunca usado para persistir.
- [ ] Criar `web/src/lib/dates.ts` com `todaySaoPaulo()` e `parseMonthParam(searchParams)` com fallback para `YearMonth` corrente.
- [ ] Criar `web/src/lib/api.ts` — funções tipadas `getEnvelopes(month)`, `createEnvelope(dto)`, `registerExpense(envelopeId,dto)`, `getSummary(month)` que delegam a `AuthClient.request` e lançam `ApiError{status, code, detail}` a partir de `ProblemDetail`.

**Fonte:** `docs/api/README.md:3-14`, `Money.java:84-96`, `IncomeService.java:26-28` (`America/Sao_Paulo`).

### Fase 1 — Backend leitura mínima (3–5 dias, TDD)

- [ ] Flyway `V2__create_envelope_tables.sql` e `V3__create_ledger_tables.sql` conforme `ADR-007 §1.2` (UUID, `NUMERIC(19,2)`, `TIMESTAMPTZ`, índices por `owner_id`/`envelope_id,occurred_at`).
- [ ] Entidades JPA `Envelope`, `EnvelopeParticipant` com `@Version` + `MoneyJpaConverter`.
- [ ] `EnvelopeService` com `canBeSeenBy`/`canSpend`/`archive` + `IncomeChangeConstraint` concreto (`SUM(base_amount) WHERE archived_at IS NULL`).
- [ ] `LedgerService` com `registerExpense` (valida `Money>0`, escala 2, `occurredAt <= today+1`, `description<=140`) e `balanceOf(envelopeId, YearMonth)` via `SUM(CASE WHEN kind ...)` + carry iterativo.
- [ ] Controllers com `ProblemDetail` e `code` estável (`envelope.not_found`, `amount.invalid_scale`, `allocation.exceeds_income`).
- [ ] Testes: `LedgerServiceTest` com Testcontainers cobre gasto negativo permitido, autorização participante vs estranho, concorrência de renda.

**Sem nova infra:** sem fila/cache — eventos via `ApplicationEvent` para `audit`.

### Fase 2 — Web lista real (2–3 dias, TDD)

- [ ] `hooks/useEnvelopes.ts` e `useLedgerSummary.ts` — `useEffect` + `AuthClient.request` + `AbortController`, sem `tanstack-query` no corte (migrar só se `p95>200ms` ou polling).
- [ ] Refatorar `Dashboard.tsx` para props reais:
  ```tsx
  <BalanceSummary income={summary.income} allocated={summary.allocated} unallocated={summary.unallocated} />
  <EnvelopeList envelopes={envelopes} onRegisterExpense={openExpenseForm} />
  ```
- [ ] `EnvelopeCard` — `progress` derivado de `available/baseAmount`, `isNegative` com `role="alert"` + ícone `⚠` (não só cor), `role` exibe "Compartilhada · Participante" quando aplicável.
- [ ] Estados: `loading` (`aria-busy`), `erro` (`role="alert"` + botão "Tentar novamente"), `vazio` ("Nenhuma verba neste mês — crie a primeira").

### Fase 3 — Criar/editar/arquivar verba (2–3 dias, TDD)

- [ ] `components/ui/Dialog.tsx` — trap focus (Tab/Shift+Tab), Esc fecha, foco retorna ao trigger, `aria-modal="true"`, `role="dialog"`.
- [ ] `EnvelopeForm` — campos `name` (1..80, trim), `purpose` (select), `baseAmount` (máscara pt-BR, envio ponto), validação local + erro 409 com `excess` formatado em BRL.
- [ ] Otimismo: `setEnvelopes(prev => [...prev, optimistic])` e rollback em `onError`; revalida `summary.unallocated`.

### Fase 4 — Registrar gasto (2–3 dias, TDD)

- [ ] `ExpenseForm` — `amount` (BRL), `occurredAt` (date, default hoje SP), `description` opcional 140c, `kind` fixo `EXPENSE` no corte.
- [ ] `POST /envelopes/{id}/entries` com atualização otimista de `available` (`prev.available - amount`) e exibição imediata de `isNegative`.
- [ ] `GET /envelopes/{id}/entries?month` em seção "Atividade recente" — substitui `activities` mock; `description` nunca logada.

### Fase 5 — Mês navegável e integração renda (1–2 dias)

- [ ] Ler `?month` de `useSearchParams` (`/verbas?month=2026-08` ou `/` com query), escrever ao trocar mês (ex.: `<select>` ou `<input type="month">`), `replaceState` sem reload.
- [ ] Buscar `GET /income?month` e `GET /ledger/summary?month` em paralelo; se 404 de renda, exibir CTA "Configure sua renda" (linka para fluxo existente).
- [ ] `occurredAt` e `effectiveFrom` sempre em `America/Sao_Paulo` — nunca `Intl.DateTimeFormat` sem `timeZone`.

### Fase 6 — Acessibilidade, testes e LGPD (1–2 dias)

- [ ] Adicionar `@axe-core/react` (dev) e teste `axe` em `EnvelopeCard.test.tsx`/`ExpenseForm.test.tsx`.
- [ ] Checklist WCAG 2.1 AA: Tab navega todo fluxo, Enter/Space ativa, Esc fecha Dialog, `prefers-reduced-motion` respeitado (já em `Dashboard.module.css:254-259`), contraste `4.5:1` preservado.
- [ ] Testes responsivos: 320/768/1024/1440 screenshots ou `vitest` com `window.resizeTo`.
- [ ] Atualizar `docs/privacy/inventario-de-dados.md` com `ledger_entry` e `envelope_participant`; garantir logs sem `amount`/`description`, exportação inclui entradas do titular.

---

## 9. Testes e verificação (definição de pronto)

**Backend**

```bash
cd backend && ./gradlew test          # inclui arquitetura Modulith
make check                             # testes + lint + builds verificáveis
```

- `EnvelopeBalanceTest` e `MonthlyAllocationPlanTest` continuam verdes.
- `LedgerServiceTest` cobre: gasto > saldo permitido com `isNegative`, `BaseAllocation` negativa rejeitada, autorização `owner`/`participant`/`estranho`, `IncomeChangeConstraint` bloqueia renda abaixo da soma.

**Web**

```bash
cd web && pnpm test    # vitest run + axe
cd web && pnpm lint
cd web && pnpm build
```

- `useEnvelopes.test.tsx`: loading → sucesso, 401 com refresh, 409 com `excess` formatado.
- `EnvelopeForm.test.tsx`: máscara `1.200,50` → envia `1200.50`, escala inválida mostra erro sem logar valor.
- `ExpenseForm.test.tsx`: Dialog trap focus, Esc fecha, `aria-busy` durante submit, rollback otimista em erro.
- `axe` sem violações críticas; teclado e `prefers-reduced-motion` OK.

**Manual**

- [ ] Login → lista verbas reais → criar verba → verba aparece com `não alocado` atualizado.
- [ ] Registrar gasto acima do saldo → saldo fica `-R$ 25,00` + alerta visível (ícone+texto).
- [ ] Trocar `?month=2026-07` → saldos recalculados com carry.
- [ ] Participante vê e gasta, mas não arquiva; estranho recebe 404 genérico.
- [ ] 320/768/1024/1440 sem quebra; bottom-sheet em mobile.

---

## 10. LGPD / Segurança — checklist

- [ ] `envelope`/`ledger_entry`/`envelope_participant` adicionados a `docs/privacy/inventario-de-dados.md` com retenção "até exclusão da conta".
- [ ] Logs com apenas `envelopeId`/`authorId` (hash), nunca `amount`/`description`/`email` bruto.
- [ ] `ProblemDetail` com `code` estável, sem `detail` com dado pessoal ou stack.
- [ ] Exportação/correção/exclusão alcançam `envelope` + `ledger_entry` (cascade por `owner_id`).
- [ ] Cookies `__Secure-refresh_token` com `Secure; HttpOnly; SameSite=Strict; Path=/api/v1/auth` (já em `openapi.yaml:37` e `AuthClient`).
- [ ] Sem `long/double` para dinheiro em nenhum ponto — `Money` + `NUMERIC(19,2)` + string JSON.

---

## 11. Riscos e decisões adiadas (com gatilho para ADR)

| Risco | Mitigação no corte | Gatilho para evoluir |
|---|---|---|
| Saldo derivado lento com muitos lançamentos | `SUM` por mês + carry iterativo; índice `ledger_envelope_occurred_idx` | `p95 >200ms` em `EXPLAIN ANALYZE` → materializar `envelope_balance` mensal |
| Falta de cache/deduplicação no web | `AuthClient` + `useEffect` simples | Polling ou múltiplos consumidores → ADR para `SWR`/`tanstack-query` |
| Validação compartilhada web/mobile | `isValidBRL` duplicado | Extrair `shared/money` para pacote ou `zod` com schema OpenAPI |
| Notificações de saldo negativo | Apenas alerta visual | Opt-in do titular + Firebase/APNs configurado (`fundacao-tecnica.md:86`) |
| `occurredAt` como `DATE` vs `TIMESTAMPTZ` | `DATE` alinhado a `YearMonth` da renda | Caso de uso com hora exata → ADR |

---

## 12. Critérios de aceite (o que o revisor deve conseguir verificar)

1. `Dashboard` não usa mais dados mock — `Renda do mês`, `Já reservado`, `Não alocado`, `Uso %` vêm de `GET /ledger/summary?month` + `GET /income?month`.
2. Criar verba com `R$ 400,00` em renda `R$ 5.000,00` reduz `Não alocado` para `R$ 4.600,00`; tentar exceder retorna 409 com excesso em BRL.
3. Registrar gasto `R$ 125,00` em verba com `R$ 100,00` grava, saldo vira `-R$ 25,00` e exibe alerta acessível (não só cor vermelha).
4. `?month=YYYY-MM` é compartilhável e reflete saldos com carry do mês anterior.
5. Participante via `envelope_participant` lista e gasta, mas não arquiva; não-participante recebe 404 sem vazar nome da verba.
6. `Money` nunca arredonda: enviar `10.999` retorna 400 `amount.invalid_scale`.
7. `make check` verde; `pnpm test` com `axe` sem violações; 320–1440 OK; `prefers-reduced-motion` respeitado.

---

## 13. Checklist de entrega (copiar para PR)

- [ ] `docs/api/openapi.yaml` atualizado com envelopes/ledger + exemplos `5000.00`.
- [ ] `backend/db/migration/V2__create_envelope_tables.sql` + `V3__create_ledger_tables.sql` + entidades + `IncomeChangeConstraint` concreto.
- [ ] `POST/GET /envelopes`, `PATCH /envelopes/{id}`, `POST /envelopes/{id}/archive`, `POST/GET /envelopes/{id}/entries`, `GET /ledger/summary?month` com `ProblemDetail`.
- [ ] `web/src/lib/{api,money,dates}.ts` + `hooks/{useEnvelopes,useLedgerSummary,useEnvelopeEntries}.ts` + `components/{EnvelopeCard,EnvelopeForm,ExpenseForm,ui/Dialog}`.
- [ ] `Dashboard` consome dados reais; estados loading/erro/vazio acessíveis.
- [ ] Testes backend (Testcontainers) + web (vitest + Testing Library + axe) verdes.
- [ ] `docs/privacy/inventario-de-dados.md` e `README.md` (comandos) atualizados.
- [ ] `make check` verde antes de merge (`AGENTS.md`).

---

## 14. Referências

- `docs/ideas/fundacao-tecnica.md:8-17,27-39` — verbas, renda, saldo acumulável, monólito modular
- `docs/decisions/0001-monorepo-e-monolito-modular.md` — limites Modulith
- `docs/decisions/0006-valores-monetarios-decimais-e-renda-mensal.md:21-34` — `Money`/`NUMERIC(19,2)`/string JSON
- `docs/decisions/0007-gastos-registro-saldo-e-ux.md:58-141` — modelo `envelope`/`ledger_entry`, regras de autorização, contrato proposto
- `backend/src/main/java/br/com/controlegastos/shared/money/Money.java:8-96` — normalização sem arredondamento
- `backend/src/main/java/br/com/controlegastos/envelopes/domain/MonthlyAllocationPlan.java:7-46` — cálculo de `unallocated`
- `backend/src/main/java/br/com/controlegastos/envelopes/domain/EnvelopeBalance.java:6-36` — `allocate`/`spend`/`isNegative`
- `backend/src/main/java/br/com/controlegastos/income/application/IncomeService.java:26-108` — `BUSINESS_ZONE`, `IncomeChangeConstraint`
- `docs/api/openapi.yaml:71-125` — contrato de renda (padrão para verbas)
- `docs/privacy/inventario-de-dados.md:13-16` — classificação de dados financeiros
- `web/src/auth/auth-client.ts:21-147` — `request` com refresh + `Navigator.locks` + `credentials: include`
- `web/src/app/globals.css:1-11` e `web/src/components/Dashboard/Dashboard.module.css:1-259` — tokens e layout mobile-first
- `web/src/components/Dashboard/Dashboard.tsx:3-51` — mock atual a substituir
- `web/package.json:12-16` — Next 16.2, React 19, vitest 4

