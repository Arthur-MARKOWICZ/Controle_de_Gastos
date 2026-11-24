# Spec Delta

## Purpose

Define a Visão geral como o painel do mês: um resumo financeiro, verbas que pedem atenção e o próximo lançamento, sem duplicar renda nem misturar configuração.

## ADDED Requirements

### Requirement: Overview shows a single month summary
The overview SHALL present income, allocated, unallocated and usage in one summary block. The same monetary amount MUST NOT be repeated as a second lead figure on the same page.

#### Scenario: Income configured
- **GIVEN** an owner with income "5000.00", allocated "4250.00" and unallocated "750.00" for the selected month
- **WHEN** they open Visão geral
- **THEN** the page shows one summary with income "5000.00", allocated "4250.00", unallocated "750.00" and usage percent
- **AND** it does not render a second card whose lead figure is again "5000.00"

#### Scenario: Income missing
- **GIVEN** an owner with no income for the selected month
- **WHEN** they open Visão geral
- **THEN** the summary states that income is not configured
- **AND** a control to configure income is available once, not as duplicate header and card actions

#### Scenario: Participant
- **GIVEN** a participant who can see shared envelopes but does not own the income
- **WHEN** they open Visão geral
- **THEN** they see envelopes they may view
- **AND** they MUST NOT be offered the control to configure the owner's income

### Requirement: Overview preview focuses on actionable envelopes
The overview envelope list SHALL be a short preview with a link to the full Verbas area. Header actions MUST stay limited to the month's primary tasks and MUST NOT include glossary as a primary action.

#### Scenario: Several envelopes exist
- **GIVEN** an owner with more than four visible envelopes in the month
- **WHEN** they open Visão geral
- **THEN** the preview lists at most four envelopes
- **AND** a link to see all envelopes is present
- **AND** creating an envelope is available without a second identical footer button

#### Scenario: Empty month
- **GIVEN** an owner with income and zero envelopes in the month
- **WHEN** they open Visão geral
- **THEN** an empty state explains that no envelope exists
- **AND** the empty state offers a control to create the first envelope

### Requirement: Negative-balance alerts appear on overview
Persistent in-app alerts for negative envelope balance SHALL appear on Visão geral for envelopes the current user can see, with a way to dismiss an alert the user has already handled.

#### Scenario: Owner has a persistent negative alert
- **GIVEN** an owner with a persistent alert that envelope "Combustível" went negative in "2026-08"
- **WHEN** they open Visão geral
- **THEN** the alert names "Combustível" and the month "2026-08"
- **AND** dismissing it removes the alert from the list

#### Scenario: Third party
- **GIVEN** a user with no access to the envelope
- **WHEN** they are not authenticated as owner or participant
- **THEN** they do not receive that envelope's alert
