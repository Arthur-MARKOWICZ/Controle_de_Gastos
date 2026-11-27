# Proposal

## Why

O ADR-0034 abriu o módulo `investments`, mas ainda não existe nenhum código
nele. O titular precisa registrar à mão o que comprou na B3 para que a cotação
automática (#77) e o resumo da carteira (#79) tenham sobre o que trabalhar.
Esta change corresponde à issue #76 e cria o módulo com o primeiro recurso:
lotes de compra de ativos da B3.

## What Changes

- Novo módulo Spring Modulith `br.com.controlegastos.investments`
  (`displayName = "Investimentos"`), dependendo só de `shared` e `identity`,
  conforme o ADR-0034.
- Novo recurso **lote de ativo da B3**: cada compra é um registro próprio, com
  ticker, quantidade inteira, preço unitário e data da compra. O custo de
  aquisição (`quantidade × preço unitário`) é derivado e devolvido pela API.
  Duas compras do mesmo ticker geram dois lotes. O preço médio e a agregação
  por ticker ficam para a #79.
- API autenticada, restrita ao titular:
  - `POST /api/v1/investments/stocks`: cadastra um lote;
  - `GET /api/v1/investments/stocks`: lista os lotes do titular;
  - `DELETE /api/v1/investments/stocks/{id}`: exclui o lote fisicamente.
  - Um lote de outro titular responde 404.
- O ticker é validado só pelo formato da B3 (`^[A-Z]{4}\d{1,2}$`), depois de
  ser normalizado para maiúsculas e sem o sufixo `F` do mercado fracionário.
  Isso aceita ações, FIIs, ETFs, units e BDRs, por isso o recurso se chama
  "ativos da B3".
- Nova tabela `stock_lot` (migration `V22`), com valores em `NUMERIC(19,2)`.
- Direitos do titular: a exportação ganha `stockLots`, e a exclusão da conta
  apaga os lotes do titular (ADR-0023).
- O inventário de dados (`docs/privacy/inventario-de-dados.md`) ganha a nova
  tabela.

### Fora do escopo

- Edição de lote. Para corrigir um lote, o usuário o exclui e cadastra de novo.
- Cotação, valor atual e a escolha do provedor (#77).
- CDB e a dependência `big-math` (#78).
- Resumo, preço médio, agregação por ticker e P&L (#79).
- Clientes web e Android (#80). MCP e desktop ficam fora do dia 1 (ADR-0034).
- Corretagem, emolumentos e impostos no custo de aquisição.
- Verificar se o ticker existe na B3, o que depende da #77.
- Venda, saldo de quantidade e posição consolidada.

## Capabilities

### New Capabilities

- `investments/stock-lots`: cadastro, listagem e exclusão de lotes de compra
  de ativos da B3 do titular, com as validações de ticker, quantidade, preço e
  data e o custo de aquisição derivado.

### Modified Capabilities

Nenhuma. Não há spec de privacidade em `openspec/specs/`. A inclusão na
exportação e na exclusão do titular é coberta como requisito da nova
capacidade.

## Impact

- **ADRs que restringem a mudança**: ADR-0034 (módulo, acesso só do titular,
  fronteira com o orçamento), ADR-0001 (monólito modular), ADR-0003
  (privacidade), ADR-0006 (dinheiro em `BigDecimal`, duas casas, JSON como
  string) e ADR-0023 (exclusão do titular). Nenhum precisa ser atualizado.
- **Backend**: novo pacote `investments` (domain, application, infrastructure,
  web), migration `V22__create_stock_lot.sql` e alterações em
  `privacy/application/TitularRightsService`.
- **API**: três endpoints novos em `/api/v1/investments/stocks`. Nenhum
  contrato existente muda.
- **Dependências**: nenhuma nova.
- **Canais**: só a API. Web, Android, MCP e desktop não mudam.
- **Documentação**: inventário de dados.
