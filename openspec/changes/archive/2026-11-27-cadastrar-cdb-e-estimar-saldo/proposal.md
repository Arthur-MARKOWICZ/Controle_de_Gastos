# Proposal

## Why

O ADR-0034 abriu a carteira de investimentos e as issues #76/#77 já cobrem
ativos da B3. Falta o segundo pilar do dia 1 do intent: CDB prefixado com
saldo estimado pelo rendimento contratado, sem marcação a mercado e sem
misturar com verbas. Esta change corresponde à issue #78.

## What Changes

- Novo recurso **posição de CDB prefixado** no módulo `investments`: cada
  cadastro guarda principal (BRL), taxa anual prefixada em decimal
  (`0.1250` = 12,50% a.a.), data de início e data de vencimento (obrigatória).
- **Estimativa de saldo** recalculada na leitura pela fórmula do ADR-0034
  (base 365 dias corridos, `big-math`, arredondamento `HALF_EVEN` só no
  resultado). No vencimento o valor deixa de crescer. O estimado é bruto
  (sem IR/IOF) e não é persistido.
- API autenticada, restrita ao titular, espelhando o padrão de lotes:
  - `POST /api/v1/investments/cdbs`: cadastra;
  - `GET /api/v1/investments/cdbs`: lista do titular com `estimatedValue`;
  - `DELETE /api/v1/investments/cdbs/{id}`: exclusão física; recurso de outro
    titular responde 404.
- Nova tabela `cdb_position` (migration `V24`), inventário LGPD, exportação
  (`cdbPositions`) e exclusão na encerramento da conta.
- Dependência `ch.obermuhlner:big-math` no backend (consequência do
  ADR-0034).

### Fora do escopo

- CDB pós-fixado (% do CDI) e qualquer série do CDI.
- Fundos, marcação a mercado de título, IR/IOF.
- Edição de posição (corrige excluindo e cadastrando de novo).
- Resumo da carteira, agregação e P&L (#79).
- Clientes web e Android (#80). MCP e desktop fora do dia 1 (ADR-0034).
- Sync/import de corretora.
- Base de 252 dias úteis.
- Campos derivados na API além de `estimatedValue` (`elapsedDays`, `asOf`).

## Capabilities

### New Capabilities

- `investments/cdb-positions`: cadastro, listagem e exclusão de posições de
  CDB prefixado do titular, com estimativa de saldo pelo rendimento e as
  validações de principal, taxa, datas e autorização.

### Modified Capabilities

Nenhuma em `openspec/specs/`. Não há spec arquivada de privacidade; a
inclusão na exportação e na exclusão do titular fica como requisito da nova
capacidade, no mesmo padrão de `investments/stock-lots`.

## Impact

- **ADRs**: restringem a mudança ADR-0034 (fórmula, `big-math`, só titular,
  isolado do orçamento), ADR-0001 (monólito modular), ADR-0003 (privacidade),
  ADR-0006 (dinheiro em `BigDecimal` / duas casas nas fronteiras monetárias;
  a taxa é decimal de taxa, não `Money`) e ADR-0023 (exclusão do titular).
  Nenhum ADR existente precisa ser alterado.
- **Backend**: domínio, service, repositório e controller de CDB no módulo
  `investments`; migration `V24__create_cdb_position.sql`; alterações em
  `privacy/application/TitularRightsService`; `build.gradle.kts` ganha
  `big-math`.
- **API**: três endpoints novos em `/api/v1/investments/cdbs`. Nenhum
  contrato existente muda.
- **Canais**: só a API.
- **Documentação**: inventário de dados.
- **Dependência de change**: pressupõe o módulo `investments` (#76) já
  presente nesta branch.
