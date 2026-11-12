# Spec Delta

## Purpose

Define como a web associa erros de validação e de API ao campo que o usuário precisa corrigir nos formulários de renda, verba e lançamento.

## ADDED Requirements

### Requirement: Field errors are bound to the invalid control
When a money, name or date field fails validation, the web app SHALL show the message next to that field, mark the control invalid, and associate the message with the control for assistive technology. A page-level notice MAY remain for errors that are not field-specific.

#### Scenario: Expense amount missing scale
- **GIVEN** an owner or participant who can spend on envelope "Combustível"
- **WHEN** they submit an expense without a value of exactly two decimal places
- **THEN** the amount field is marked invalid
- **AND** the error message is announced with that field
- **AND** focus moves to the amount field

#### Scenario: Envelope name empty
- **GIVEN** an owner creating an envelope
- **WHEN** they submit with an empty name
- **THEN** the name field is marked invalid
- **AND** a third party without a session cannot submit the form

#### Scenario: Income below committed bases
- **GIVEN** an owner whose committed envelope bases require at least "4000.00"
- **WHEN** they submit income "3000.00"
- **THEN** the income amount field shows that the value is below the minimum
- **AND** the message includes the required minimum as "4000.00" when the API provides it

### Requirement: Non-field errors stay recoverable
Errors that do not belong to a single field SHALL appear at the top of the form as an alert, with enough detail to retry, and MUST NOT log tokens, passwords or detailed financial payloads.

#### Scenario: Unexpected failure
- **GIVEN** an owner submitting a valid expense
- **WHEN** the API returns an unexpected error
- **THEN** the form shows a generic recovery message
- **AND** the amount field values remain so the user can retry
