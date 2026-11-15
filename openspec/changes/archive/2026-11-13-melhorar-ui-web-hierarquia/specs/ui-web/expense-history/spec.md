# Spec Delta

## Purpose

Define a página de histórico de lançamentos: consultar, corrigir e registrar um gasto sem copy que promete uma ação inexistente, e sem datas ou gráficos ambíguos.

## ADDED Requirements

### Requirement: History copy matches available actions
The ledger history page SHALL describe only actions the page actually offers. If the page allows registering an expense, it MUST expose a visible control that opens the existing expense dialog for an envelope the user can spend on.

#### Scenario: Owner can register from history
- **GIVEN** an owner with at least one envelope they can spend on
- **WHEN** they open the ledger history page
- **THEN** a visible control starts registering an expense
- **AND** the page copy does not promise registration unless that control exists

#### Scenario: Participant cannot edit others' entries
- **GIVEN** a participant viewing a shared envelope entry they did not own
- **WHEN** the history list renders
- **THEN** they can read amount, date and description
- **AND** they MUST NOT see edit or delete for an entry whose role is not OWNER

#### Scenario: Empty period
- **GIVEN** an owner and a date range with no entries
- **WHEN** the history page renders
- **THEN** an empty state says there are no expenses in the period
- **AND** if registration is available, the empty state offers that action

### Requirement: History dates use Brazilian locale
Visible entry dates on the history page SHALL use a day/month/year format appropriate for `pt-BR`. Filter inputs MAY remain ISO date controls.

#### Scenario: List date is localized
- **GIVEN** an entry with occurredAt "2026-08-10"
- **WHEN** the history list renders
- **THEN** the visible date is not the raw ISO string "2026-08-10"
- **AND** the visible date is understandable as 10 de agosto de 2026 in Portuguese locale

### Requirement: Purpose breakdown is a chart or is not called a chart
A section titled as a chart SHALL encode values as a visual proportion (bar, pie or equivalent) plus a text amount. A legend-only list MUST NOT use a chart heading.

#### Scenario: Purpose totals rendered
- **GIVEN** purpose totals that include LIMIT "1200.00" and GOAL "300.00"
- **WHEN** the history page shows the type breakdown
- **THEN** each purpose amount is visible as text with two decimal places
- **AND** either each purpose has a proportional visual encoding or the section heading does not call the list a chart
