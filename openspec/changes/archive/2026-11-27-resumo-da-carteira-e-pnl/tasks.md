# Tasks

## 1. Agregação e P&L (TDD)

- [x] 1.1 Testes falhando para o agregador de ações: dois lotes do mesmo ticker (100×"38.47" + 50×"40.00", cotação "41.20") produzem quantity 150, averagePrice "38.98" (HALF_EVEN), acquisitionCost "5847.00", currentValue "6180.00", pnl "333.00" e lotes com pnl individual; ticker sem cotação fica com currentValue/pnl null e averagePrice/custo ainda calculados; tickers distintos não se misturam (design D2, D3). Implementar o helper/agregador até passarem
- [x] 1.2 Testes falhando para o resumo completo em memória: carteira vazia → complete true e totais "0.00"; misturar ticker cotado + ticker sem cotação → complete false e totais só do cotado; CDB com principal "10000.00" e estimated "11250.00" entra nos totais com pnl "1250.00"; P&L total = soma dos pnls incluídos. Implementar o montador de `PortfolioSummary` (records de aplicação) até passarem

## 2. Serviço e API (TDD)

- [x] 2.1 Testes falhando para `PortfolioSummaryService` com `FixedClockTestConfiguration`: `summarize` usa o titular atual, lê lotes/cotações/CDBs existentes, ordena stocks por ticker e cdbs por start_date/created_at, e não chama `QuoteProvider`. Implementar o service (`@Transactional(readOnly = true)`, design D1)
- [x] 2.2 Testes de integração de API falhando (`PortfolioSummaryApiIntegrationTest`) cobrindo os cenários de `specs/investments/portfolio-summary/spec.md`: carteira vazia; agregação PETR4 com averagePrice "38.98"; lote sem cotação com nulls e complete false; CDB com pnl "1250.00"; totais parciais; consultar o resumo não cria `ledger_entry` nem altera saldo de verba. Implementar `PortfolioSummaryController` em `GET /api/v1/investments/summary` com `MoneyJson` (design D4)
- [x] 2.3 Testes de autorização na API: sem token → 401; titular B não vê posições de A no resumo; participante de verba de A não ganha acesso ao resumo de A. Verificar: todos passam

## 3. Verificação integrada

- [x] 3.1 Rodar `cd backend && ./gradlew check`. Verificar: a suíte completa, a arquitetura modular e o limite de cobertura JaCoCo passam
- [x] 3.2 Revisar os logs gerados pelos testes do resumo. Verificar: nenhum ticker com valor, total ou P&L aparece em log
