# ADR-0035: Provedor e cadência da cotação de ações da B3

## Status

Aceito

## Data

2026-11-26

## Contexto

O [ADR-0034](0034-modulo-de-investimentos.md) decidiu que o valor atual de uma
posição em ações é `quantidade × última cotação válida`, com a cotação vinda
de uma porta `QuoteProvider`, persistida no PostgreSQL e atualizada por
`@Scheduled` em horário de pregão. Ele deixou para este ADR, na issue #77, a
escolha do provedor, a frequência, o limite de requisições e o cuidado com a
credencial.

O spike, feito em 2026-11-26 com base na documentação pública, levantou:

| | brapi.dev (gratuito) | HG Brasil Finance (gratuito) |
|---|---|---|
| Cota | 15.000 requisições por ciclo mensal | 400 requisições por dia |
| Tickers por requisição | 1 | vários (máximo não documentado) |
| Requisições simultâneas | 1 (excesso responde 429) | não documentado |
| Atualização dos dados | a cada 30 minutos | de 15 a 45 minutos no pregão |
| Credencial | `Authorization: Bearer` ou `?token=` | só `?key=` na URL |
| Preço e instante | `regularMarketPrice` (número), `regularMarketTime` (ISO 8601) | `quote.value` (número), `quote.updated_at` (ISO 8601) |

Os dois provedores devolvem o preço como número JSON. O
[ADR-0006](0006-valores-monetarios-decimais-e-renda-mensal.md) e as regras do
projeto exigem dinheiro em `BigDecimal` com duas casas, sem arredondar nas
fronteiras.

## Decisão

### Provedor

- Usamos a **brapi.dev no plano gratuito**, pelo endpoint
  `GET /api/quote/{ticker}`.
- O token vai **só no header `Authorization: Bearer`** e vem da variável de
  ambiente `INVESTMENTS_QUOTES_BRAPI_TOKEN`. Ele nunca vai na URL, porque URLs
  aparecem em logs de acesso e em mensagens de exceção
  ([ADR-0003](0003-privacidade-desde-a-concepcao.md)).
- Sem token configurado, a atualização fica desligada, e a API continua
  respondendo com as cotações já gravadas ou nulas.
- Os **termos de uso** da brapi não puderam ser lidos no spike (a página
  respondeu 404). Antes de configurar o token em produção, o titular confere
  os termos no painel do provedor.

### Cadência e orçamento de requisições

- **Rodada do pregão**: a cada 30 minutos, de segunda a sexta, das 10h00 às
  17h30 em `America/Sao_Paulo` (16 rodadas por dia). Ela consulta cada ticker
  distinto presente em algum lote, um por vez e nunca em paralelo.
- **Rodada de tickers sem cotação**: de hora em hora, aos 15 minutos, todos os
  dias. Consulta só os tickers que ainda não têm cotação, para que um lote
  cadastrado fora do pregão receba o último fechamento.
- **Orçamento**: 16 rodadas × cerca de 22 pregões = 352 requisições por ticker
  por mês. A cota cobre cerca de 42 tickers. Com folga para a rodada de
  faltantes e para os feriados, o limite operacional é de **30 tickers
  distintos** (configurável). Acima disso, a rodada registra um aviso no log e
  continua.
- Feriados não são detectados. Neles a rodada regrava a última cotação.
- Os crons são configuráveis, mas o padrão é este.

### Falhas

- Uma falha nunca altera lotes nem o custo de aquisição. A última cotação
  válida continua valendo, e os clientes mostram "cotado em `quotedAt`".
- O `quotedAt` é o instante de mercado informado pelo provedor, e não o
  instante da consulta. A gravação nunca troca uma cotação por outra mais
  antiga.
- O tratamento de cada falha:
  - **429** (cota ou simultaneidade): a rodada para imediatamente;
  - **401 e 403** (credencial ou plano): a rodada para, sem repetir;
  - **404, `results` vazio ou preço ausente, zero ou negativo**: o ticker fica
    sem cotação nova e a rodada segue;
  - **timeout, erro de rede, 5xx, 400 ou corpo inválido**: a rodada segue, mas
    para após 3 falhas seguidas, para não prender o scheduler com o provedor
    fora do ar.
- Os logs registram só contagens e códigos de parada. Nunca registram ticker
  com preço, a resposta bruta ou o token.

### Exceção de arredondamento na fronteira

- O preço do provedor é lido como `BigDecimal` diretamente do texto JSON, sem
  passar por `double`. Depois é arredondado para **2 casas com `HALF_EVEN`**,
  somente no adaptador de `investments/infrastructure`.
- Essa é uma exceção deliberada à regra de não arredondar nas fronteiras. A
  regra existe para valores informados pelo titular, enquanto a cotação é um
  dado externo de referência. Como o tick das ações na B3 é R$ 0,01, o
  arredondamento só remove ruído de ponto flutuante do provedor (por exemplo,
  `38.470001`). O modo `HALF_EVEN` é o mesmo do valor estimado do CDB no
  ADR-0034.
- Do domínio para dentro, a cotação é `Money` com duas casas exatas, como
  qualquer outro valor.

### Armazenamento

- A tabela `stock_quote` (`ticker`, `price NUMERIC(19,2)`, `quoted_at`,
  `fetched_at`) não tem `owner_id`, porque o preço de um ticker é o mesmo para
  todos os titulares. Ela fica fora da exportação do titular.
- No início de cada rodada, as cotações de tickers que nenhum lote usa mais
  são apagadas. Isso cobre a exclusão do último lote e a exclusão da conta.

## Alternativas consideradas

### HG Brasil Finance

Rejeitada. Aceita vários tickers por requisição, e a cota de 400 por dia seria
suficiente, mas a chave só pode ir na query string, onde vaza em logs de
acesso e em mensagens de erro. Ela continua como plano B caso a carteira
passe muito de 30 tickers, mas isso exigiria revisar este ADR.

### Alpha Vantage

Rejeitada. O limite gratuito é baixo demais para cotar uma carteira várias
vezes por dia.

### Yahoo Finance com sufixo `.SA`

Rejeitada. Não há API oficial, e o uso de endpoints não documentados pode
quebrar ou violar termos sem aviso.

### B3 for Developers

Rejeitada. É voltada a clientes institucionais e exige contrato comercial.

### Recusar preços com mais de duas casas

Rejeitada por decisão do titular. Recusar transformaria ruído de ponto
flutuante do provedor em falha de cotação. A exceção de arredondamento fica
restrita e documentada aqui.

### Buscar a cotação logo após o cadastro do lote

Rejeitada. Disputaria a única requisição simultânea com as rodadas agendadas.
A rodada horária de faltantes cobre esse caso com até uma hora de atraso.

## Consequências

- O módulo `investments` ganha a porta `QuoteProvider`, o adaptador
  `BrapiQuoteProvider`, a tabela `stock_quote` (V23) e as duas rodadas
  agendadas.
- A listagem de lotes passa a trazer `currentQuote` e `currentValue`, nulos
  quando ainda não há cotação. A listagem nunca chama o provedor.
- As rodadas assumem **uma única instância** da API. Escalar horizontalmente
  exige um lock distribuído (por exemplo, ShedLock), sob pena de dobrar o
  consumo e violar o limite de simultaneidade.
- O plano gratuito tem atraso de cerca de 30 minutos. Isso é aceito para uma
  carteira pessoal. Trocar de plano ou de provedor afeta só o adaptador e este
  ADR.
- O endpoint v2 da brapi (`/api/v2/stocks/quote`), recomendado pelo provedor
  para integrações novas, pode substituir o v1 dentro do adaptador sem mudar o
  contrato da API.
