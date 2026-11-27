# app-shell Specification

## Purpose

Define como a web autenticada apresenta navegação, cabeçalho de página, tipografia e chrome compartilhado, para o usuário achar a tarefa sem decifrar o modelo interno.

## Requirements

### Requirement: Destination labels do not collide
The web app SHALL give each primary destination a label that a first-time user can distinguish from the others, without requiring knowledge of envelope purpose codes.

#### Scenario: History versus annual expenses
- **GIVEN** an authenticated owner
- **WHEN** they open the main navigation
- **THEN** the destination for ledger history is not labelled identically to annual expenses
- **AND** both destinations remain reachable

#### Scenario: Participant sees the same labels
- **GIVEN** an authenticated participant of at least one envelope
- **WHEN** they open the main navigation
- **THEN** destination labels match those shown to an owner
- **AND** a third party without a session does not see the authenticated navigation

### Requirement: Navigation fits every destination
At every supported viewport the main navigation SHALL show every primary destination without clipping labels or using a column count smaller than the destination count.

#### Scenario: Medium viewport lists all destinations
- **GIVEN** an authenticated user and a viewport at most 64rem wide
- **WHEN** the shell renders
- **THEN** all primary destinations are visible
- **AND** no destination label is truncated by a five-column grid

#### Scenario: Current page is indicated
- **GIVEN** an authenticated user on a primary destination
- **WHEN** the shell renders
- **THEN** the matching navigation item is marked as the current page

### Requirement: Authenticated pages share page chrome
Every authenticated page SHALL use the shared page header pattern (eyebrow, one `h1`, description, optional actions) and SHALL load the Inter typeface declared by the design tokens.

#### Scenario: Reports and history use the shared header
- **GIVEN** an authenticated user
- **WHEN** they open Relatórios or the ledger history page
- **THEN** the page uses the same header pattern as Visão geral
- **AND** primary actions use the shared button styles

#### Scenario: Settings and security use the shared header
- **GIVEN** an authenticated owner
- **WHEN** they open Configurações or Segurança
- **THEN** the page uses the shared header pattern
- **AND** the page has exactly one `h1`
- **AND** a third party cannot open those pages without authenticating

#### Scenario: Inter is the rendered UI font
- **GIVEN** any authenticated page
- **WHEN** the document loads
- **THEN** the UI font stack uses a loaded Inter face, not only an unloaded family name in CSS

### Requirement: Carteira is a primary destination
A navegação principal autenticada SHALL incluir o destino "Carteira", com
rótulo distinto de Verbas, Metas, Histórico e demais destinos de orçamento.
O destino MUST apontar para `/carteira` e MUST aparecer para titular e
participante de verba com o mesmo rótulo.

#### Scenario: Carteira appears in main navigation
- **GIVEN** um usuário autenticado
- **WHEN** ele abre a navegação principal
- **THEN** o destino "Carteira" está visível
- **AND** o rótulo não é idêntico ao de Verbas, Metas ou Histórico

#### Scenario: Participant sees the same Carteira label
- **GIVEN** um participante autenticado de pelo menos uma verba
- **WHEN** ele abre a navegação principal
- **THEN** o rótulo "Carteira" é o mesmo mostrado ao titular
- **AND** um terceiro sem sessão não vê a navegação autenticada

#### Scenario: Current page marks Carteira
- **GIVEN** um usuário autenticado em `/carteira`
- **WHEN** o shell renderiza
- **THEN** o item Carteira é marcado como página atual
