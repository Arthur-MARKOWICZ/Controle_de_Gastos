# carteira Specification

## Purpose

Oferece no Android autenticado a área Carteira, alcançável por Mais, com o
mesmo hub de resumo, cadastro e exclusão isolado do orçamento de verbas.

## Requirements

### Requirement: Authenticated user reaches Carteira from Mais
O Android autenticado SHALL expor Carteira a partir de Mais, sem adicionar
item à barra inferior. A tela MUST carregar o resumo via
`GET /api/v1/investments/summary` e MUST NOT exigir o fluxo de verbas para
cadastrar ou consultar posições.

#### Scenario: Open Carteira from Mais
- **GIVEN** um usuário autenticado na tela Mais
- **WHEN** ele abre Carteira
- **THEN** o aplicativo mostra o hub de carteira
- **AND** a barra inferior continua com os mesmos cinco destinos de primeiro nível

#### Scenario: Bottom bar labels still fit
- **GIVEN** um dispositivo com largura 360 dp
- **WHEN** a barra inferior é exibida com Carteira disponível em Mais
- **THEN** os rótulos da barra inferior cabem sem ellipsis
- **AND** Carteira não aparece como sexto item da barra

#### Scenario: Third party cannot open Carteira
- **GIVEN** um visitante sem sessão
- **WHEN** o aplicativo está na tela de entrar
- **THEN** Carteira não está acessível
- **AND** Mais autenticado não é exibido

### Requirement: Hub shows portfolio totals and positions
O hub SHALL exibir totais de custo, valor atual e P&L, ações por ticker e
CDBs do resumo. Valores monetários MUST usar strings decimais com duas casas.

#### Scenario: Portfolio with positions
- **GIVEN** o resumo com `totalCurrent` "1650.00", uma ação "PETR4" e um CDB
- **WHEN** o usuário autenticado abre Carteira
- **THEN** a tela mostra o total "1650.00"
- **AND** lista a ação e o CDB

#### Scenario: Partial summary is labelled
- **GIVEN** o resumo com `complete` false
- **WHEN** o usuário autenticado abre Carteira
- **THEN** a tela indica que o resumo está parcial

#### Scenario: CDB values are labelled as estimated
- **GIVEN** o resumo com um CDB de `estimatedValue` "11250.00"
- **WHEN** o usuário autenticado abre Carteira
- **THEN** o valor aparece rotulado como estimado
- **AND** a UI não apresenta o valor como líquido de IR ou IOF

### Requirement: Empty portfolio offers registration
Carteira vazia SHALL mostrar empty state com ações para cadastrar ação e
cadastrar CDB.

#### Scenario: Empty portfolio
- **GIVEN** o resumo com listas vazias
- **WHEN** o usuário autenticado abre Carteira
- **THEN** a tela explica que ainda não há posições
- **AND** oferece cadastrar ação e cadastrar CDB

### Requirement: User registers positions from the hub
O hub SHALL permitir cadastrar ação e CDB pelos endpoints existentes e, em
sucesso, atualizar o resumo sem abrir fluxos de verba ou gasto.

#### Scenario: Register stock succeeds
- **GIVEN** o usuário autenticado no hub
- **WHEN** ele cadastra uma ação válida
- **THEN** o hub reflete a posição após o resumo
- **AND** nenhum lançamento de verba é iniciado na UI

#### Scenario: Register CDB succeeds
- **GIVEN** o usuário autenticado no hub
- **WHEN** ele cadastra um CDB válido
- **THEN** o hub reflete o CDB após o resumo
- **AND** o valor estimado permanece rotulado como estimado

#### Scenario: Envelope participant still manages own portfolio
- **GIVEN** um usuário que só participa de verbas de outra pessoa e tem carteira vazia
- **WHEN** ele abre Carteira e cadastra uma ação
- **THEN** o cadastro vale para a carteira dele
- **AND** a UI não oferece a carteira do dono das verbas compartilhadas

### Requirement: User deletes a position with confirmation
Excluir lote ou CDB MUST pedir confirmação com ênfase de risco e, se
confirmado, chamar o DELETE correspondente e atualizar o hub.

#### Scenario: Delete CDB with confirmation
- **GIVEN** um CDB visível no hub
- **WHEN** o usuário confirma a exclusão
- **THEN** o hub deixa de listá-lo após o resumo
- **AND** o botão confirmar usa ênfase de risco distinta da ação primária

#### Scenario: Cancel delete
- **GIVEN** a confirmação de exclusão aberta
- **WHEN** o usuário cancela
- **THEN** a posição permanece
- **AND** nenhum DELETE é enviado
