# Spec Delta

## Purpose

Define como a web apresenta uma verba: o que o usuário pode fazer agora, quanto já avançou neste mês ou nesta meta, e o que acontece ao encerrar.

## ADDED Requirements

### Requirement: Envelope card has one primary action
The envelope card SHALL expose a single visually primary action for the current user role and envelope purpose. Remaining actions MUST be visually secondary and MUST NOT compete as equal text links in the same row.

#### Scenario: Owner of a spending envelope
- **GIVEN** an owner of a LIMIT or FIXED envelope named "Combustível"
- **WHEN** the card renders
- **THEN** the primary action is to register an expense in "Combustível"
- **AND** participants, archive and wealth-allocation controls are secondary

#### Scenario: Owner of a contribution envelope
- **GIVEN** an owner of a GOAL or SAVINGS_TARGET envelope
- **WHEN** the card renders
- **THEN** the primary action is to register a contribution
- **AND** a participant of the same envelope sees register-expense as the primary action and MUST NOT see archive or participant-management actions

#### Scenario: Third party
- **GIVEN** a user who is neither owner nor participant of the envelope
- **WHEN** they request the month view
- **THEN** the envelope card is not shown

### Requirement: Progress fills as the period is consumed or the goal advances
The progress indicator SHALL use one fill metaphor per purpose family: LIMIT and FIXED fill as the month is spent; GOAL, SAVINGS_TARGET and ANNUAL_EXPENSE fill as the target is reached. The card MUST also state the remaining or missing amount in text, not by color alone.

#### Scenario: Limit envelope half spent
- **GIVEN** a LIMIT envelope with baseAmount "400.00" and available "200.00"
- **WHEN** the card renders
- **THEN** the progress value represents 50 percent spent
- **AND** the status text includes the remaining amount "200.00"

#### Scenario: Fixed commitment partly paid
- **GIVEN** a FIXED envelope with baseAmount "1200.00" and available "300.00"
- **WHEN** the card renders
- **THEN** the progress value represents 75 percent spent
- **AND** the status text and the progress value describe the same direction, so the text does not report 25 percent while the bar reports 75 percent

#### Scenario: Goal envelope half complete
- **GIVEN** a GOAL envelope whose remaining amount is "500.00" of a planned "1000.00"
- **WHEN** the card renders
- **THEN** the progress value represents 50 percent complete
- **AND** the status text includes the missing amount "500.00"

#### Scenario: Negative balance
- **GIVEN** an envelope with isNegative true and available "-50.00"
- **WHEN** the card renders
- **THEN** the negative state is announced in text
- **AND** the primary action remains available

### Requirement: Archive copy matches the confirmation
The control that ends an envelope and the confirmation dialog SHALL use the same verb. The confirmation MUST explain that past ledger entries remain in history.

#### Scenario: Owner confirms ending an envelope
- **GIVEN** an owner viewing an envelope they can archive
- **WHEN** they activate the end action
- **THEN** the control label and the dialog title use the same verb
- **AND** the message states that existing entries stay in history

#### Scenario: Participant cannot end the envelope
- **GIVEN** a participant viewing a shared envelope
- **WHEN** the card renders
- **THEN** no end-envelope action is offered
