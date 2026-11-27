# carteira Specification

## Purpose

Oferece na web autenticada a área Carteira: ver totais e P&L, cadastrar ações
e CDBs e excluir posições, sem misturar com o fluxo de verbas.

## Requirements

### Requirement: Authenticated user opens the portfolio hub
A web autenticada SHALL expor a área Carteira em `/carteira`, com o chrome
compartilhado do app-shell. A página MUST carregar o resumo via
`GET /api/v1/investments/summary` e MUST NOT exigir navegação por verbas,
histórico de gastos ou `wealthAllocation` para cumprir o fluxo.

#### Scenario: Owner opens Carteira
- **GIVEN** um usuário autenticado
- **WHEN** ele abre `/carteira`
- **THEN** a página mostra o hub de carteira com título distinto das telas de orçamento
- **AND** a navegação principal marca Carteira como página atual

#### Scenario: Third party cannot open Carteira
- **GIVEN** um visitante sem sessão
- **WHEN** ele tenta abrir `/carteira`
- **THEN** a aplicação não mostra o hub autenticado
- **AND** o fluxo de autenticação permanece o único caminho

### Requirement: Hub shows portfolio totals and positions
O hub SHALL exibir totais de custo, valor atual e P&L, a lista de ações
agregadas por ticker e a lista de CDBs devolvidos pelo resumo. Valores
monetários MUST aparecer como strings decimais com duas casas.

#### Scenario: Portfolio with stocks and CDBs
- **GIVEN** o resumo com `totalCost` "1500.00", `totalCurrent` "1650.00", `totalPnl` "150.00", uma ação "PETR4" e um CDB
- **WHEN** o usuário autenticado abre Carteira
- **THEN** a página mostra esses totais
- **AND** lista a ação "PETR4" e o CDB

#### Scenario: Partial summary is labelled
- **GIVEN** o resumo com `complete` false
- **WHEN** o usuário autenticado abre Carteira
- **THEN** a página indica que o resumo está parcial
- **AND** posições de ação sem cotação não inventam valor atual na UI

#### Scenario: CDB values are labelled as estimated
- **GIVEN** o resumo com um CDB cujo `estimatedValue` é "11250.00"
- **WHEN** o usuário autenticado abre Carteira
- **THEN** o valor do CDB aparece rotulado como estimado
- **AND** a UI não apresenta esse valor como líquido de IR ou IOF

### Requirement: Empty portfolio offers registration
Quando o resumo não tem ações nem CDBs, o hub SHALL mostrar um empty state
com ações para cadastrar ação e cadastrar CDB.

#### Scenario: Empty portfolio
- **GIVEN** o resumo com `stocks` [] e `cdbs` []
- **WHEN** o usuário autenticado abre Carteira
- **THEN** a página explica que ainda não há posições
- **AND** oferece controles para cadastrar ação e cadastrar CDB

### Requirement: User registers a stock lot from the hub
O hub SHALL permitir cadastrar um lote de ação (ticker, quantidade, preço
unitário, data de compra) via `POST /api/v1/investments/stocks` e, em
sucesso, atualizar o resumo sem criar lançamento em verba.

#### Scenario: Register stock succeeds
- **GIVEN** o usuário autenticado no hub
- **WHEN** ele cadastra ticker "PETR4", quantidade 100, unitPrice "10.00" e purchaseDate "2026-01-15"
- **THEN** a UI confirma o cadastro e o hub reflete a nova posição após o resumo
- **AND** nenhum fluxo de envelope ou histórico de gasto é aberto

#### Scenario: Register stock validation error
- **GIVEN** o usuário autenticado no hub
- **WHEN** a API responde 400 ao cadastro de ação
- **THEN** a UI mostra o erro sem fechar o formulário de forma silenciosa
- **AND** o resumo anterior permanece visível

### Requirement: User registers a CDB from the hub
O hub SHALL permitir cadastrar um CDB (principal, taxa anual decimal,
início, vencimento) via `POST /api/v1/investments/cdbs` e, em sucesso,
atualizar o resumo sem criar lançamento em verba.

#### Scenario: Register CDB succeeds
- **GIVEN** o usuário autenticado no hub
- **WHEN** ele cadastra principal "10000.00", annualRate "0.1250", startDate e maturityDate válidos
- **THEN** a UI confirma o cadastro e o hub reflete o CDB após o resumo
- **AND** o valor estimado do CDB permanece rotulado como estimado

#### Scenario: Registering does not touch envelopes
- **GIVEN** um usuário com verbas e saldos
- **WHEN** ele cadastra uma ação ou um CDB pela Carteira
- **THEN** a UI não registra gasto nem altera saldo de verba no cliente
- **AND** não navega para telas de orçamento como parte do sucesso

### Requirement: User deletes a position with confirmation
O hub SHALL permitir excluir um lote de ação ou um CDB após confirmação
destrutiva, via DELETE nos endpoints existentes, e atualizar o resumo.

#### Scenario: Delete stock lot
- **GIVEN** um lote de ação visível no hub
- **WHEN** o usuário confirma a exclusão
- **THEN** a UI chama a exclusão do lote e o hub deixa de listá-lo após o resumo
- **AND** o botão de confirmar usa ênfase de risco distinta da ação primária

#### Scenario: Cancel delete
- **GIVEN** o diálogo de exclusão aberto
- **WHEN** o usuário cancela
- **THEN** a posição permanece no hub
- **AND** nenhum DELETE é enviado
