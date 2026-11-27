# Spec Delta

## ADDED Requirements

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
