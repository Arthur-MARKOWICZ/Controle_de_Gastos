# Spec Delta

## ADDED Requirements

### Requirement: LIMIT card shows spending pace
A LIMIT envelope card SHALL display the projected overrun day when the spending-pace API provides one, and otherwise the daily remaining allowance as a two-decimal string. The card MUST keep the existing spent-progress and remaining-balance text. GOAL, FIXED, SAVINGS_TARGET and ANNUAL_EXPENSE cards MUST NOT show spending-pace metrics.

#### Scenario: LIMIT card with projected overrun
- **GIVEN** a LIMIT envelope "Combustível" with spending-pace projected overrun day "2026-11-20"
- **WHEN** the card renders
- **THEN** the card text includes the projected overrun day "2026-11-20"
- **AND** the primary action remains register expense

#### Scenario: LIMIT card within pace
- **GIVEN** a LIMIT envelope with spending-pace daily remaining allowance "9.55" and no overrun day
- **WHEN** the card renders
- **THEN** the card text includes daily remaining allowance "9.55"

#### Scenario: Non-LIMIT card
- **GIVEN** a GOAL envelope card
- **WHEN** the card renders
- **THEN** it does not show projected overrun day or daily remaining allowance from spending pace
