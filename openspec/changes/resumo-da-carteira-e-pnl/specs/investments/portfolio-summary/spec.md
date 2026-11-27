# Spec Delta

## Purpose

Permite que o titular consulte o resumo da carteira de investimentos com
totais, agregação por ativo da B3, preço médio, detalhe de CDBs e P&L simples
desde a compra, sem misturar com o orçamento de verbas.

## ADDED Requirements

### Requirement: Titular reads portfolio summary
A API SHALL expor `GET /api/v1/investments/summary` para o titular autenticado.
A resposta 200 MUST trazer `complete`, totais monetários, `stocks` agregados
por ticker e `cdbs` por posição. Sem posições, MUST responder 200 com totais
zero, listas vazias e `complete` true.

#### Scenario: Empty portfolio
- **WHEN** o titular sem lotes nem CDBs consulta o resumo
- **THEN** a API responde 200 com `complete` true, totais "0.00", `stocks` [] e `cdbs` []

#### Scenario: Summary does not touch the budget
- **GIVEN** um titular com verbas, lotes e CDBs
- **WHEN** ele consulta o resumo
- **THEN** nenhum lançamento é criado e nenhum saldo de verba muda

### Requirement: Stocks are aggregated by ticker with average price
Cada item de `stocks` MUST agrupar os lotes do mesmo ticker: quantidade total,
`averagePrice` = soma dos custos / quantidade total arredondada a duas casas
com HALF_EVEN, `acquisitionCost`, e a lista de lotes. Tickers MUST aparecer
mesmo sem cotação.

#### Scenario: Two lots of the same ticker
- **GIVEN** o titular tem PETR4 com 100 a "38.47" e 50 a "40.00", com cotação "41.20"
- **WHEN** ele consulta o resumo
- **THEN** `stocks` tem um item PETR4 com quantity 150, averagePrice "38.98", acquisitionCost "5847.00", currentValue "6180.00" e pnl "333.00"

#### Scenario: Average price uses HALF_EVEN
- **GIVEN** lotes cujo quociente exato não cabe em duas casas
- **WHEN** o titular consulta o resumo
- **THEN** o `averagePrice` é o quociente arredondado a duas casas com HALF_EVEN

#### Scenario: Distinct tickers stay separate
- **GIVEN** o titular tem lotes de PETR4 e VALE3
- **WHEN** ele consulta o resumo
- **THEN** `stocks` tem um item por ticker

### Requirement: Stock lot detail is included under each ticker
Cada lote em `stocks[].lots` MUST trazer id, quantity, unitPrice,
acquisitionCost, purchaseDate e, quando houver cotação do ticker,
currentValue e pnl do lote. Sem cotação, currentValue e pnl do lote MUST ser
null.

#### Scenario: Lot pnl when quoted
- **GIVEN** um lote PETR4 de 100 a "38.47" e cotação "41.20"
- **WHEN** o titular consulta o resumo
- **THEN** o lote traz currentValue "4120.00" e pnl "273.00"

#### Scenario: Lot without quote has null current fields
- **GIVEN** um lote cujo ticker ainda não tem cotação
- **WHEN** o titular consulta o resumo
- **THEN** o lote traz currentValue e pnl null, e o ticker permanece em `stocks`

### Requirement: CDB positions contribute estimated value and pnl
Cada item de `cdbs` MUST trazer id, principal, estimatedValue e
pnl = estimatedValue − principal. O estimado MUST seguir a mesma regra da
listagem de CDBs. Todo CDB MUST entrar nos totais.

#### Scenario: CDB pnl is estimate minus principal
- **GIVEN** a data atual 2026-11-26 e um CDB com principal "10000.00", taxa "0.1250", início 2025-11-26 e vencimento 2027-11-26
- **WHEN** o titular consulta o resumo
- **THEN** o item traz estimatedValue "11250.00" e pnl "1250.00"

### Requirement: Totals include only positions with current value
`totalCost`, `totalCurrent` e `totalPnl` MUST somar só ações cotadas e todos
os CDBs. Tickers sem cotação MUST NOT entrar nesses totais. `complete` MUST
ser false se algum ticker de ação do titular não tiver cotação; caso
contrário true.

#### Scenario: Incomplete summary excludes unquoted ticker from totals
- **GIVEN** PETR4 cotada (custo "3847.00", atual "4120.00") e VALE3 sem cotação (custo "2000.00"), sem CDBs
- **WHEN** o titular consulta o resumo
- **THEN** complete é false, totalCost "3847.00", totalCurrent "4120.00", totalPnl "273.00", e VALE3 aparece em stocks com currentValue e pnl null

#### Scenario: Complete summary with stocks and cdbs
- **GIVEN** só tickers cotados e um CDB com pnl "1250.00"
- **WHEN** o titular consulta o resumo
- **THEN** complete é true e os totais somam custo, valor atual e pnl das ações cotadas mais o CDB

### Requirement: Portfolio summary is visible only to its titular
O resumo MUST refletir somente as posições do titular autenticado. Não há
participantes nem compartilhamento. Chamadas sem autenticação MUST receber
401. Logs MUST NOT registrar tickers com valores, totais ou P&L.

#### Scenario: Unauthenticated request
- **WHEN** um cliente sem autenticação chama o resumo
- **THEN** a API responde 401

#### Scenario: Another user does not see the titular's positions
- **GIVEN** o titular A tem lotes e CDBs
- **WHEN** o titular B consulta o resumo
- **THEN** a resposta 200 não inclui as posições de A
