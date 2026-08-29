# Plano — Renda no front-end web com integração ao backend

> Entregar na web o fluxo completo de renda mensal (configurar, consultar efetiva por mês, histórico e integração com o cálculo de não alocado) reutilizando o backend já modelado como fonte única da verdade, sem quebrar `Money` exato, autorização por recurso, LGPD e monólito modular.

- **Data:** 2026-08-29
- **Status:** Proposto — pronto para execução
- **Alvo web validável:** Next.js 16.2 + React 19 (`web/package.json:12-16`), Android validável no mobile, iOS preservado sem teste sem macOS/Xcode (`docs/decisions/0004-kmp-android-primeiro.md`)
- **Fonte de verdade:** `docs/ideas/fundacao-tecnica.md:8-17,27-39`, `docs/decisions/0006-valores-monetarios-decimais-e-renda-mensal.md:21-38`, `AGENTS.md:1-40`

---

## 1. Objetivo

Entregar na web, atrás de autenticação, o corte vertical de renda `[fundacao-tecnica.md:58-65]`:

1. **Configurar renda do mês corrente** (`PUT /income {amount: "5000.00"}`) com máscara BRL `pt-BR` e envio `string` duas casas, sem arredondar.
2. **Consultar renda efetiva** de qualquer mês (`GET /income?month=YYYY-MM` e `GET /income` sem parâmetro = mês corrente em `America/Sao_Paulo`).
3. **Exibir derivado** `não alocado = renda - soma(baseAmount)` e `uso%` vindos de `GET /ledger/summary?month=` (`docs/api/openapi.yaml:269-285`), atualizando instantaneamente após `PUT /income`.
4. **Exibir histórico paginado** (`GET /income/history?page=&size=`, `web/src/lib/api.ts:148-160` já existe mas sem UI) com ator, mês de vigência e `changedAt`.
5. **Bloquear redução abaixo de `SUM(envelope.base_amount)`** via `IncomeBelowAllocationsException` → `409 INCOME_BELOW_BASE_ALLOCATIONS` e mensagem humana com `requiredMinimum/shortfall`.
6. **Navegar por mês** (`?month=YYYY-MM` compartilhável) e manter estado sem reload.

Fora de escopo neste corte: microserviços/filas/cache distribuído, multi-moeda, integração bancária, Keycloak, recuperação de e-mail (`fundacao-tecnica.md:68-76`).

---

## 2. Premissas confirmadas (não negociáveis)

- **Monólito modular preservado** — `income`, `envelopes`, `ledger`, `identity` como `@ApplicationModule` (`income/package-info.java:1`, `envelopes/package-info.java:1`, `ledger/package-info.java:1`). Não criar microserviços sem ADR (`AGENTS.md:8`).
- **Dinheiro sempre `BigDecimal` escala 2** + `NUMERIC(19,2)` + JSON string duas casas + `currency: BRL` (`0006:21-29`, `Money.java:8-96`, `Money.java:84-96` `setScale(2, UNNECESSARY)`). Proibido `long/double/float` para dinheiro (`AGENTS.md:10`).
- **Autorização por recurso**: `owner_id = currentUserId()` derivado de `AuthenticationService.currentUserId()` (`IncomeService.java:46`, `income/domain/IncomeRevision.java:22`) — nunca confiar em `ownerId` vindo do cliente.
- **TDD obrigatório** (`AGENTS.md:9`): teste falha → implementação mínima → refatoração.
- **Web mantém tokens atuais** `globals.css:1-11` (`--accent:#146c43`, `--surface:#ffffff`, `--border:#d9ddd6`) + CSS Modules, sem Tailwind/roxo/gradientes (`Dashboard.module.css:1-259`).

---

## 3. Estado atual — o que já está implementado

### 3.1 Backend `income` — completo e testado (fonte única, nada a migrar)

| Artefato | O que faz | Arquivo |
|---|---|---|
| `Money` imutável | `brl(String)`/`brl(BigDecimal)` normaliza `setScale(2, UNNECESSARY)`, rejeita `10.999`, `PRECISION=19 SCALE=2`, `toPlainString()` | `shared/money/Money.java:8-96` |
| `MoneyJpaConverter` | `AttributeConverter<Money,BigDecimal>` autoApply | `shared/money/MoneyJpaConverter.java:7-19` |
| `monthly_income` | PK `(owner_id, effective_month DATE primeiro dia)`, `amount NUMERIC(19,2) CHECK >=0`, `updated_at TIMESTAMPTZ`, `version` | `V2__create_income_tables.sql:1-13` |
| `income_revision` | append-only `id UUID, owner_id, actor_user_id, amount, effective_month DATE, changed_at TIMESTAMPTZ`, índice `(owner_id, changed_at DESC)` | `V2:15-27` |
| `MonthlyIncome` | `start(owner, month, amount, now)` + `changeTo(newAmount, now)` idempotente (`equals` → `false` sem `save`) | `income/domain/MonthlyIncome.java:43-55` |
| `IncomeService` | `BUSINESS_ZONE = America/Sao_Paulo` (`IncomeService.java:26`), `change(Money)` grava `YearMonth.now(BUSINESS_ZONE)`, `find(YearMonth)` usa `findEffectiveAtOrBefore` (última vigência ≤ mês pedido, `0006:31-33`), `history(page,size)` valida `page>=0 1<=size<=100`, `requireAllowedAmount` consulta `List<IncomeChangeConstraint>` e lança `IncomeBelowAllocationsException` | `income/application/IncomeService.java:24-108` |
| `IncomeChangeConstraint` | `@NamedInterface("constraints") @FunctionalInterface Money minimumIncomeFor(UUID, YearMonth)` — `envelopes` implementa `SUM(base_amount) WHERE archived_at IS NULL` sem expor `envelope` table (`envelopes/application/EnvelopeIncomeConstraint.java:11`) | `income/application/IncomeChangeConstraint.java:7-10` |
| `IncomeQuery` | `@NamedInterface("query") Optional<IncomeSnapshot> findEffective(UUID,YearMonth)` exposto para `ledger` calcular `unallocated` sem quebrar Modulith (`ledger` não importa `MonthlyIncomeRepository` direto) | `income/application/IncomeQuery.java:13-26` |
| `IncomeSnapshot` | `record(Money amount, YearMonth effectiveFrom, Instant changedAt)` `@NamedInterface("query")` | `income/application/IncomeSnapshot.java:7` |
| `IncomeController` | `PUT /income` `moneyFrom(JsonNode)` valida `{"amount":"5000.00"}` regex `^\d+(\.\d{1,2})?$` → `Money.brl` (escala validada por `Money`), `GET /income?month=` , `GET /income/history` | `income/web/IncomeController.java:31-73` |
| `IncomeExceptionHandler` | `INCOME_NOT_CONFIGURED 404`, `INCOME_BELOW_BASE_ALLOCATIONS 409 {requiredMinimum,shortfall,currency}`, `INCOME_CONCURRENT_CHANGE 409` via `OptimisticLocking` | `income/web/IncomeExceptionHandler.java:14-56` |
| Testes | `IncomeApiIntegrationTest.java:78-129` (Testcontainers `postgres:18-alpine`, registra `5000.00`, idempotência `5000.0`, `4100.25` em mês anterior, paginação) + `IncomeServiceTest`, `MoneyTest`, `ModularityTest` verdes `make test` | `backend/src/test/java/br/com/controlegastos/income/IncomeApiIntegrationTest.java:37-79` |

**Conclusão:** backend de renda não precisa de nova migração, entidade ou regra neste plano. O front vai **reusar** `PUT/GET /income` e `GET /ledger/summary` já existentes.

### 3.2 Frontend — parcial, sem tela dedicada de renda

| Camada | Estado | Arquivo |
|---|---|---|
| `lib/money.ts` | `formatBRL(plain)`, `parseBRLInput`, `isValidBRL ^\d{1,17}\.\d{2}$`, `formatBRLInputMask` (dígitos → `/100` → `pt-BR` duas casas), `maskToPlain` | `web/src/lib/money.ts:1-56` |
| `lib/dates.ts` | `todaySaoPaulo()`, `currentMonthSaoPaulo()`, `parseMonthParam`, `formatMonthLabel`, `BUSINESS_ZONE` | `web/src/lib/dates.ts:1-31` |
| `lib/api.ts` | `createApiClient(authClient)` com `getEnvelopes`, `createEnvelope`, `getLedgerSummary`, `getIncome(month?)`, `putIncome(amount)` + `ApiError{status,code,detail}` de `ProblemDetail` | `web/src/lib/api.ts:97-162` |
| `auth/auth-client.ts` | `AuthClient.request(path, init, retry)` com `Bearer` + `credentials:include` + `Navigator.locks` + refresh automático + `BroadcastChannel` | `auth/auth-client.ts:102-116` |
| `auth/auth-context.tsx` | `AuthProvider` expõe `client`, `state: loading/anonymous/authenticated/expired`, sync entre abas | `auth/auth-context.tsx:7-92` |
| `hooks/useEnvelopes.ts`, `useLedgerSummary.ts` | `useMemo(api)` + `useCallback(refresh)` + `useEffect(() => void refresh())` | `hooks/useEnvelopes.ts:12-38` |
| `components/Dashboard/Dashboard.tsx` | Já consome `useLedgerSummary(month)` + `useEnvelopes(month)` + `?month` via `useSearchParams/useRouter` + `BalanceSummary` + `EnvelopeCard` + `Dialog` | `Dashboard.tsx:18-138` |
| `components/BalanceSummary` | `income ? formatBRL(income.amount) : "Renda não configurada"` + `allocated/unallocated/usagePct` | `BalanceSummary/BalanceSummary.tsx:10-35` |
| Lacuna | **Não existe** tela/form dedicado de renda: `Dashboard` mostra `summary.income` mas se `404 INCOME_NOT_CONFIGURED` exibe só mensagem `"Configure sua renda em /api/v1/income..."` (`Dashboard.tsx:122`) sem `PUT`. Não há `useIncome`/`IncomeForm`/`IncomeHistory` com paginação, máscara, idempotência, erro `409` com `shortfall`, navegação `?month` lado-a-lado renda↔verbas. | — |

### 3.3 O que este plano fecha (gap)

| Gap | Impacto | Sem o plano |
|---|---|---|
| UI de renda inexistente | Usuário não consegue alterar renda sem `curl` | `PUT /income` só via API direta; web fica read-only |
| Sem `useIncome` tipado | Cada componente reimplementa `AuthClient.request` | Duplicação e falta de `loading/error/refresh` padronizado |
| Histórico sem paginação | Não atende `fundacao-tecnica.md:16` (manter histórico) | `GET /income/history` existe mas sem consumidor |
| Erro `409` não humano | Redução bloqueada sem `shortfall` em BRL | Usuário não entende por que não conseguiu reduzir |
| Mês não sincronizado renda↔verbas | `GET /income?month=` e `GET /ledger/summary?month=` podem divergir | `?month` do Dashboard não reflete renda efetiva do mês visitado |

---

## 4. Arquitetura alvo — o que vai ser (e permanece o que já é)

### 4.1 Contrato REST já existente (reusado, não estendido)

```
PUT    /api/v1/income              { amount:"5000.00" }               → 200 { amount,currency,effectiveFrom,changedAt } | 400 INVALID_REQUEST | 409 INCOME_BELOW_BASE_ALLOCATIONS {requiredMinimum,shortfall}
GET    /api/v1/income?month=2026-08 → 200 { ... } | 404 INCOME_NOT_CONFIGURED | 401
GET    /api/v1/income/history?page=0&size=20 → 200 { items:[{id,amount,currency,effectiveFrom,changedAt,changedBy}], page,size,hasNext }
GET    /api/v1/ledger/summary?month=2026-08 → 200 { income:{amount,currency,effectiveFrom,changedAt}|null, allocated, unallocated, usagePct, envelopes[] }
```

Regras transversais já vigentes (`docs/api/README.md:3-14`, `openapi.yaml:71-125`):

- `amount` string duas casas obrigatórias, `currency:BRL` (`IncomeController.java:69` regex `^\d+(\.\d{1,2})?$` + `Money` rejeita `10.999`).
- `ProblemDetail application/problem+json` com `code` estável, sem stack, sem `amount` em logs (`ApiExceptionHandler.java:63-73`).
- `owner_id` e `actor` derivados de `AuthenticationService.currentUserId()` (`IncomeService.java:46`), nunca do body.
- `effectiveFrom = YearMonth.now(IncomeService.BUSINESS_ZONE)` (`IncomeService.java:48`), `America/Sao_Paulo` — mesma zona usada por `envelopes` e `ledger` (`LedgerService.java:27`, `EnvelopeService.java:23`).

**Nada de nova tabela/coluna/migração** — `V2` já cobre `monthly_income` + `income_revision`. O plano não cria `V6`.

### 4.2 Fluxo de integração web ↔ API (sequência)

```
[Input BRL "5.000,00" ] -- maskToPlain "5000.00" --> [IncomeForm] -- api.putIncome("5000.00") --> AuthClient.request("PUT /api/v1/income", {credentials:"include", Authorization: Bearer}) --> [Caddy] --> [SecurityFilterChain JWT + SessionValidation]
  → IncomeService.change(Money.brl("5000.00")) @Transactional
      1. requireAllowedAmount(owner, YearMonth.now(SP), Money) → constraints = SUM(envelopes.base_amount) → se < shortfall lança 409
      2. MonthlyIncome.start|changeTo (idempotente, version optimistic)
      3. IncomeRevision.record(owner, actor, amount, month, now) append-only
  ← 200 { amount:"5000.00", currency:"BRL", effectiveFrom:"2026-08", changedAt }
  ← ApiError 409 { code:"INCOME_BELOW_BASE_ALLOCATIONS", requiredMinimum:"4250.00", shortfall:"250.00" } → IncomeForm mapeia para "Reduza verbas em R$ 250,00 ou aumente renda para R$ 4.250,00"
  → refresh: Promise.all([useIncome.refresh(), useLedgerSummary.refresh(month)])
  → BalanceSummary re-render com novo `unallocated = income - allocated`
```

Idempotência: `PUT "5000.0"` normalizado → `Money.brl("5000.0")` → `5000.00` → `income.changeTo` retorna `false` sem `save`/`revision` (`IncomeService.java:57-60`). Front não precisa otimizar, mas pode evitar `refresh` se `409/400`.

### 4.3 Estrutura de pastas alvo (colocation, sem camada técnica global)

```
web/src/
  lib/
    api.ts                # já existe: getIncome/putIncome + handleResponse<IncomeDTO> (manter)
    money.ts              # já existe: formatBRL, maskToPlain, isValidBRL (reusar)
    dates.ts              # já existe: todaySaoPaulo, parseMonthParam, BUSINESS_ZONE (reusar)
  hooks/                  # NOVO/ajuste
    useIncome.ts          # GET /income?month + PUT /income + história paginada; estado {income, loading, error, history, page, hasNext}
    useLedgerSummary.ts   # já existe — será consumido junto a useIncome para recalcular não alocado
  components/
    Dashboard/            # já existe — passa a compor IncomeCard no header (não mock)
    IncomeCard/
      IncomeCard.tsx      # apresentação pura: renda efetiva, mês vigência, changedAt, unallocated/uso%
      IncomeCard.test.tsx
    IncomeForm/
      IncomeForm.tsx      # Dialog acessível: input BRL com máscara, submit PUT, mapeamento 409/400
      IncomeForm.test.tsx
      useIncomeForm.ts    # (opcional colocation) estado local + validação
    IncomeHistory/
      IncomeHistory.tsx   # lista paginada items: amount, effectiveFrom, changedAt, changedBy (sem expor UUID se LGPD)
      IncomeHistory.test.tsx
    ui/
      Dialog.tsx          # já existe: trap focus, Esc, aria-modal, bottom-sheet ≤48rem
      Field.tsx           # já existe
```

> Por que colocation: cada `*Form` com seu `*.test.tsx`/`*.module.css`/`use*.ts` evita pasta `components/` gigante por camada técnica (exigência `frontend-ui-engineering`).

---

## 5. Decisões detalhadas — O QUE, COMO, PORQUE

### D1 — Reusar backend `income` sem nova migração

- **O QUE:** Não criar `V6`, entidade ou coluna nova para renda.
- **COMO:** Front consome `PUT /income`, `GET /income?month=` e `GET /income/history` já existentes (`IncomeController.java:31-49`). Se precisar de novo campo, exigir ADR antes (`AGENTS.md:12`).
- **PORQUE:** `V2` já persiste `monthly_income` por `(owner_id, effective_month)` com `findEffectiveAtOrBefore` que reconstrói renda de meses passados (`IncomeService.java:76`), `income_revision` append-only e idempotência já testada (`IncomeApiIntegrationTest.java:88`). Nova migração sem necessidade viola `NÃO faremos agora: mais de uma moeda` e aumenta risco de `EXPLAIN ANALYZE` sem métrica.

### D2 — `Money` como string duas casas, nunca `number`/`long`

- **O QUE:** `api.putIncome` recebe `amount: string` `^\d{1,17}\.\d{2}$` (`openapi.yaml:192`), `MoneyDTO` igual.
- **COMO:** `web/src/lib/money.ts:22 isValidBRL` testa regex; `IncomeController.moneyFrom:69` valida `^\d+(\.\d{1,2})?$` → `Money.brl(decimal)` → `Money.java:84 setScale(2, UNNECESSARY)` lança em `10.999`; `api.ts` envia `JSON.stringify({amount: plain})` sem `Number()`.
- **PORQUE:** `double` binário não representa `0.10` exato, `long` centavos confunde id com dinheiro (`0006:41-46`), `Money.java:88` proíbe arredondamento silencioso e `PostgreSQL NUMERIC(19,2)` preserva exatidão (`0006:53-57`). Cliente `Kotlin`/`TypeScript` quebraria com `number` JSON.

### D3 — Fuso fixo `America/Sao_Paulo` para vigência e `occurredAt`

- **O QUE:** `effectiveFrom = YearMonth.now(IncomeService.BUSINESS_ZONE)` (`IncomeService.java:48`), `todaySaoPaulo()` para default de `month` na web (`dates.ts:1`).
- **COMO:** `lib/dates.ts` usa `toLocaleDateString("en-CA",{timeZone:"America/Sao_Paulo"})`; `IncomeForm` não envia `effectiveFrom` (backend deriva), apenas `amount`; `useIncome(month)` passa `?month=YYYY-MM` idêntico a `useLedgerSummary(month)` para manter `income` e `ledger/summary` coerentes.
- **PORQUE:** `0006:31` exige vigência no mês corrente em `America/Sao_Paulo` e que `GET /income?month=` use “última vigência ≤ mês pedido”. Usar `Intl` sem `timeZone` quebraria virada de mês para usuário em UTC e desalinharia `envelopes` (`LedgerService.BUSINESS_ZONE`) e `ledger`.

### D4 — Append-only + idempotência da renda

- **O QUE:** Repetir `PUT {"amount":"5000.00"}` não cria nova `income_revision`.
- **COMO:** `IncomeService.change:57` `isNew || income.changeTo(...)` — `changeTo` compara `Money.equals` (`MonthlyIncome.java:48-51`) e retorna `false` sem `save`; `IncomeApiIntegrationTest:88` cobre `putIncome("5000.0")` idempotente.
- **PORQUE:** `0006:35` e `0006:59-62` rejeitam sobrescrever única renda atual (perderia histórico) e garantem retry seguro do front (ex.: double-click) sem inflar `income_revision`.

### D5 — Validar redução contra `SUM(envelopes.base_amount)` via `IncomeChangeConstraint`

- **O QUE:** `PUT /income` com `amount < SUM(base_amount)` retorna `409 INCOME_BELOW_BASE_ALLOCATIONS` com `requiredMinimum/shortfall`.
- **COMO:** `IncomeService.requireAllowedAmount:100-107` coleta `constraints: List<IncomeChangeConstraint>` (bean `EnvelopeIncomeConstraint` em `envelopes` implementa `SUM WHERE archived_at IS NULL` via `EnvelopeRepository.sumBaseAmountsRaw` native) e compara `requested < max(constraint)`; `IncomeExceptionHandler:29-40` mapeia para `ProblemDetail 409` com `code`, sem `amount` em logs.
- **PORQUE:** `0006:37-38` exige “Uma redução será recusada quando restrições de verbas-base exigirem renda superior” e `fundacao-tecnica.md:10` que soma-base nunca exceda renda. Ciclo `income -> envelopes` é permitido via `@NamedInterface("constraints")` em `IncomeChangeConstraint.java:8` e `@NamedInterface` em `EnvelopeIncomeConstraint`/`EnvelopeService`, sem `income` conhecer `envelope` table diretamente (`ModularityTest` verde). `IncomeChangeConstraint` em `shared` seria alternativa, mas `income` já expõe `constraints` como API mínima.

### D6 — Autorização e LGPD por recurso

- **O QUE:** Toda query filtra por `owner_id = currentUserId()`; `GET /income/history` só retorna linhas do `owner` autenticado.
- **COMO:** `IncomeService.find/findCurrent/history` usa `authentication.currentUserId()` (`IncomeService.java:46,76,86`); `AuthClient.request` adiciona `Authorization: Bearer` e `credentials:"include"` (`auth-client.ts:102`). Front nunca envia `ownerId`; `ApiError` exibe `code` sem `detail` com PII.
- **PORQUE:** `inventario-de-dados.md:13` classifica `renda mensal e histórico` como dado financeiro pessoal com retenção até exclusão, isolado por `owner_id` (`privacy/requisitos-de-seguranca.md`). Logs não registram `amount` (`IncomeExceptionHandler` não loga `requestedMimimum` além de `ProblemDetail`).

### D7 — Front sem `tanstack-query`/`swr`/`zod` no corte

- **O QUE:** Server state via `AuthClient.request` + `useState/useCallback/useEffect` + `useMemo(api)` (`hooks/useEnvelopes.ts:12-38` já segue).
- **COMO:** `hooks/useIncome.ts` segue mesmo molde de `useLedgerSummary.ts:12-38`: `const api = useMemo(() => createApiClient(client),[client])`, `refresh = useCallback(async () => { setLoading(true); api.getIncome(month) ... }, [api, month])`, `useEffect(() => void refresh() // eslint-disable-line)`.
- **PORQUE:** `verbas-front-web.md:209` já decidiu adiar `tanstack-query` até `p95>200ms` ou polling; `auth-client.ts:62-84` já tem deduplicação via `Navigator.locks` e `refreshPromise`. `zod` +30kB bundle sem paginação complexa; validação `isValidBRL` + regex já é contrato OpenAPI.

### D8 — Máscara BRL `type="text" inputMode="decimal"`

- **O QUE:** Input exibe `1.200,50` (`pt-BR`) mas envia `1200.50`.
- **COMO:** `lib/money.ts:45-55 formatBRLInputMask` (dígitos → `/100` → `Intl.NumberFormat pt-BR`), `maskToPlain` (`1.200,50` → `1200.50`) usado por `IncomeForm.tsx:15-22` antes de `isValidBRL` e `api.putIncome`.
- **PORQUE:** `type="number"` varia por locale (` ,` vs `.`) e `step` quebra em `pt-BR` (`0007:278`), `type="text" inputMode="decimal"` é correto para `pt-BR` e mantém duas casas sem `Number` no `toPlainString`.

### D9 — `Dialog` acessível já existente, reaproveitar

- **O QUE:** `IncomeForm` dentro de `Dialog` (trap focus, `Esc`, `aria-modal`, bottom-sheet `≤48rem`).
- **COMO:** `components/ui/Dialog.tsx:1-45` (showModal, `cancel` → `onClose`, `keydown Tab` trap, `onClick` backdrop → `onClose`). `IncomeForm` usa `<label htmlFor>` + `aria-describedby="amount-help"` + `role="alert"` para `409`.
- **PORQUE:** WCAG 2.1 AA obrigatório (`frontend-ui-engineering` skill), `Dashboard.module.css:254-259` já trata `prefers-reduced-motion`, contraste `4.5:1` com `--accent #146c43` sobre branco.

### D10 — URL state `?month=YYYY-MM` compartilhável

- **O QUE:** `IncomeCard` e `BalanceSummary` leem mesmo `month` de `useSearchParams`.
- **COMO:** `Dashboard.tsx:22-24` já faz `parseMonthParam(searchParams.get("month")) ?? currentMonthSaoPaulo()` + `<input type="month" value={month} onChange={router.push("/?month="+v)}>`; `useIncome(month)` e `useLedgerSummary(month)` refetch em paralelo; se `GET /income?month=` `404`, exibe CTA “Configure renda”.
- **PORQUE:** `fundacao-tecnica.md:20` exige web para histórico completo; mês na URL é bookmarkable e alinha renda efetiva com verbas do mesmo mês via mesma zona.

---

## 6. Plano de execução em fases (TDD, cada fase mergeável, `make check` verde)

| Fase | O QUE vai fazer | COMO vai fazer (passos) | PORQUE desta ordem | Critério de pronto |
|---|---|---|---|---|
| **0 — Contrato e tipos (0,5 dia)** | Congelar tipos de renda na web e garantir `Money` sem regressão | 1. Sem nova `openapi.yaml` (já tem `Income:192-195`, `IncomeChange:338-346`). 2. Criar `web/src/lib/money.test.ts` (já existe `MoneyTest.java:8-96` espelho): `isValidBRL("10.999")==false`, `formatBRLInputMask("120050")=="1.200,50"`, `maskToPlain` round-trip. 3. Criar `web/src/lib/dates.test.ts`: `currentMonthSaoPaulo()` mockado via `vi.useFakeTimers` + `parseMonthParam`. | Garante que máscara nunca usará `Number` e que `America/Sao_Paulo` não será esquecido antes de UI existir (falha cedo). | `pnpm test` com `money.test.ts` verde, sem tocar backend |
| **1 — Hook `useIncome` + `IncomeCard` leitura (1 dia)** | Tornar `GET /income?month=` real na web | 1. Escrever `hooks/useIncome.test.tsx` falhando: `loading→income`, `404 INCOME_NOT_CONFIGURED→error`, `401→refresh`. 2. Implementar `hooks/useIncome.ts` (molde `useEnvelopes.ts:12-38`): `api.getIncome(month)` + `api.getLedgerSummary` paralelo não, só renda. 3. Criar `components/IncomeCard/IncomeCard.tsx` puro: props `income:IncomeDTO|null`, `allocated/unallocated`, `monthLabel`, `aria-busy`/`role="status"`. | Reuso imediato de `/ledger/summary` já consumido por `Dashboard`; `IncomeCard` puro é testável sem `AuthClient`. | `Dashboard` mostra renda efetiva do `?month` (mock `msw` ou `vi.mock api.ts`), `GET /income?month=2026-08` reflete `4100.25` de mês anterior (reproduz `IncomeApiIntegrationTest:101-105`) |
| **2 — Form `IncomeForm` com `PUT /income` e erros humanos (1,5 dias)** | Fechar ciclo de escrita com validação BRL e `409` | 1. TDD `IncomeForm.test.tsx`: digitar `1.200,50` → envia `1200.50`, `10.999` → erro local `"duas casas"`, `5000.00` idempotente não cria segunda linha, `409` com `shortfall 500.00` → mensagem `"Renda de R$ 5.000,00 não cobre R$ 5.500,00 (excesso R$ 500,00)"`. 2. Implementar `IncomeForm.tsx` com `maskToPlain` → `isValidBRL` → `api.putIncome(plain)` → `ApiError.code==="INCOME_BELOW_BASE_ALLOCATIONS"` mapeia `requiredMinimum/shortfall` formatados via `formatBRL`. 3. Integrar `Dialog` já existente; `onSuccess` → `Promise.all([useIncome.refresh(), useLedgerSummary.refresh])` para `unallocated` instantâneo. | Front nunca arredonda (`Money` rejeita `10.999`); `409` com `shortfall` é requisito `0006:37`; `Dialog` já testado (`Dialog.tsx:1`). | Criar renda `5000.00` → `BalanceSummary` mostra `Não alocado = 750.00` (se `allocated 4250`), reduzir para `4000.00` com verbas `4250` → modal fica aberto com alerta vermelho + ícone, sem perder `amount` digitado |
| **3 — Histórico paginado (1 dia)** | Expor `GET /income/history` | 1. TDD `IncomeHistory.test.tsx`: `page 0 size20` renderiza 2 itens decrescentes, `hasNext true` mostra “Carregar mais”, `404` não é erro mas histórico vazio. 2. Estender `useIncome` para `history(page,size)` + `hasNext`; `IncomeHistory.tsx` lista `amount/currency/effectiveFrom/changedAt` com `role="list"` e `aria-label`. 3. Paginação `size<=100` já validada em `IncomeService.history:82` e `openapi.yaml:117`. | `0006:35` exige append-only consultável; `fundacao-tecnica.md:16` manter histórico; reuse `IncomeController.java:44-50` sem mudar backend. | `PUT 6200.10` → histórico topo `6200.10`, `PUT 5000.00` no mês seguinte não reescreve mês anterior (`IncomeApiIntegrationTest:96-110`) |
| **4 — Mês navegável renda↔verbas sincronizado (0,5 dia)** | Garantir `?month` coerente | 1. Já existe `Dashboard` com `useSearchParams` (`Dashboard.tsx:19-24`); estender `IncomeCard` para receber `month` e exibir `effectiveFrom` quando `income.effectiveFrom !== month` (“Renda de 2026-07 vigente”). 2. `useIncome(month)` e `useLedgerSummary(month)` refetch parallel; se `GET /income?month=2026-07` `404` → `IncomeCard` CTA “Configure renda de julho/2026”. | `IncomeService.find:76` usa `findEffectiveAtOrBefore` — mês sem renda própria deve mostrar última efetiva, não `404` a menos que nunca houve renda. | Trocar `input type=month` 2026-07 → sem reload, `Renda do mês` atualiza para `4100.25`, `Não alocado` recalcula, URL copiável |
| **5 — A11y, testes e LGPD (0,5 dia)** | Fechar qualidade antes de merge | 1. `axe-core` em `IncomeCard.test.tsx/IncomeForm.test.tsx` (`vitest` + `@axe-core/react`), Tab/Esc/trap, `prefers-reduced-motion` (`Dashboard.module.css:254`). 2. Atualizar `inventario-de-dados.md:13` já cobre renda/histórico, mas validar que `GET /income/history` não expõe `changedBy` UUID além do próprio `owner` (já filtra por `owner_id` em `IncomeRevisionRepository:19`). 3. `make check` (`backend ./gradlew test` inclui `ModularityTest`, `web pnpm lint && pnpm test && pnpm build`). | `AGENTS.md:14` checklist antes de concluir; `privacy/requisitos-de-seguranca.md` exige `code` estável sem PII em `ProblemDetail`. | `axe` 0 violações críticas, `320/768/1024/1440` OK (breakpoints `Dashboard.module.css:227-252`), `ModularityTest` verde, logs sem `amount` |

> Cada fase começa com teste falhando (web `vitest` + `@testing-library/react`, backend já verde com Testcontainers) e termina com `make check` verde (`AGENTS.md:14`). Backend não muda, então `cd backend && ./gradlew test` já inclui `ModularityTest` e `IncomeApiIntegrationTest`.

---

## 7. Decisões adiadas e gatilhos (não fazer agora, mas quando)

| Adiado | Mitigação no corte | Gatilho para ADR |
|---|---|---|
| `tanstack-query`/`swr` | `useIncome` com `useEffect` + `AuthClient` (`withBrowserLock` já deduplica refresh) | `p95>200ms` em `GET /income` ou polling histórico → ADR `query-cache` |
| `zod` para `IncomeChange` | Regex `isValidBRL` + `Money.java` valida escala | Validação compartilhada web/mobile ou `openapi.yaml` com `oneOf` → ADR |
| `envelope` criação validar `PUT /income` síncrono | Validação já faz `IncomeService.requireAllowedAmount` via `EnvelopeIncomeConstraint`; se `PUT /income` `409`, front já mostra `shortfall` | Caso criar verba deva falhar `409` com `excess` já cobre, sem novo endpoint |
| Notificação de renda alterada | Só `changedAt` em `IncomeCard` | Opt-in `notifications` + Firebase/APNs (`fundacao-tecnica.md:86`) |
| Exportação LGPD de `income_revision` | Já em `inventario-de-dados.md:13` “até exclusão”, mas sem UI de exportação neste corte | Pedido de titular → `privacy` module `export` |

---

## 8. LGPD / Segurança — checklist (deve passar antes de abrir cadastro ao público, `privacy/requisitos-de-seguranca.md`)

- [ ] `income` e `income_revision` já em `inventario-de-dados.md:13` com “dado financeiro pessoal; somente valor total, mês, ator, instantes” — manter, não logar `amount` (`ApiExceptionHandler` já retorna `INVALID_REQUEST` genérico sem `amount` no `detail`).
- [ ] `GET /income/history` filtra `owner_id = currentUserId()` (`IncomeService.history:86` + `IncomeRevisionRepository.findByOwnerIdOrderByChangedAtDescIdDesc`) — não vazar `changedBy` de outro usuário; `changedBy` é sempre `owner_id` no corte (`IncomeService.change:63` `owner, owner`).
- [ ] `ProblemDetail.code` estável (`INCOME_NOT_CONFIGURED`, `INCOME_BELOW_BASE_ALLOCATIONS`, `INCOME_CONCURRENT_CHANGE`) sem `stackTrace` (`IncomeExceptionHandler:19-48`).
- [ ] `version` (`@Version` em `MonthlyIncome.java:30-32`) garante `409 INCOME_CONCURRENT_CHANGE` em race, sem `DataIntegrityViolation` vazado.
- [ ] Cookie `__Secure-refresh_token` `Secure; HttpOnly; SameSite=Strict; Path=/api/v1/auth` (`openapi.yaml:37`), `AuthClient` nunca registra `amount`.

---

## 9. Critérios de aceite (o que o revisor deve conseguir verificar manualmente)

1. Login → sem renda → `IncomeCard` “Renda não configurada” + `Dashboard` “Configure sua renda” → `PUT 5000.00` (digitando `5.000,00`) → `R$ 5.000,00` aparece com `Vigência 2026-08`, `Não alocado` = `5000 - SUM(base)` e `Uso%` correto.
2. Repetir `PUT 5000.00` (ou `5000.0`) é idempotente: `GET /income/history` não cria nova linha, `changedAt` não muda (reproduz `IncomeApiIntegrationTest:88`).
3. Com verbas `SUM=4250`, tentar `PUT 4000.00` → modal mantém valor digitado, exibe `role="alert"` “Renda menor que verbas-base. Mínimo R$ 4.250,00 (faltam R$ 250,00)” vindo de `409 {requiredMinimum, shortfall}`.
4. `PUT 6200.10` → `GET /income?month=2026-07` continua `4100.25` (mês anterior não reescrito), `GET /income?month=2026-08` → `6200.10`.
5. `GET /income/history?page=0&size=1` → `hasNext true`, `items[0].amount 6200.10`; `size=101` → `400 INVALID_REQUEST`.
6. Trocar `?month=2026-07` via `input type=month` → sem reload, `IncomeCard` mostra “Renda de 2026-07 vigente” + `ledger/summary` do mesmo mês, URL copiável.
7. `Money` nunca arredonda: enviar `10.999` ou `1E+3` via `PUT` → `400 INVALID_REQUEST` (`IncomeController.moneyFrom:69`, `Money.java:88`).
8. `make check` verde; `axe` 0 violações; `320/768/1024/1440` sem quebra; `prefers-reduced-motion` respeitado; isolamento: `outra@example.com` vê `404 INCOME_NOT_CONFIGURED` (`IncomeApiIntegrationTest:162`).

---

## 10. Checklist de entrega (copiar para PR)

- [ ] `web/src/hooks/useIncome.ts` + `components/IncomeCard`/`IncomeForm`/`IncomeHistory` + `lib/money.ts`/`dates.ts` reusados, com `isValidBRL` e `formatBRLInputMask`.
- [ ] `Dashboard` compõe `IncomeCard` + `BalanceSummary` com `month` compartilhado (`useSearchParams`).
- [ ] `api.ts` já expõe `getIncome`/`putIncome` — testes `IncomeForm.test.tsx` cobrem máscara `1.200,50→1200.50`, `409→shortfall`, `400→INVALID_REQUEST` sem logar PII.
- [ ] `IncomeApiIntegrationTest` verde (Testcontainers `postgres:18-alpine`) + `MoneyTest` + `IncomeServiceTest` verdes.
- [ ] `docs/api/openapi.yaml` não precisa de nova versão (contrato já tem `Income/IncomeChange`); se mudar, versionar `openapi.yaml` e `README.md:12-14`.
- [ ] `docs/privacy/inventario-de-dados.md:13` já cobre renda; validar que nenhum novo campo pessoal foi adicionado.
- [ ] `make check` verde antes de merge (`AGENTS.md:14`), `ModularityTest` verificação de `income` expõe `IncomeQuery`/`IncomeSnapshot` via `@NamedInterface("query")` sem expor `MonthlyIncomeRepository`.

---

## 11. Fontes

- `docs/ideas/fundacao-tecnica.md:8-17,56-65` — corte vertical renda+verbas+saldos
- `docs/decisions/0006-valores-monetarios-decimais-e-renda-mensal.md:21-38` — `Money`, `NUMERIC(19,2)`, vigência, histórico, idempotência, `IncomeChangeConstraint`
- `backend/src/main/java/br/com/controlegastos/shared/money/Money.java:8-96` — `setScale(2, UNNECESSARY)`, `PRECISION=19`
- `backend/src/main/java/br/com/controlegastos/income/domain/MonthlyIncome.java:16-55` — `EmbeddedId`, `@Version`, `changeTo`
- `backend/src/main/java/br/com/controlegastos/income/application/IncomeService.java:24-108` — `BUSINESS_ZONE`, `change` idempotente, `findEffectiveAtOrBefore`, `requireAllowedAmount`
- `backend/src/main/java/br/com/controlegastos/income/web/IncomeController.java:31-73` — `moneyFrom` regex, `PUT/GET/history`
- `backend/src/main/resources/db/migration/V2__create_income_tables.sql:1-27` — `monthly_income` + `income_revision`
- `docs/api/openapi.yaml:71-125,192-214,236-240` — `Income`, `IncomeChange`, `IncomeHistoryPage`, `Problem`
- `web/src/lib/api.ts:97-162` — `createApiClient`, `handleResponse<T>`, `ApiError`
- `web/src/lib/money.ts:1-56` — `formatBRL`, `maskToPlain`, `isValidBRL`
- `web/src/lib/dates.ts:1-31` — `America/Sao_Paulo`, `currentMonthSaoPaulo`
- `web/src/auth/auth-client.ts:21-147` — `Bearer` + `refresh` + `Navigator.locks` + `BroadcastChannel`
- `web/src/components/Dashboard/Dashboard.tsx:18-138` — `useSearchParams` mês, `BalanceSummary`, `Dialog` bottom-sheet
- `web/src/components/Dashboard/Dashboard.module.css:1-259` — tokens, breakpoints, `prefers-reduced-motion`
- `docs/privacy/inventario-de-dados.md:13` — retenção renda até exclusão

