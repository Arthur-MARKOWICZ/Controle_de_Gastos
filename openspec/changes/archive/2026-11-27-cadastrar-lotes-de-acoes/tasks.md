# Tasks

## 1. Estrutura do módulo e fronteira

- [x] 1.1 Criar `br.com.controlegastos.investments` com `package-info.java` (`@ApplicationModule(displayName = "Investimentos")`) e os subpacotes `domain`, `application`, `infrastructure` e `web` (design D8). Verificar: `./gradlew test --tests '*ModularityTest'` passa e o módulo aparece na verificação
- [x] 1.2 Teste falhando de fronteira: `investments` depende só de `shared` e `identity`, e nenhum módulo do orçamento depende de `investments`. Implementar a asserção (no `ModularityTest` ou num `ApplicationModuleTest`) até ela passar

## 2. Dinheiro: multiplicação por quantidade (TDD)

- [x] 2.1 Teste falhando em `shared/money`: `Money.brl("38.47").multiply(100)` resulta em "3847.00"; quantidade 0 ou negativa lança `IllegalArgumentException`; um resultado acima de 17 dígitos inteiros lança `IllegalArgumentException`. Implementar `Money.multiply(long)` (design D1). Verificar: os testes de `MoneyTest` passam

## 3. Domínio do lote (TDD)

- [x] 3.1 Testes falhando para `B3Ticker.of`: " petr4f " vira "PETR4"; "TAEE11", "BOVA11", "MXRF11" e "AAPL34" são aceitos; "PETR", "PETR123", "PE4", "PETR-4", nulo e vazio lançam `IllegalArgumentException` (design D2). Implementar até passarem
- [x] 3.2 Testes falhando para `StockLot`: custo de aquisição = quantidade × preço unitário; quantidade ≤ 0 é recusada; preço ≤ 0 é recusado; data posterior a "hoje" é recusada e a data igual a "hoje" é aceita, com "hoje" passado como argumento (design D1 e D3). Implementar a entidade JPA com `MoneyJpaConverter`

## 4. Persistência

- [x] 4.1 Criar `V22__create_stock_lot.sql` com a tabela, os `CHECK` e o índice `(owner_id, purchase_date)` do design D6. Verificar: o Flyway aplica a migration ao subir o contexto em `./gradlew test`
- [x] 4.2 Criar `StockLotRepository` com `findByIdAndOwnerId` e a listagem do dono ordenada por `purchase_date, ticker, created_at`. Verificar com um teste de repositório (ou coberto pelo teste de API da tarefa 5.2) que um lote de outro dono não é encontrado

## 5. Aplicação e API (TDD)

- [x] 5.1 Testes falhando para `StockLotService` com `FixedClockTestConfiguration` (2026-11-26 em America/Sao_Paulo): `register` usa o titular atual e recusa a data 2026-11-27; `list` traz só os lotes do titular; `delete` de um lote alheio ou inexistente lança `StockLotNotFoundException`. Implementar o service
- [x] 5.2 Testes de integração de API falhando (`StockLotApiIntegrationTest`) cobrindo os cenários de `specs/investments/stock-lots/spec.md`: 201 com custo "3847.00"; segundo lote do mesmo ticker; normalização do ticker; 400 para ticker, quantidade (0, -5, 1.5, "100"), preço ("0.00", "-1.00", "38.475") e data inválidos; ordem da listagem; lista vazia; 204 na exclusão e 404 para um id inexistente. Implementar `StockLotController`, o `MoneyJson` local e o `@RestControllerAdvice` com `STOCK_LOT_NOT_FOUND` (design D4 e D5)
- [x] 5.3 Testes de autorização na API: o usuário B não vê os lotes de A; B recebe 404 ao excluir o lote de A, e o lote continua existindo; um participante de verba de A não ganha acesso; chamadas sem token recebem 401 nos três endpoints. Verificar: todos passam
- [x] 5.4 Teste falhando: cadastrar e excluir um lote não cria `ledger_entry` nem altera o saldo de verba do titular. Verificar: o cenário "Registering a lot does not touch the budget" passa

## 6. Direitos do titular e privacidade

- [x] 6.1 Teste falhando em `TitularRightsApiIntegrationTest`: a exportação traz `stockLots`, com `unitPrice` como string de duas casas e `currency` "BRL"; depois da exclusão da conta, `stock_lot` não tem linhas do titular. Implementar em `TitularRightsService` (design D7)
- [x] 6.2 Atualizar `docs/privacy/inventario-de-dados.md` com a linha de `stock_lot` (colunas, finalidade, retenção até a exclusão da conta, inclusão na exportação e ausência de ticker e valores nos logs). Verificar: a linha cita o ADR-0034 e bate com a spec

## 7. Verificação integrada

- [x] 7.1 Rodar `cd backend && ./gradlew check`. Verificar: a suíte completa, a arquitetura modular e o limite de cobertura JaCoCo passam
- [x] 7.2 Revisar os logs gerados pelos testes da API de lotes. Verificar: nenhum ticker, quantidade ou valor aparece em log
