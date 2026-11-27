# Proposal

## Why

Os lotes de ativos da B3 (#76) só mostram o custo de aquisição. O titular quer
saber quanto a carteira vale hoje, e o ADR-0034 decidiu que esse valor vem de
uma cotação automática, persistida como última cotação válida. A escolha do
provedor, da cadência e do limite de requisições ficou para um ADR filho nesta
issue (#77). O spike já foi feito na exploração.

## What Changes

- **ADR-0035 (filho do ADR-0034)**: provedor de cotação brapi.dev no plano
  gratuito, com token em `Authorization: Bearer` lido da variável de ambiente
  `INVESTMENTS_QUOTES_BRAPI_TOKEN`. O ADR registra a cadência (a cada 30 minutos
  em dias úteis, das 10h às 18h, em America/Sao_Paulo), o orçamento de
  requisições (15.000 por mês, 1 ticker por requisição, 1 requisição por vez),
  o tratamento de falhas e uma **exceção explícita** à regra de não arredondar
  na fronteira: o preço vindo do provedor é convertido em `BigDecimal` a
  partir do texto e arredondado para 2 casas com `HALF_EVEN`, somente no
  adaptador. A HG Brasil fica registrada como alternativa, rejeitada porque a
  chave só pode ir na URL.
- **Porta `QuoteProvider`** no módulo `investments` e o adaptador HTTP
  `BrapiQuoteProvider` em `investments/infrastructure`, com `RestClient` e
  timeouts curtos, no padrão de `identity`.
- **Última cotação por ticker** em uma nova tabela `stock_quote` (migration
  `V23`): ticker, preço `NUMERIC(19,2)`, `quoted_at` (a hora de mercado
  informada pelo provedor) e `fetched_at`. É um dado de referência global, não
  pessoal, e não é cache (ADR-0034).
- **Atualização agendada**:
  - a rodada do pregão atualiza todos os tickers distintos que existem em
    `stock_lot`;
  - uma rodada de hora em hora, todos os dias, busca só os tickers que ainda
    não têm cotação. Assim um lote cadastrado no fim de semana recebe o
    fechamento sem esperar o próximo pregão;
  - as cotações de tickers que nenhum lote usa mais são apagadas.
- **Falha da fonte**: a última cotação válida continua valendo. O 429 encerra
  a rodada, o ticker desconhecido fica sem cotação e os erros de autenticação
  ou de plano não são repetidos. Nenhuma falha altera o lote nem o custo de
  aquisição. O log registra só contagens e códigos, sem ticker, preço ou
  resposta bruta. Sem token configurado, a atualização fica desligada e o
  resto da API funciona.
- **API**: cada item de `GET /api/v1/investments/stocks` ganha
  `currentQuote` (`{ price, quotedAt }`) e `currentValue`
  (`quantidade × cotação`). Os dois vêm `null` quando o ticker ainda não tem
  cotação. A mudança é aditiva e não quebra o contrato. O `POST` responde com
  os dois campos sem buscar cotação na hora.
- O inventário de dados ganha a tabela `stock_quote`.

### Fora do escopo

- Validar no cadastro se o ticker existe. O cadastro não chama o provedor, e
  um ticker inexistente simplesmente fica sem cotação.
- Série histórica de cotações, variação do dia, gráficos.
- Resumo da carteira, total, P&L e preço médio (#79).
- CDB e `big-math` (#78). Fundos e pós-fixado.
- Import automático de compras da corretora.
- Clientes web e Android (#80). MCP e desktop seguem fora do dia 1.
- Calendário de feriados da B3. Em feriado a rodada roda e apenas grava de
  novo a última cotação.
- Plano pago do provedor ou troca automática para um segundo provedor.

## Capabilities

### New Capabilities

- `investments/stock-quotes`: obtenção e guarda da última cotação válida dos
  tickers cadastrados, cadência e tratamento de falha da fonte, e exposição da
  cotação e do valor atual de cada lote do titular.

### Modified Capabilities

Nenhuma em `openspec/specs/`. A capacidade `investments/stock-lots` ainda está
só na change `cadastrar-lotes-de-acoes`, não arquivada. Os campos novos da
listagem ficam como requisito de `investments/stock-quotes`, sem alterar os
requisitos de `stock-lots`.

## Impact

- **ADRs**: novo ADR-0035. Restringem a mudança: ADR-0034 (porta
  `QuoteProvider`, última cotação persistida, sem cache, logs), ADR-0001
  (monólito modular), ADR-0003 (privacidade), ADR-0006 (dinheiro em
  `BigDecimal` com duas casas, com a exceção registrada no ADR-0035). Nenhum
  ADR existente precisa ser alterado.
- **Backend**: `investments` ganha domínio, serviço de atualização, porta,
  adaptador HTTP, repositório, configuração e o agendamento. Migration
  `V23__create_stock_quote.sql`. O controller de lotes passa a ler a cotação.
- **Configuração**: `application.yml` e `compose.yaml` ganham
  `INVESTMENTS_QUOTES_BRAPI_TOKEN` (vazio por padrão, o que desliga a
  atualização).
- **Privacidade**: `stock_quote` não tem `owner_id` e fica fora da exportação
  e da exclusão do titular. A limpeza de órfãos evita manter o ticker de quem
  saiu. A linha no inventário registra isso.
- **Dependências**: nenhuma nova biblioteca.
- **Canais**: só a API.
- **Dependência de change**: pressupõe a `cadastrar-lotes-de-acoes` (#76), já
  implementada nesta branch.
