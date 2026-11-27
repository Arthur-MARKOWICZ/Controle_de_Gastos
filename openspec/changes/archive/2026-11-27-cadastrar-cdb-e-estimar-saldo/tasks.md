# Tasks

## 1. Dependência big-math

- [x] 1.1 Adicionar `ch.obermuhlner:big-math` (2.x estável) em `backend/build.gradle.kts` e verificar que `./gradlew :backend:dependencies --configuration compileClasspath` lista o artefato

## 2. Domínio da posição e estimativa (TDD)

- [x] 2.1 Testes falhando para a taxa anual: `"0.1250"` é aceita; `"12.50"`, `"0.125"`, `"0.12500"`, `"0.0000"`, `"-0.0100"`, nulo e vazio lançam `IllegalArgumentException` (design D2). Implementar `AnnualRate` (ou validação equivalente) até passarem
- [x] 2.2 Testes falhando para `CdbPosition.estimatedValue`: com principal `"10000.00"`, taxa `"0.1250"`, início 2025-11-26 e hoje 2026-11-26 (vencimento depois) resulta em `"11250.00"`; com hoje igual ao início o estimado é o principal; com hoje depois do vencimento o estimado usa o vencimento e não a data atual; principal ≤ 0, início futuro e vencimento ≤ início são recusados (design D1, D3, D4). Implementar a entidade JPA com `MoneyJpaConverter` e o cálculo via `BigDecimalMath.pow` + `DECIMAL128`, arredondando só o resultado com `HALF_EVEN`

## 3. Persistência

- [x] 3.1 Criar `V24__create_cdb_position.sql` com a tabela, os `CHECK` e o índice `(owner_id, start_date)` do design D6. Verificar: o Flyway aplica a migration ao subir o contexto em `./gradlew test` (ou um teste de repositório)
- [x] 3.2 Criar `CdbPositionRepository` com `findByIdAndOwnerId` e a listagem do dono ordenada por `start_date, created_at`. Verificar com teste de repositório (ou coberto pelo teste de API) que uma posição de outro dono não é encontrada

## 4. Aplicação e API (TDD)

- [x] 4.1 Testes falhando para `CdbPositionService` com `FixedClockTestConfiguration` (2026-11-26 em America/Sao_Paulo): `register` usa o titular atual e recusa início 2026-11-27; `list` traz só as posições do titular com `estimatedValue` recalculado; `delete` de posição alheia ou inexistente lança `CdbPositionNotFoundException`. Implementar o service
- [x] 4.2 Testes de integração de API falhando (`CdbPositionApiIntegrationTest`) cobrindo os cenários de `specs/investments/cdb-positions/spec.md`: 201 com `estimatedValue` `"11250.00"`; segundo CDB; 400 para taxa, principal e datas inválidos; ordem da listagem; lista vazia; 204 na exclusão e 404 para id inexistente; estimado congela no vencimento. Implementar `CdbPositionController` e o `@RestControllerAdvice` com `CDB_POSITION_NOT_FOUND` (design D5)
- [x] 4.3 Testes de autorização na API: o usuário B não vê os CDBs de A; B recebe 404 ao excluir o de A, e a posição continua existindo; um participante de verba de A não ganha acesso; chamadas sem token recebem 401 nos três endpoints. Verificar: todos passam
- [x] 4.4 Teste falhando: cadastrar e excluir um CDB não cria `ledger_entry` nem altera o saldo de verba do titular. Verificar: o cenário "Registering a CDB does not touch the budget" passa

## 5. Direitos do titular e privacidade

- [x] 5.1 Teste falhando em `TitularRightsApiIntegrationTest`: a exportação traz `cdbPositions`, com `principal` como string de duas casas, `currency` "BRL", taxa com quatro casas e sem `estimatedValue`; depois da exclusão da conta, `cdb_position` não tem linhas do titular. Implementar em `TitularRightsService` (design D7)
- [x] 5.2 Atualizar `docs/privacy/inventario-de-dados.md` com a linha de `cdb_position` (colunas, finalidade, retenção até a exclusão da conta, inclusão na exportação e ausência de valores nos logs). Verificar: a linha cita o ADR-0034 e bate com a spec

## 6. Verificação integrada

- [x] 6.1 Rodar `cd backend && ./gradlew check`. Verificar: a suíte completa, a arquitetura modular e o limite de cobertura JaCoCo passam
- [x] 6.2 Revisar os logs gerados pelos testes da API de CDB. Verificar: nenhum principal, taxa ou valor estimado aparece em log
