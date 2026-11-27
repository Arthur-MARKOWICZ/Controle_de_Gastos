# Design

## Context

See `proposal.md` — Why. Hoje o módulo `reporting` só exporta CSV/XLSX (`ADR-0014`); o resumo do mês (`GET /api/v1/ledger/summary`) devolve `available` / `isNegative` sem ritmo. Verbas `LIMIT` usam saldo mensal sem carry (`ADR-0022`): capacidade do mês = `baseAmount` + aportes do mês; gasto do mês não bloqueia além do limite (`ADR-0007`). Não há tabela nem cálculo de projeção de ritmo.

## Goals / Non-Goals

**Goals:**

- Isolar a regra de ritmo no módulo `reporting` como leitura pura reutilizando portas de ledger já usadas pelos exports.
- Publicar um contrato JSON estável consumível por web, Android e desktop na visão geral e nos cartões LIMIT.
- Definir aritmética de dias e escala monetária de forma testável (28/29/30/31, dia 1, gasto zero).

**Non-Goals:**

- Enriquecer `ledger/summary` com campos de ritmo (evita acoplar overview do ledger a reporting).
- Alertas preventivos (V14 / `event-alerts`).
- MCP, iOS validação, cache, filas, novas tabelas.
- Mudar exports existentes ou a fórmula de `available`.

## Decisions

### 1. Endpoint dedicado no reporting

- **Escolha:** `GET /api/v1/reports/limit-spending-pace?month=YYYY-MM` → `200 application/json`.
- **Por quê:** a issue pede leitura no `reporting`; mantém `ledger/summary` estável; espelha o padrão de um endpoint por relatório do ADR-0014.
- **Alternativa rejeitada:** embutir no summary — menos round-trip, mas mistura capacidade de saldo com projeção e força todo consumidor do summary a ignorar campos novos.
- **ADR:** contrato público novo no módulo existente → na aplicação, adendo curto ao **ADR-0014** registrando o quarto endpoint JSON (sem persistência), alinhado à alternativa rejeitada “projeções ou arquivos persistidos” (materialização ≠ cálculo sob demanda).

### 2. Fórmulas e data de referência

Fuso: `America/Sao_Paulo`.

- `monthLength` = dias do mês civil.
- `referenceDate` = `clamp(hoje, primeiroDia(mês), últimoDia(mês))` (mês passado → último dia; mês futuro → dia 1).
- `elapsedDays` = dia do mês de `referenceDate` (sempre ≥ 1).
- `remainingDays` = `monthLength - elapsedDays + 1` (inclui o dia de referência).
- `capacity` = `baseAmount + CONTRIBUTION` ativos do mês (mesma base do saldo mensal).
- `spent` = soma de gastos ativos do mês na verba.
- `remainingCapacity` = `capacity - spent`.
- `projectedMonthSpend` = se `spent` é zero → `"0.00"`; senão `(spent / elapsedDays) * monthLength`.
- `dailyPace` = `spent / elapsedDays` (só se `spent > 0`).
- `projectedOverrunDay` = se `dailyPace > 0` e `projectedMonthSpend > capacity`, o dia civil `ceil(capacity / dailyPace)` limitado a `[1, monthLength]`, formatado `YYYY-MM-DD`; senão `null`. Se `remainingCapacity ≤ 0`, `projectedOverrunDay` = `null` e allowance `"0.00"`.
- `dailyRemainingAllowance` = se `remainingCapacity ≤ 0` → `"0.00"`; senão `remainingCapacity / remainingDays`.

### 3. Escala monetária nas fronteiras de projeção

- **Escolha:** intermediários em `BigDecimal`; campos monetários de saída com escala 2 via `RoundingMode.HALF_UP` **somente** nestas métricas derivadas (`projectedMonthSpend`, `dailyRemainingAllowance`). Entradas de ledger continuam `UNNECESSARY` / `Money` existente.
- **Por quê:** divisão por dias não é exata em centavos; ADR-0006 proíbe arredondamento silencioso nas fronteiras de dinheiro de ledger, não impede métrica de ritmo explicitamente arredondada para exibição.
- **Alternativa rejeitada:** expor mais de duas casas — quebra o contrato JSON de BRL do produto.

### 4. Forma da resposta

Lista de objetos por verba LIMIT visível, por exemplo:

- `envelopeId`, `name`
- `capacity`, `spent`, `projectedMonthSpend`, `dailyRemainingAllowance` (strings `"0.00"`)
- `projectedOverrunDay` (`YYYY-MM-DD` ou `null`)
- `month` (`YYYY-MM`)

Sem e-mail, sem descrição de gasto, sem PII extra. Autorização = mesma visibilidade owner/participante dos exports. `Cache-Control: no-store`. Mês inválido → `400 application/problem+json`.

### 5. Clientes (web, Android, desktop)

- Após (ou em paralelo a) carregar o summary do mês, chamar `limit-spending-pace` com o mesmo `month`.
- Mapear por `envelopeId` na preview da visão geral e nos cartões/lista LIMIT.
- Não recalcular no cliente; se a API falhar, omitir o bloco de ritmo sem quebrar o restante da overview.
- **MCP:** fora.

### 6. Testes

- Domínio/serviço: TDD das fórmulas (dia 1, spent 0, 28/29/30/31, estouro, já negativo).
- API: auth, participante vs terceiro, mês inválido.
- UI: cenários das deltas (web / Android validável / desktop Linux).

## Risks / Trade-offs

- **[Risk] Round-trip extra na overview** → Mitigação: uma chamada por mudança de mês; payload pequeno; sem cache.
- **[Risk] Interpretação de ADR-0014 “sem projeções”** → Mitigação: adendo explícito no ADR na aplicação; só cálculo efêmero.
- **[Risk] HALF_UP vs Money.UNNECESSARY** → Mitigação: tipo/helpers só para métricas de ritmo; testes fixam strings esperadas.
- **[Risk] Mês futuro com reference day 1** → Mitigação: documentado; UI tipicamente mostra mês corrente/passado.
- **[Trade-off] Não enriquecer summary** → Mais uma chamada nos três clientes; fronteira de módulo mais limpa.

## Migration Plan

1. Entregar API + OpenAPI + testes de backend.
2. Adendo ADR-0014.
3. Ligar web, depois Android, depois desktop (ordem flexível; todos no mesmo change).
4. Rollback = remover consumo nos clientes e desligar o endpoint; sem migração de dados.

## Open Questions

Nenhuma que altere specs ou a abordagem acima; detalhes de copy visual seguem `DESIGN.md` na aplicação.
