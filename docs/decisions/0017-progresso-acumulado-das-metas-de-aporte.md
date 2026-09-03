# ADR-017: Progresso acumulado das metas de aporte

## Status

Aceito

## Data

2026-09-03

## Contexto

`GOAL` representa a expectativa mensal de aportes definida no ADR-014. O
saldo financeiro derivado da verba inclui a alocação-base automática, por isso
não representa o avanço de aportes explícitos: um card novo podia começar em
100% e crescer ao receber um aporte.

O titular precisa ver a pendência acumulada. Se uma meta mensal de R$ 100,00
recebe R$ 20,00 no primeiro mês, faltam R$ 80,00; no mês seguinte, passam a
faltar R$ 180,00 de R$ 200,00 planejados desde a criação.

## Decisão

- O backend deriva `goalProgress` somente para `GOAL` no resumo do ledger,
  sem tabela ou migração adicional.
- `plannedAmount` é `baseAmount` multiplicado pelos meses entre a criação e o
  mês consultado, inclusive. `contributedAmount` soma apenas aportes ativos
  até o fim desse mês. `remainingAmount` é o máximo entre zero e a diferença
  dos dois; `percent` é calculado no backend, arredondado para inteiro e
  limitado a 100.
- Gastos não alteram esse progresso; continuam afetando exclusivamente o
  saldo financeiro `available`, que preserva seu significado atual.
- O contrato REST expõe `goalProgress` anulável para manter uma única resposta
  de verba. Clientes apresentam o valor restante e usam o percentual pronto.
- O relatório de metas abaixo do esperado continua mensal, sem carry, conforme
  ADR-014.

## Alternativas consideradas

### Reutilizar `available`

Rejeitada porque mudaria o significado do saldo financeiro e quebraria os
clientes que o usam para limites, gastos e reservas.

### Calcular a porcentagem nos clientes

Rejeitada porque duplicaria a regra de carry e exigiria cálculo monetário em
web e mobile.

### Alterar o relatório mensal

Rejeitada porque o relatório responde se cada mês cumpriu sua expectativa; o
card responde qual é a pendência acumulada atual.

## Consequências

- OpenAPI, web e Android consomem o novo resumo derivado; iOS é preservado no
  código compartilhado, sem ser declarado validado neste ambiente.
- A regra usa dinheiro `Money`/`BigDecimal` e lançamentos ativos, preservando
  escala, autorização e exclusão lógica já existentes.
