# ADR-0022: Saldo mensal sem carry para limite de gasto e compromisso fixo

## Status

Aceito

## Data

2026-10-08

## Contexto

A fundação técnica e o ADR-007 trataram o saldo não utilizado como acumulável
entre meses para todas as verbas. Na prática, limite de gasto e compromisso
fixo são orçamento **do mês**: o titular planeja gastar até R$ X ou reservar
R$ X naquele mês. Somar o que sobrou do mês anterior ao valor-base faz o card
mostrar, por exemplo, R$ 200,00 de um limite de R$ 100,00 e distorce o
acompanhamento mensal.

Metas de aporte (`GOAL`), metas de acumulação (`SAVINGS_TARGET`) e gastos
anuais (`ANNUAL_EXPENSE`) continuam com saldo ou progresso que atravessa
meses. O dinheiro não alocado da renda também permanece acumulável.

## Decisão

- Para `LIMIT` e `FIXED`, o saldo financeiro do mês consultado é
  `baseAmount + aportes_do_mês − gastos_do_mês`, considerando apenas
  lançamentos ativos com `occurred_at` no mês (até a data consultada) e o
  valor-base uma única vez. Não há carry de saldo positivo ou negativo entre
  meses.
- Verbas arquivadas em mês anterior ao consultado não entram com saldo
  residual nesses propósitos.
- `GOAL`, `SAVINGS_TARGET` e `ANNUAL_EXPENSE` preservam as regras de
  acumulação já aceitas (ADR-014, ADR-015, ADR-016, ADR-017).
- Clientes e glossário deixam de afirmar que limite e compromisso acumulam o
  que sobrou; o cálculo continua exclusivo do backend.

## Alternativas consideradas

### Manter carry e só mudar a UI

Rejeitada: o `available` do ledger alimenta resumo, alertas de saldo negativo
e relatório de limites extrapolados. Exibir um restante “mensal” diferente do
saldo real manteria a regra financeira errada para o uso mensal.

### Remover carry de todos os propósitos

Rejeitada: metas de acumulação e gastos anuais dependem do saldo atravessar
meses; o progresso acumulado de `GOAL` (ADR-017) também.

## Consequências

- A fórmula documentada em privacidade e relatórios deixa de ser
  `base × meses` para `LIMIT`/`FIXED`.
- Relatório de limites extrapolados passa a avaliar o fechamento **do mês**,
  sem dívida ou sobra herdada.
- Textos da fundação técnica, web e mobile precisam refletir a distinção por
  propósito.
