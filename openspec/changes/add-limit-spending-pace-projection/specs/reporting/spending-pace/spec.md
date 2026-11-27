# Spec Delta

## Purpose

Expõe a projeção de ritmo do mês para verbas LIMIT visíveis: gasto projetado, dia previsto de estouro e quanto ainda dá para gastar por dia, como leitura pura sem materializar dados.

## ADDED Requirements

### Requirement: Limit spending-pace query for a calendar month
The system SHALL expose an authenticated JSON read that, for a calendar month in America/Sao_Paulo, returns one pace row per visible LIMIT envelope. Each monetary field MUST be a two-decimal string. Non-LIMIT purposes MUST NOT appear.

#### Scenario: Owner with two LIMIT envelopes
- **GIVEN** an owner with LIMIT envelopes "Combustível" and "Lazer" and one GOAL envelope in month "2026-11"
- **WHEN** they request spending pace for "2026-11"
- **THEN** the response includes rows for "Combustível" and "Lazer" only
- **AND** every monetary amount is a two-decimal string

#### Scenario: Month with 31 days mid-month
- **GIVEN** today is "2026-10-10" (day 10 of a 31-day month) in America/Sao_Paulo
- **AND** a LIMIT envelope with month-to-date expenses totaling "100.00" and monthly limit capacity "310.00"
- **WHEN** the owner requests spending pace for "2026-10"
- **THEN** projected month spend is "310.00"
- **AND** projected overrun day is absent or null
- **AND** daily remaining allowance is "9.55"

#### Scenario: February with 28 days
- **GIVEN** today is "2026-02-14" in America/Sao_Paulo
- **AND** a LIMIT envelope with month-to-date expenses "140.00" and monthly limit capacity "280.00"
- **WHEN** the owner requests spending pace for "2026-02"
- **THEN** elapsed days used in the pace formula are 14
- **AND** month length used is 28
- **AND** projected month spend is "280.00"

#### Scenario: Leap February with 29 days
- **GIVEN** today is "2024-02-15" in America/Sao_Paulo
- **AND** a LIMIT envelope with month-to-date expenses "145.00" and monthly limit capacity "290.00"
- **WHEN** the owner requests spending pace for "2024-02"
- **THEN** month length used is 29
- **AND** projected month spend is "280.33"

#### Scenario: Zero spending so far
- **GIVEN** a LIMIT envelope with month-to-date expenses "0.00" and monthly limit capacity "500.00"
- **WHEN** the owner requests spending pace for the current month
- **THEN** projected month spend is "0.00"
- **AND** projected overrun day is null
- **AND** daily remaining allowance equals the remaining capacity divided by remaining days in the month, as a two-decimal string

#### Scenario: First calendar day of the month
- **GIVEN** today is the first day of the requested month in America/Sao_Paulo
- **AND** a LIMIT envelope with month-to-date expenses "50.00"
- **WHEN** the owner requests spending pace for that month
- **THEN** elapsed days used in the pace formula are 1
- **AND** the response succeeds without division-by-zero

### Requirement: Projected overrun day and daily remaining allowance
When projected month spend exceeds the monthly limit capacity, the system SHALL return the earliest calendar day in the month on which cumulative pace would exhaust the capacity. Daily remaining allowance MUST be the remaining capacity divided by remaining calendar days including today, as a two-decimal string, and MUST be "0.00" when remaining capacity is zero or negative.

#### Scenario: Pace projects overrun before month end
- **GIVEN** today is "2026-10-10" in America/Sao_Paulo
- **AND** a LIMIT envelope named "Combustível" with month-to-date expenses "200.00" and monthly limit capacity "400.00"
- **WHEN** the owner requests spending pace for "2026-10"
- **THEN** projected month spend is "620.00"
- **AND** projected overrun day is "2026-10-20"
- **AND** daily remaining allowance is a two-decimal string for the remaining capacity over the remaining days

#### Scenario: Already over capacity
- **GIVEN** a LIMIT envelope with remaining capacity "-50.00" in the requested month
- **WHEN** the owner requests spending pace for that month
- **THEN** projected overrun day is null or equal to a day already reached
- **AND** daily remaining allowance is "0.00"

### Requirement: Authorization by envelope visibility
The spending-pace query SHALL include only LIMIT envelopes the authenticated user may view as owner or participant. A third party MUST receive no rows for envelopes they cannot see. The response MUST NOT include email, expense descriptions, or other personal detail beyond envelope identity and pace metrics.

#### Scenario: Participant sees shared LIMIT only
- **GIVEN** a participant shared on LIMIT "Mercado" and not on LIMIT "Pessoal"
- **WHEN** they request spending pace for the month
- **THEN** the response includes "Mercado"
- **AND** it does not include "Pessoal"

#### Scenario: Third party
- **GIVEN** a user who is neither owner nor participant of any LIMIT envelope in the month
- **WHEN** they request spending pace for that month
- **THEN** the response is an empty list
- **AND** no envelope identifiers of other users are returned

#### Scenario: Unauthenticated
- **GIVEN** an unauthenticated request
- **WHEN** spending pace is requested
- **THEN** the API rejects the request as unauthorized

### Requirement: Month parameter validation
The query MUST accept a calendar month reference for America/Sao_Paulo. An invalid month MUST return `400` with `application/problem+json`. The query MUST NOT persist rows, files, or cached projections.

#### Scenario: Valid month
- **GIVEN** an authenticated owner
- **WHEN** they request spending pace with month "2026-11"
- **THEN** the API returns JSON for that month

#### Scenario: Invalid month
- **GIVEN** an authenticated owner
- **WHEN** they request spending pace with month "2026-13"
- **THEN** the API responds `400` with `application/problem+json`
