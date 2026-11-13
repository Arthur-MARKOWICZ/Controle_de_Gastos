# Spec Delta

## Purpose

Lista as verbas do mês visíveis ao usuário autenticado, com saldo em BRL e
estado negativo compreensível sem depender só da cor.

## ADDED Requirements

### Requirement: Visible envelopes for the month
O cliente SHALL listar somente as verbas que a API devolve ao usuário
autenticado no mês selecionado. Cada item MUST mostrar nome, natureza e
disponível como string decimal com duas casas.

#### Scenario: Owner sees own envelopes
- **GIVEN** o titular possui a verba "Moradia" com disponível "1200.00"
- **WHEN** ele abre a lista de verbas do mês
- **THEN** a lista inclui "Moradia" com disponível "1200.00"

#### Scenario: Participant sees shared envelope
- **GIVEN** um participante de "Mercado" com disponível "80.50"
- **WHEN** ele abre a lista de verbas do mês
- **THEN** a lista inclui "Mercado" com disponível "80.50"

#### Scenario: Third party does not see foreign envelopes
- **GIVEN** um usuário autenticado que não é dono nem participante de "Viagem"
- **WHEN** ele abre a lista de verbas do mês
- **THEN** a lista não inclui "Viagem"

### Requirement: Negative state is not color-only
Uma verba com `isNegative` verdadeiro MUST exibir o estado negativo com texto (e
ícone, se houver), além da cor. O registro de gasto MUST permanecer oferecido
quando o usuário puder gastar na verba.

#### Scenario: Negative envelope remains spendable
- **GIVEN** a verba "Lazer" com disponível "-15.00" e `isNegative` verdadeiro, e
  o usuário pode gastar nela
- **WHEN** ele vê a lista de verbas
- **THEN** o item indica saldo negativo em texto
- **AND** a ação de registrar gasto permanece disponível

### Requirement: Empty month
Sem verbas visíveis no mês, o cliente SHALL mostrar estado vazio explicando que
não há verbas, sem fingir dados.

#### Scenario: No envelopes in the month
- **GIVEN** a API devolve lista vazia para o mês
- **WHEN** o usuário abre Verbas
- **THEN** o cliente mostra estado vazio
- **AND** não exibe valores "0.00" como se fossem verbas
