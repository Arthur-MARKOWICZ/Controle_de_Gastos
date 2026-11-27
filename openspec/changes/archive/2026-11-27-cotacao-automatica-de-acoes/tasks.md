# Tasks

## 1. Decisão registrada

- [x] 1.1 Escrever `docs/decisions/0035-provedor-de-cotacao-de-acoes.md` (status Aceito, filho do ADR-0034) com provedor, autenticação por header, cadência, orçamento de requisições, mapeamento de falhas, exceção de arredondamento `HALF_EVEN`, alternativas rejeitadas, risco de várias instâncias e a pendência de conferir os termos de uso antes de ligar o token (design D1). Verificar: o ADR cita os ADRs 0034, 0006 e 0003 e bate com o design
- [x] 1.2 Acrescentar a referência ao ADR-0035 em `docs/ideas/fundacao-tecnica.md`, ao lado da menção ao ADR-0034. Verificar: o link resolve

## 2. Domínio e persistência da cotação

- [x] 2.1 Criar `V23__create_stock_quote.sql` com a tabela e os `CHECK` do design D2. Verificar: o Flyway aplica a migration ao subir o contexto em `./gradlew test`
- [x] 2.2 Criar a entidade `StockQuote` (`ticker` como id, `price` em `Money`, `quotedAt`, `fetchedAt`) e o `StockQuoteRepository` com `findHeldTickers`, `findMissingTickers`, `deleteOrphans` e o `upsert` nativo que não regride no tempo (design D2, D5 e D6)
- [x] 2.3 Teste de repositório falhando (Testcontainers): o upsert grava; um upsert com `quoted_at` mais antigo não sobrescreve; `findHeldTickers` traz tickers distintos de vários titulares; `findMissingTickers` exclui os já cotados; `deleteOrphans` apaga só tickers sem lote. Implementar até passar

## 3. Porta e adaptador da brapi (TDD)

- [x] 3.1 Criar `QuoteProvider` e o `sealed` `QuoteResult` em `investments.application` (design D3)
- [x] 3.2 Testes falhando em `BrapiQuoteProviderTest` com `MockRestServiceServer`: envia `Authorization: Bearer <token>` e nenhum `token` na URL; 200 com `regularMarketPrice` 38.470001 vira "38.47"; 10.125 vira "10.12" e 10.135 vira "10.14"; `regularMarketTime` vira `quotedAt`; `results` vazio, preço ausente, 0 ou negativo viram `NotFound`; 404 vira `NotFound`; 429 vira `RateLimited`; 401 e 403 viram `Rejected`; 500, 400 e JSON inválido viram `Unavailable`; `regularMarketTime` ausente vira `Unavailable("bad_payload")`; token em branco faz `isConfigured()` falso
- [x] 3.3 Implementar `BrapiQuoteProvider` (com `RestClient` próprio, timeouts de 3 s e 5 s) com o `JsonMapper` local `USE_BIG_DECIMAL_FOR_FLOATS` (design D4), até os testes da 3.2 passarem. Verificar: nenhuma mensagem de exceção do adaptador contém o corpo da resposta nem o token
- [x] 3.4 Adicionar `app.investments.quotes.*` ao `application.yml` e `INVESTMENTS_QUOTES_BRAPI_TOKEN` ao `compose.yaml`, com padrão vazio (design D8). Verificar: `./gradlew test` sobe sem token

## 4. Rodadas de atualização (TDD)

- [x] 4.1 Testes falhando em `StockQuoteRefresherTest` (Mockito e `Clock` fixo): `refreshHeld` consulta cada ticker distinto uma vez e grava os `Quoted`; `NotFound` segue para o próximo; `RateLimited` para a rodada sem consultar os restantes; `Rejected` para a rodada; 3 `Unavailable` seguidos param a rodada; `refreshMissing` consulta só os tickers sem cotação; sem token nenhum fetch acontece; `deleteOrphans` roda antes das consultas; com o lock ocupado a rodada é pulada; mais tickers que `max-tickers` gera o aviso e ainda consulta todos
- [x] 4.2 Implementar `StockQuoteRefresher` com os dois `@Scheduled` (cron configurável, zona America/Sao_Paulo), a guarda não reentrante, as transações curtas por escrita e o log final só com contagens e código de parada (design D5 e D6), até os testes da 4.1 passarem
- [x] 4.3 Teste falhando: durante uma rodada com falhas, nenhum `stock_lot` é alterado (mesmos ticker, quantidade, preço e data antes e depois). Verificar: o cenário "Timeout keeps the previous quote" passa

## 5. Cotação e valor atual na API (TDD)

- [x] 5.1 Testes falhando em `StockLotServiceTest`: `list` devolve cada lote com a cotação do seu ticker, ou nula, usando uma única consulta de cotações para todos os tickers; `currentValue` = quantidade × preço; `register` devolve a cotação já gravada do ticker sem chamar o `QuoteProvider` (design D7). Implementar `StockLotView` e ajustar o service
- [x] 5.2 Testes de integração falhando em `StockLotApiIntegrationTest`: lote de "PETR4" (100 × "38.47") com cotação "41.20" gravada traz `currentQuote.price.amount` "41.20", `quotedAt` em ISO UTC, `currentValue.amount` "4120.00" e `acquisitionCost` "3847.00"; um lote sem cotação traz `currentQuote` e `currentValue` como `null` explícito; o POST responde 201 com os campos nulos para um ticker sem cotação; o titular B vê só os próprios lotes, com a cotação compartilhada aplicada. Ajustar `StockLotController` até passarem

## 6. Privacidade e logs

- [x] 6.1 Teste de integração falhando: depois da exclusão da conta do único titular com "MGLU3", uma rodada remove a cotação de "MGLU3", e a exportação do titular não ganha chave de cotação. Implementar se faltar algo
- [x] 6.2 Atualizar `docs/privacy/inventario-de-dados.md` com a linha de `stock_quote` (colunas, finalidade, sem `owner_id`, removida quando nenhum lote usa o ticker, fora da exportação, ticker e preço nunca juntos em log) citando o ADR-0035. Verificar: a linha bate com a spec
- [x] 6.3 Teste falhando com `OutputCaptureExtension`: uma rodada com sucesso, `NotFound` e `Unavailable` loga só contagens e códigos, sem tickers, preços ou o token. Ajustar os logs até passar

## 7. Verificação integrada

- [x] 7.1 Smoke test manual sem token: `curl https://brapi.dev/api/quote/PETR4` confirma `results[0].regularMarketPrice` e `regularMarketTime` no formato usado pelo adaptador. Registrar o resultado (só o formato, sem valores) no PR. Se o formato divergir, ajustar o adaptador e os testes da 3.2
- [x] 7.2 Rodar `cd backend && ./gradlew check`. Verificar: a suíte completa, o `ModularityTest` (com `investments` dependendo só de `shared` e `identity`) e o limite de cobertura JaCoCo passam
- [x] 7.3 Rodar `openspec validate cotacao-automatica-de-acoes --strict`. Verificar: passa sem avisos
