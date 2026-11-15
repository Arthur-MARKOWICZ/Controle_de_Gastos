# Tasks

## 1. Decisão e banco

- [x] 1.1 Escrever `docs/decisions/0032-busca-textual-com-pg-trgm.md`: contexto (#94), decisão (`pg_trgm` + índice GIN em `lower(description)`), alternativas (`ILIKE` nativo, `tsvector`, `unaccent`), consequências e quando revisar
- [x] 1.2 Criar `backend/src/main/resources/db/migration/V18__add_ledger_description_trgm_index.sql` com `CREATE EXTENSION IF NOT EXISTS pg_trgm` e o índice `ledger_entry_description_trgm_idx`
- [x] 1.3 Confirmar que o role do banco de produção pode criar a extensão *trusted* (dono do banco com `CREATE`) e registrar o resultado no ADR

## 2. API — busca e filtros (TDD)

- [x] 2.1 Teste de integração falhando (`HistorySearchApiIntegrationTest`, Testcontainers `postgres:18-alpine`): `q` sem diferenciar caixa ("Farmácia"/"farmácia"), termo vazio, `%`/`_` literais, gasto sem descrição
- [x] 2.2 Implementar em `LedgerEntryRepository.findHistory` o filtro de texto com flag (`LOWER(e.description) LIKE :pattern ESCAPE '\'`) e montar o pattern escapado no `LedgerService`, até o 2.1 passar
- [x] 2.3 Teste falhando para `envelopeId`: dono, participante, terceiro (200 + lista vazia + matchedTotal "0.00") e UUID inválido (400); implementar
- [x] 2.4 Teste falhando para `author` `ME`/`OTHERS` numa verba compartilhada (titular × participante) e valor inválido (400); implementar com enum interno
- [x] 2.5 Teste falhando para a combinação com `includeDeleted`: participante não vê excluídos e dono vê. Teste de regressão: sem os filtros novos, a lista é idêntica à de antes
- [x] 2.6 Teste falhando para `q` com mais de 140 caracteres após `trim` → 400 com mensagem em português; implementar a validação
- [x] 2.7 Teste falhando para `matchedTotal`: soma "48.00" com `size=1` e `hasNext` true, excluído fora da soma com `includeDeleted=true`, "0.00" sem resultado; implementar `sumHistory` e expor `matchedTotal` em `HistoryPage`/`HistoryPageResponse`
- [x] 2.8 Refatorar: uma assinatura `default` no repositório que converte nulos em flags, mantendo a lista numa única JPQL. A soma fica em SQL nativo com as mesmas condições (ver design D3) e os testes de integração verificam a paridade. Garantir que nenhum log novo recebe `q`
- [x] 2.9 Rodar `cd backend && ./gradlew test` (inclui teste de arquitetura modular)

## 3. Web

- [x] 3.1 Estender `api.getHistory` com filtros opcionais (`q`, `envelopeId`, `author`), sem enviar os vazios, e `HistoryPageDTO.matchedTotal`. Ajustar os chamadores e testes existentes
- [x] 3.2 Teste falhando em `HistoryPage`: ler e escrever `q`, `envelopeId` e `author` na URL, zerar `page` ao filtrar, "Limpar filtros" remover os três
- [x] 3.3 Implementar campo de busca com rótulo (debounce ~400 ms + Enter), seletor de verba ("Todas" + verbas visíveis) e filtro de autor ("Todos", "Lançados por mim", "Lançados por outras pessoas")
- [x] 3.4 Teste falhando e implementação: total dos resultados em BRL `pt-BR` visível só com filtro ativo, e estado vazio específico de busca com "Limpar filtros"
- [x] 3.5 Conferir que participante continua sem editar/excluir em resultados filtrados (teste existente ou novo)
- [x] 3.6 Rodar `cd web && pnpm test` e `pnpm lint`. Verificar no navegador, com a API local, a busca, o filtro, o recarregamento da URL e o estado vazio

## 4. Android (KMP)

- [x] 4.1 Adicionar `HistoryFilter(query, envelopeId)` com default vazio em `LedgerGateway.loadHistory` e `matchedTotal` em `HistoryPageView`. Atualizar `AndroidApiGateway`, o gateway indisponível em `Gateways.kt` e os fakes de teste
- [x] 4.2 Teste falhando em `HistoryControllerTest`: `search` zera página e envia o termo, trocar de mês preserva filtros, `filterEnvelope` e `clearFilters`
- [x] 4.3 Implementar o estado de filtro no `HistoryController` até o 4.2 passar
- [x] 4.4 Atualizar `HistoryScreen`: campo de busca com rótulo (`ImeAction.Search` + debounce), seletor de verba com "Todas", total dos resultados e estado vazio de busca com limpar filtros
- [x] 4.5 Rodar `cd mobile && ./gradlew :shared:jvmTest :androidApp:assembleDebug --no-configuration-cache`
- [x] 4.6 Extrair da `HistoryScreen` a decisão de total filtrado e estado vazio (`historyListContent`) e testá-la em `HistoryListContentTest`
- [x] 4.7 Testar a leitura de `matchedTotal` e dos itens em `androidApp` (`HistoryPageParsingTest`, com `junit` e `org.json` só para testes) e incluir `:androidApp:testDebugUnitTest` em `make mobile-check`/`mobile-unit-test` e no relatório do CI

## 5. Verificação final

- [x] 5.1 Rodar `make check`, que já cobre backend, web e MCP: confirmar que `mcp` continua passando com `matchedTotal` extra
- [x] 5.2 Avaliar se `docs/privacy/inventario-de-dados.md` deve citar o termo de busca como dado transitório não persistido. Atualizar se sim
- [x] 5.3 Rodar `openspec validate busca-e-filtros-no-historico --strict`
