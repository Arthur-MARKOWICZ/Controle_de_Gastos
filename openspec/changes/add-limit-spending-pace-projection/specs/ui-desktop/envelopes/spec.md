# Spec Delta

## ADDED Requirements

### Requirement: Desktop LIMIT envelope shows spending pace
Cada item LIMIT na lista de verbas do desktop SHALL exibir o dia previsto de estouro quando a API de ritmo o fornecer, e caso contrário a allowance diária restante como string decimal com duas casas. O disponível e o estado negativo existentes MUST permanecer. Verbas que não sejam LIMIT MUST NOT mostrar ritmo.

#### Scenario: Item LIMIT com estouro projetado
- **GIVEN** a verba LIMIT "Combustível" com dia previsto "2026-11-20" na API de ritmo
- **WHEN** a lista de verbas do mês é exibida
- **THEN** o item inclui a data "2026-11-20"
- **AND** o registro de gasto permanece oferecido quando o usuário puder gastar

#### Scenario: Item LIMIT dentro do ritmo
- **GIVEN** a verba LIMIT com allowance diária "9.55" e sem dia de estouro
- **WHEN** a lista é exibida
- **THEN** o item inclui "9.55" como quanto ainda dá para gastar por dia

#### Scenario: Item GOAL
- **GIVEN** uma verba GOAL na lista
- **WHEN** a lista é exibida
- **THEN** o item não mostra dia de estouro nem allowance diária de ritmo
