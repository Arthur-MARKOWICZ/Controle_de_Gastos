# Design

## Context

Ver `proposal.md` (Why) e `specs/investments/stock-quotes/spec.md`. O ADR-0034
já fixou a porta `QuoteProvider`, a última cotação persistida no PostgreSQL, o
`@Scheduled` em America/Sao_Paulo e a proibição de logar tickers com valores.
O que o código atual impõe:

- O módulo `investments` (#76) tem `StockLot` (ticker já normalizado por
  `B3Ticker`), `StockLotService`, `StockLotRepository` e
  `StockLotController`, com o `MoneyJson` local. A última migration é a `V22`.
- `Money.brl(BigDecimal)` recusa mais de duas casas. Portanto o arredondamento
  precisa acontecer antes de criar o `Money`.
- `identity` tem um `RestClient` com `JdkClientHttpRequestFactory` e timeouts
  de 3 s e 5 s, mas a configuração é package-private. `investments` não pode
  depender de `identity.config`.
- `@EnableScheduling` já está ligado. Os jobs existentes (`DailyReminderService`
  e `RecurringOccurrenceGenerator`) usam `@Scheduled(cron, zone)` e logam só
  contagens. O pool de scheduling é o padrão do Spring Boot, com uma thread.
- Os testes de clientes HTTP usam `MockRestServiceServer`
  (`GitHubOAuthClientTest`). Os serviços agendados são testados como unidade,
  com Mockito e `Clock` fixo.
- Os segredos de runtime entram por variável de ambiente no `application.yml`
  e são repassados em `compose.yaml`, como `NOTIFICATIONS_VAPID_*`, com vazio
  desligando o recurso.
- O spike (exploração da #77) levantou o plano gratuito da brapi.dev: 15.000
  requisições por ciclo mensal, 1 ticker por requisição, 1 requisição
  simultânea e dados atualizados a cada 30 minutos. Token em `Authorization:
  Bearer` ou em `?token=`. Endpoint `GET /api/quote/{ticker}`, com
  `results[].regularMarketPrice` (número JSON) e `regularMarketTime` (ISO
  8601). Códigos: 429 para cota ou simultaneidade, 401 para token, 403 para
  plano e 404 para recurso inexistente. Sem token, só PETR4, MGLU3, VALE3 e
  ITUB4 respondem, o que serve de smoke test.

## Goals / Non-Goals

**Goals:**

- Isolar o provedor atrás da porta, para que trocar de provedor mexa só em
  `infrastructure` e no ADR.
- Manter o consumo dentro do plano gratuito com folga e nunca fazer duas
  requisições simultâneas ao provedor.
- A listagem de lotes não depende do provedor em tempo de requisição.

**Non-Goals:**

- Detecção de feriados e circuit breaker genérico.
- Métricas Micrometer das rodadas. Os logs de contagem bastam por agora.
- Rodar a atualização em mais de uma instância da API. A implantação atual
  tem uma só.

## Decisions

### D1. ADR-0035 registra provedor, cadência, orçamento e arredondamento

`docs/decisions/0035-provedor-de-cotacao-de-acoes.md`, filho do ADR-0034:

- **Provedor**: brapi.dev, plano gratuito, token por `Authorization: Bearer`
  vindo de `INVESTMENTS_QUOTES_BRAPI_TOKEN`. Nunca vai na URL, porque URLs
  aparecem em logs de acesso e em mensagens de exceção.
- **Cadência**: pregão a cada 30 minutos, de segunda a sexta, das 10h00 às
  17h30 (16 rodadas). A rodada de tickers sem cotação roda de hora em hora,
  aos 15 minutos.
- **Orçamento**: 16 × cerca de 22 pregões = 352 requisições por ticker por
  mês. São 42 tickers no limite da cota, ou cerca de 30 deixando folga para a
  rodada de faltantes e para os feriados. O limite de 30 é configurável e, ao
  ser passado, gera um aviso no log.
- **Arredondamento**: exceção à regra de não arredondar na fronteira, porque a
  cotação é um dado externo de referência e não um valor informado pelo
  titular. O tick da B3 é R$ 0,01, então o arredondamento só remove ruído de
  ponto flutuante. Leitura como `BigDecimal` a partir do texto JSON e
  `setScale(2, HALF_EVEN)`, o mesmo modo do CDB no ADR-0034.
- **Alternativas rejeitadas**: HG Brasil (400 requisições por dia e vários
  tickers por requisição, mas a chave só vai em `?key=`), Alpha Vantage (limite
  gratuito baixo demais), Yahoo `.SA` (sem API oficial), B3 for Developers
  (exige contrato institucional) e recusar preços com mais de 2 casas (decisão
  do titular na exploração).
- **Termos de uso da brapi**: a página estava fora do ar no spike. O ADR
  registra que é preciso conferir no painel antes de ligar o token em
  produção.

A fundação técnica (`docs/ideas/fundacao-tecnica.md`) ganha a referência ao
ADR-0035 ao lado da menção ao ADR-0034.

### D2. Cotação como entidade `StockQuote`, chave pelo ticker

`investments.domain.StockQuote` guarda `ticker` (`@Id`, `String` de
`B3Ticker`), `price` (`Money`), `quotedAt` e `fetchedAt` (`Instant`). Não tem
`owner_id`, porque o preço de um ticker é o mesmo para todos.

```sql
-- V23__create_stock_quote.sql
CREATE TABLE stock_quote (
    ticker     VARCHAR(6) PRIMARY KEY CHECK (ticker ~ '^[A-Z]{4}[0-9]{1,2}$'),
    price      NUMERIC(19, 2) NOT NULL CHECK (price > 0),
    quoted_at  TIMESTAMPTZ NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL
);
```

Não há FK para `stock_lot`, porque o ticker não é único lá. A limpeza de
órfãos (D6) cumpre esse papel.

A gravação é um upsert nativo que não regride no tempo:

```sql
INSERT INTO stock_quote (ticker, price, quoted_at, fetched_at)
VALUES (?, ?, ?, ?)
ON CONFLICT (ticker) DO UPDATE
   SET price = EXCLUDED.price, quoted_at = EXCLUDED.quoted_at,
       fetched_at = EXCLUDED.fetched_at
 WHERE stock_quote.quoted_at <= EXCLUDED.quoted_at;
```

- *Alternativa: guardar a cotação dentro de `stock_lot`.* Rejeitada porque
  duplicaria o preço entre lotes e titulares, e uma atualização teria de tocar
  dados pessoais.
- *Alternativa: `save` do JPA.* Rejeitada porque o merge faz um `SELECT` antes
  e não impede que uma resposta atrasada sobrescreva uma cotação mais nova.

### D3. Porta `QuoteProvider` com resultado fechado, sem exceções

```java
// investments.application
public interface QuoteProvider {
    boolean isConfigured();
    QuoteResult fetch(B3Ticker ticker);
}

public sealed interface QuoteResult {
    record Quoted(Money price, Instant quotedAt) implements QuoteResult {}
    record NotFound() implements QuoteResult {}        // ticker desconhecido, preço ausente ou <= 0
    record RateLimited() implements QuoteResult {}     // 429
    record Rejected(int status) implements QuoteResult {}    // 401, 403: credencial ou plano
    record Unavailable(String code) implements QuoteResult {} // timeout, IO, 5xx, 400, JSON inválido
}
```

O refresher decide o que fazer com um `switch` exaustivo, e o adaptador
concentra o mapeamento HTTP.

- *Alternativa: exceções por tipo de falha.* Rejeitada porque o fluxo normal
  da rodada (ticker desconhecido) viraria controle por exceção, e seria fácil
  um `catch (Exception)` engolir a diferença entre "parar" e "continuar".

### D4. Adaptador `BrapiQuoteProvider`

- Fica em `investments.infrastructure`, com um `RestClient` próprio
  (`JdkClientHttpRequestFactory`, conexão de 3 s e leitura de 5 s), no mesmo
  formato de `identity`. Esse `RestClient` é montado dentro do adaptador, e não
  exposto como bean: um segundo bean `RestClient` deixaria ambígua a injeção
  sem qualificador dos clientes OAuth de `identity`. Um construtor
  package-private recebe o `RestClient` para os testes com
  `MockRestServiceServer`.
- Faz `GET {baseUrl}/api/quote/{ticker}` com `Authorization: Bearer {token}`.
  `baseUrl` é configurável (padrão `https://brapi.dev`) para os testes.
- O corpo é lido como `String` e interpretado por um `JsonMapper` local com
  `USE_BIG_DECIMAL_FOR_FLOATS`, para que `regularMarketPrice` nunca passe por
  `double`. O mapper global não muda.
- Preço: `decimalValue().setScale(2, HALF_EVEN)`, seguido de `Money.brl`.
  Preço ausente, zero ou negativo, ou `results` vazio, viram `NotFound`.
- `quotedAt` vem de `Instant.parse(regularMarketTime)`. Se esse campo faltar
  ou for inválido, o resultado é `Unavailable("bad_payload")`, para não
  inventar um instante de mercado.
- Mapeamento HTTP: 404 → `NotFound`; 429 → `RateLimited`; 401 e 403 →
  `Rejected`; 400, outros 4xx e 5xx → `Unavailable("http_<status>")`; timeout
  e IO → `Unavailable("io")`.
- `isConfigured()` é verdadeiro quando o token não está em branco.
- O adaptador nunca loga. Ele não inclui o corpo da resposta nem o token em
  mensagens e não propaga `RestClientResponseException`, cuja mensagem leva o
  corpo.

### D5. `StockQuoteRefresher`: duas rodadas, um ticker por vez

`investments.application.StockQuoteRefresher`:

```
refreshHeld()     @Scheduled(cron = "${app.investments.quotes.trading-cron:0 0/30 10-17 * * MON-FRI}", zone = SP)
refreshMissing()  @Scheduled(cron = "${app.investments.quotes.missing-cron:0 15 * * * *}", zone = SP)

run(tickers):
  if !provider.isConfigured()  -> return            (log de "desligado" uma vez, no startup)
  if !running.compareAndSet()  -> log skipped; return
  deleteOrphans()
  tickers = held | missing     (DISTINCT, ordenados)
  if tickers.size > maxTickers -> log.warn quote_budget_exceeded tickers=N limit=M
  for t in tickers:
     switch provider.fetch(t):
       Quoted      -> upsert; updated++
       NotFound    -> notFound++; continue
       Unavailable -> failed++; consecutive++; if consecutive >= 3: stop
       RateLimited -> stop("rate_limited")
       Rejected    -> stop("rejected_<status>")
  log.info quote_refresh mode=held|missing tickers=N updated=U not_found=F failed=X stopped=<code|none>
```

- **Sequencial, sob uma guarda `AtomicBoolean` (não reentrante)**, para
  cumprir o limite de 1 requisição simultânea mesmo se o pool de scheduling
  crescer ou se as duas rodadas coincidirem. Se a guarda estiver ocupada, a
  rodada é pulada, e não enfileirada. Uma exceção inesperada (por exemplo, do
  banco) é logada só pelo tipo e libera a guarda.
- **Sem transação envolvendo as chamadas HTTP**: cada upsert é uma escrita
  curta, e a leitura de tickers e a limpeza também têm transação própria.
- **Parada após 3 indisponibilidades seguidas**: com o provedor fora do ar,
  isso evita prender a thread do scheduler por 30 × 5 s.
- Consultas no `StockQuoteRepository`: `findHeldTickers()` (`SELECT DISTINCT
  ticker FROM stock_lot ORDER BY ticker`), `findMissingTickers()` (os
  mantidos sem linha em `stock_quote`), `deleteOrphans()` e `upsert(...)`.

- *Alternativa: buscar a cotação logo após o cadastro (evento depois do
  commit).* Rejeitada porque acoplaria o POST, mesmo que de forma assíncrona,
  ao limite de simultaneidade. A rodada horária cobre o caso com até uma hora
  de atraso.
- *Alternativa: ShedLock.* Desnecessário com uma instância. Fica registrado
  como risco.

### D6. Limpeza de órfãos no início de cada rodada

```sql
DELETE FROM stock_quote q
 WHERE NOT EXISTS (SELECT 1 FROM stock_lot l WHERE l.ticker = q.ticker);
```

A limpeza cobre tanto a exclusão do último lote quanto a exclusão da conta.
`privacy.TitularRightsService` não muda, porque `stock_quote` não guarda nada
do titular e fica fora da exportação. Com o recurso desligado (sem token)
não há rodada nem limpeza. Também não há cotação nova, e as órfãs que já
existem saem na primeira rodada depois que o token volta. O inventário
registra esse comportamento.

### D7. Leitura na listagem sem N+1

`StockLotService.list()` passa a devolver `List<StockLotView>`, com
`record StockLotView(StockLot lot, StockQuote quote)` e `quote` nulo quando não
há cotação. As cotações vêm em uma única consulta, `findAllById(tickers
distintos dos lotes)`, montada num `Map`. `register` também devolve um
`StockLotView`, usando a cotação já gravada do ticker, se houver, e nunca
chama o provedor.

`StockLotView.currentValue()` é `quote.price().multiply(lot.quantity())`.

JSON do item (campos novos):

```json
"currentQuote": { "price": { "amount": "41.20", "currency": "BRL" },
                  "quotedAt": "2026-11-26T20:00:00Z" },
"currentValue": { "amount": "4120.00", "currency": "BRL" }
```

Sem cotação, os dois campos vêm `null`. Os dois são sempre serializados, para
o cliente não precisar distinguir entre campo ausente e campo nulo.

- *Alternativa: endpoint separado `/investments/quotes`.* Rejeitada porque o
  cliente teria de cruzar os dados, e a #79 vai agregar a partir do mesmo
  serviço.

### D8. Configuração

```yaml
app:
  investments:
    quotes:
      brapi-token: ${INVESTMENTS_QUOTES_BRAPI_TOKEN:}
      brapi-base-url: ${INVESTMENTS_QUOTES_BRAPI_BASE_URL:https://brapi.dev}
      max-tickers: ${INVESTMENTS_QUOTES_MAX_TICKERS:30}
```

`compose.yaml` repassa `INVESTMENTS_QUOTES_BRAPI_TOKEN` com padrão vazio. Os
testes ficam sem token, então nenhuma rodada real acontece nos testes de
integração.

### D9. Fronteiras e canais

- Tudo fica dentro de `investments`, e as dependências continuam sendo
  `shared` e `identity`. O `ModularityTest` existente cobre isso.
- **API**: a única afetada. **Web e Android**: #80. **MCP e desktop**: fora do
  dia 1 (ADR-0034).

## Risks / Trade-offs

- [Termos de uso da brapi não verificados] → O ADR-0035 exige conferir antes
  de configurar o token em produção. Sem token, nada é chamado.
- [Formato da resposta pode mudar ou a v1 ser descontinuada] → O adaptador
  isola o formato, o `bad_payload` mantém a última cotação, e um smoke test
  manual com PETR4 sem token confirma o formato no apply. A v2
  (`/api/v2/stocks/quote`) fica como troca contida no adaptador.
- [Mais de cerca de 30 tickers distintos estouram a cota no fim do ciclo] →
  Aviso no log. Quando a cota acaba, o 429 para a rodada e as cotações ficam
  velhas, mas válidas e com `quotedAt` visível.
- [Duas instâncias da API dobrariam o consumo e violariam a simultaneidade] →
  Hoje há uma instância. Escalar exige ShedLock ou equivalente, registrado no
  ADR-0035.
- [Feriado gasta 16 requisições por ticker sem dado novo] → Já está dentro da
  folga. O upsert com `quoted_at <=` só reescreve o mesmo valor.
- [Cotação muito alta pode estourar o limite de `Money` em `currentValue`] →
  Exigiria um valor com 17 dígitos inteiros. `StockLotView.currentValue()`
  devolve `null` nesse caso, para a listagem não responder 400.
- [Delay de 30 minutos do plano gratuito] → O cliente sempre mostra
  "cotado em `quotedAt`". Para uma carteira pessoal, isso é aceito pela
  intenção.
- [Arredondar na fronteira abre um precedente] → A exceção vale só para dado
  externo de referência e fica escrita no ADR-0035, com o motivo.

## Migration Plan

A `V23` só cria uma tabela nova. O deploy segue o fluxo normal. Sem
`INVESTMENTS_QUOTES_BRAPI_TOKEN`, o comportamento é igual ao da #76, com os
campos novos em `null`. Para ligar, configure o token depois de conferir os
termos. Para desfazer, basta remover o token (desliga as rodadas) ou reverter o
código. A tabela pode ficar.

## Open Questions

- Migrar para o endpoint v2 da brapi, que é o recomendado para integrações
  novas mas cujo formato não foi verificado no spike. É uma troca contida no
  adaptador e não muda specs nem tasks.
