# Spec Delta

## Purpose

Mostra o mapa do mês do titular autenticado: renda, alocado, não alocado e
alertas de saldo negativo, sem configurar renda neste corte.

## ADDED Requirements

### Requirement: Monthly summary for the authenticated user
A visão geral SHALL exibir, para o mês selecionado em America/Sao_Paulo, a renda
e os totais alocado e não alocado devolvidos pela API, como strings decimais
com duas casas e moeda BRL.

#### Scenario: Owner views the current month
- **GIVEN** um titular com renda "8000.00" e não alocado "1550.00" no mês
- **WHEN** ele abre a visão geral daquele mês
- **THEN** o cliente mostra renda "8000.00" e não alocado "1550.00"

#### Scenario: Month without configured income
- **GIVEN** o titular autenticado sem renda no mês selecionado
- **WHEN** ele abre a visão geral
- **THEN** o cliente indica que a renda não está configurada
- **AND** não inventa um valor "0.00" como se fosse renda lançada

### Requirement: Negative balance alerts
A visão geral SHALL listar alertas persistentes de saldo negativo visíveis ao
titular. Cada alerta MUST identificar a verba por nome, não só por cor. O
usuário SHALL poder dispensar um alerta já tratado, e a recusa da API MUST
manter o alerta visível em vez de sumir com ele.

#### Scenario: Owner sees a negative envelope alert
- **GIVEN** um alerta ativo de saldo negativo na verba "Combustível"
- **WHEN** o titular abre a visão geral
- **THEN** o alerta cita "Combustível" em texto
- **AND** o estado negativo não depende só da cor

#### Scenario: Owner dismisses a handled alert
- **GIVEN** um alerta ativo de saldo negativo na verba "Combustível"
- **WHEN** o titular dispensa esse alerta
- **THEN** o alerta sai da lista da visão geral
- **AND** um arranque seguinte não volta a exibi-lo

#### Scenario: Dismissal refused by the API
- **GIVEN** um alerta ativo na visão geral
- **WHEN** a API recusa a dispensa
- **THEN** o alerta permanece visível
- **AND** o cliente mostra erro sem afirmar que foi dispensado

#### Scenario: Participant without owner alerts
- **GIVEN** um participante que não é titular da conta
- **WHEN** ele abre a visão geral
- **THEN** o cliente não mostra alertas de verbas de outro titular

### Requirement: Month selector
O usuário SHALL poder escolher o mês de referência. A visão geral MUST recarregar
o resumo para o mês escolhido.

#### Scenario: User changes month
- **GIVEN** a visão geral em "2026-11"
- **WHEN** o usuário seleciona "2026-10"
- **THEN** o resumo exibido passa a ser o de "2026-10"
