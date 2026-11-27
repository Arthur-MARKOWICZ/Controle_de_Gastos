# Spec Delta

## ADDED Requirements

### Requirement: Overview surfaces LIMIT spending pace
The overview SHALL show, for LIMIT envelopes in the selected month that have pace data from the API, the projected overrun day when present and otherwise the daily remaining allowance. Values MUST come from the spending-pace API as two-decimal strings; the client MUST NOT recompute pace locally. Non-LIMIT envelopes MUST NOT show pace metrics.

#### Scenario: LIMIT projects overrun
- **GIVEN** the spending-pace API returns envelope "Combustível" with projected overrun day "2026-11-20" for month "2026-11"
- **WHEN** the owner opens Visão geral for "2026-11"
- **THEN** the overview names "Combustível" with projected overrun day "2026-11-20"

#### Scenario: LIMIT within pace shows daily allowance
- **GIVEN** the spending-pace API returns envelope "Lazer" with projected overrun day null and daily remaining allowance "9.55"
- **WHEN** the owner opens Visão geral for that month
- **THEN** the overview shows daily remaining allowance "9.55" for "Lazer"
- **AND** it does not invent an overrun day

#### Scenario: Participant only sees shared pace
- **GIVEN** a participant who can view LIMIT "Mercado" and cannot view LIMIT "Pessoal"
- **AND** the spending-pace API returns only "Mercado"
- **WHEN** they open Visão geral
- **THEN** pace for "Mercado" is shown
- **AND** pace for "Pessoal" is not shown
