# Spec Delta

## ADDED Requirements

### Requirement: LIMIT card shows spending pace on Android
O card de verba LIMIT no Android SHALL exibir o dia previsto de estouro quando a API de ritmo o informar, e caso contrário a allowance diária restante como string decimal com duas casas. O sinal MUST vir da API de spending-pace; o cliente MUST NOT recalcular. Cards que não sejam LIMIT MUST NOT mostrar esses indicadores.

#### Scenario: Card LIMIT com estouro projetado
- **GIVEN** a verba LIMIT "Combustível" com dia previsto de estouro "2026-11-20" na API de ritmo
- **WHEN** o card aparece na lista ou na visão geral do mês
- **THEN** o texto do card inclui a data "2026-11-20"
- **AND** a ação primária "Registrar" permanece disponível

#### Scenario: Card LIMIT dentro do ritmo
- **GIVEN** a verba LIMIT com allowance diária "9.55" e sem dia de estouro
- **WHEN** o card é exibido
- **THEN** o texto inclui "9.55" como quanto ainda dá para gastar por dia

#### Scenario: Participante vê só verbas compartilhadas
- **GIVEN** um participante com acesso a LIMIT "Mercado" e sem acesso a LIMIT "Pessoal"
- **WHEN** a lista de verbas do mês é exibida
- **THEN** o ritmo de "Mercado" pode aparecer
- **AND** "Pessoal" não aparece
