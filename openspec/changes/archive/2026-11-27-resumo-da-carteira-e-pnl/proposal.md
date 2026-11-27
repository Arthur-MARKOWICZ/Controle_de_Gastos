# Proposal

## Why

As issues #76–#78 já entregam lotes de ativos da B3 (com cotação) e CDB
prefixado (com saldo estimado), mas o titular ainda não tem a visão que o
intent e o ADR-0034 pedem: total da carteira, agregação por ativo, preço médio
e P&L simples desde a compra. Esta change corresponde à issue #79 e fecha o
último pedaço de API do dia 1 de investimentos, antes da UI (#80).

## What Changes

- Novo recurso de leitura **resumo da carteira** no módulo `investments`:
  `GET /api/v1/investments/summary`, autenticado e restrito ao titular.
- O resumo agrega:
  - **ações**: lotes agrupados por ticker, com quantidade total, preço médio,
    custo de aquisição, valor atual (quando houver cotação), P&L e o detalhe
    por lote;
  - **CDBs**: cada posição com principal, valor estimado e P&L
    (`estimado − principal`);
  - **totais** de custo, valor atual e P&L.
- **Cotação ausente**: os totais consideram só posições com valor atual
  (tickers cotados + todos os CDBs). Tickers sem cotação entram no detalhe com
  `currentValue` e `pnl` nulos. A resposta traz a flag booleana `complete`
  (`false` quando algum ticker de ação do titular ainda não tem cotação;
  carteira vazia é `complete: true`).
- **Preço médio** por ticker:
  `sum(custo de aquisição) / sum(quantidade)`, arredondado a duas casas com
  `HALF_EVEN` (mesmo modo do ADR-0035 / estimado do CDB). Sem ADR novo: a
  exceção fica documentada no design desta change.
- Sem tabela nova, sem job e sem cache: leitura pura sobre `stock_lot`,
  `stock_quote` e `cdb_position`.

### Fora do escopo

- Rentabilidade anualizada, benchmark, série histórica e gráficos.
- Clientes web e Android (#80). MCP e desktop fora do dia 1 (ADR-0034).
- Alterar cadastro/listagem/exclusão de lotes ou CDBs.
- Persistência de derivados (preço médio, totais, P&L).
- Venda, saldo de quantidade residual ou posição consolidada além da leitura.
- Fundos, CDB pós-fixado, IR/IOF, import de corretora.
- Novo ADR (o arredondamento do preço médio reutiliza o precedente já aceito).

## Capabilities

### New Capabilities

- `investments/portfolio-summary`: leitura autenticada do resumo da carteira do
  titular, com totais, agregação por ticker (preço médio e P&L), detalhe por
  posição de CDB e tratamento de cotação ausente via flag `complete`.

### Modified Capabilities

Nenhuma. As capacidades `investments/stock-lots`, `investments/stock-quotes` e
`investments/cdb-positions` continuam cobrindo cadastro/listagem; o resumo é
uma capacidade de leitura agregada própria.

## Impact

- **ADRs**: restringem a mudança ADR-0034 (resumo, P&L, só titular, isolado do
  orçamento), ADR-0035 (precedente de `HALF_EVEN` em duas casas para valor
  derivado externo/calculado), ADR-0001 (monólito modular), ADR-0003
  (privacidade / sem valores em logs) e ADR-0006 (dinheiro em `BigDecimal`,
  JSON como string). Nenhum ADR existente precisa ser alterado.
- **Backend**: novo serviço/controller de resumo no módulo `investments`,
  reutilizando repositórios e views já existentes. Sem migration.
- **API**: um endpoint novo `GET /api/v1/investments/summary`. Nenhum contrato
  existente muda.
- **Canais**: só a API.
- **Dependência de change**: pressupõe #76–#78 já presentes nesta branch
  (`stock_lot`, `stock_quote`, `cdb_position`).
