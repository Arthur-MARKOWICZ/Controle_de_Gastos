# ADR-016: Gastos anuais com provisão até o vencimento

## Status

Aceito

## Data

2026-09-01

## Contexto

IPVA e assinaturas anuais têm um valor total, vencem em um mês e dia e não se comportam como uma meta de acumulação livre. O titular pode reservar uma parcela a cada mês até o vencimento ou optar por registrar o pagamento inteiro quando ele ocorrer. A reserva não pode desaparecer na mudança de mês e o pagamento real deve continuar explícito no ledger.

## Decisão

- Adicionar o propósito `ANNUAL_EXPENSE`, com `annualAmount`, `dueMonth`, `dueDay` e `fundingMode` (`MONTHLY` ou `ONE_TIME`). Ele sempre usa `baseAmount = 0,00`.
- No modo mensal, o backend divide o valor anual entre todos os meses do ciclo, do mês corrente até o mês do próximo vencimento, inclusive. Centavos residuais ficam na(s) última(s) parcela(s), para que a soma seja exatamente o valor anual.
- Ao passar o vencimento, o ciclo seguinte começa no mês posterior e termina no mesmo mês de vencimento do ano seguinte. Em 29 de fevereiro, o vencimento ocorre em 28 de fevereiro nos anos não bissextos.
- O modo único não cria provisão mensal. Nenhum modo cria automaticamente um lançamento de gasto: o titular ou participante autorizado registra o pagamento real.
- O saldo é calculado pelo mesmo ledger acumulável das demais verbas; saldo negativo continua permitido.

## Alternativas consideradas

### Reutilizar `SAVINGS_TARGET`

Rejeitada porque meta livre não possui vencimento, recorrência ou modo de provisão.

### Reutilizar `FIXED` com valor anual dividido por doze

Rejeitada porque perde a data de vencimento, não cobre ciclos parciais e não distribui centavos de modo exato.

### Gerar automaticamente o gasto no vencimento

Rejeitada porque o produto registra gastos reais manualmente.

## Consequências

- O contrato REST, o OpenAPI e os clientes reconhecem o novo propósito e sua configuração condicional.
- Os campos são dados financeiros pessoais, sujeitos a autorização por recurso, retenção, exportação e exclusão já definidos.
- O relatório de gastos por tipo pode incluir `ANNUAL_EXPENSE`; relatórios de limite e de metas não mudam neste corte.
