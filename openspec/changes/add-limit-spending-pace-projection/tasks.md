# Tasks

## 1. Domínio de ritmo (TDD)

- [x] 1.1 Escrever testes que falham para as fórmulas de `projectedMonthSpend`, `projectedOverrunDay` e `dailyRemainingAllowance` (dia 1, spent `0.00`, meses 28/29/30/31, estouro, capacity já negativa) e verificar que falham antes da implementação
- [x] 1.2 Implementar o cálculo puro (BigDecimal, HALF_UP só nas métricas de saída, `elapsedDays`/`remainingDays`/`referenceDate` conforme `design.md`) e verificar que os testes do 1.1 passam

## 2. API reporting

- [x] 2.1 Escrever testes de API/serviço que falham para `GET /api/v1/reports/limit-spending-pace` (owner com LIMIT, participante só compartilhadas, terceiro lista vazia, mês inválido `400`, sem PII extra) e verificar a falha inicial
- [x] 2.2 Implementar endpoint, DTO JSON, OpenAPI e reuso das portas de leitura do ledger; verificar que os testes do 2.1 passam e que os exports CSV/XLSX existentes continuam verdes
- [x] 2.3 Registrar adendo no ADR-0014 documentando o endpoint JSON sem persistência e verificar que o texto cita o path e a ausência de tabela/cache

## 3. Web

- [x] 3.1 Consumir `limit-spending-pace` no cliente web no mês da visão geral e verificar (teste ou inspeção tipada) o mapeamento por `envelopeId` sem recalcular ritmo
- [x] 3.2 Exibir dia de estouro ou allowance diária na Visão geral e no card LIMIT conforme specs `ui-web/overview` e `ui-web/envelope-card`; verificar com teste de UI ou story que GOAL/FIXED não mostram ritmo e que falha da API omite o bloco sem quebrar o summary

## 4. Android

- [x] 4.1 Adicionar chamada/contrato KMP para spending-pace e verificar teste de parsing/DTO com strings de duas casas
- [x] 4.2 Mostrar ritmo no card LIMIT (lista/visão geral) conforme `ui-mobile/cartao-de-verba` e verificar em teste Android ou UI test que participante não vê verbas não compartilhadas e que não-LIMIT omitem o indicador

## 5. Desktop

- [x] 5.1 Consumir o endpoint no cliente desktop para o mês selecionado e verificar o mapeamento por `envelopeId`
- [x] 5.2 Exibir ritmo na visão geral e na lista de verbas LIMIT conforme `ui-desktop/overview` e `ui-desktop/envelopes`; verificar que mês sem LIMIT não inventa indicadores

## 6. Verificação integrada

- [x] 6.1 Rodar as verificações documentadas no `README.md` relevantes a backend + canais tocados e verificar saída verde (ou registrar falhas pré-existentes não introduzidas por esta change)
