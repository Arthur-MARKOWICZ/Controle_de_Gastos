# Spec Delta

## Purpose

Leva as recorrências e as pendências de confirmação ao app Android, onde o
hábito diário acontece: confirmar o aluguel deve custar um toque.

## ADDED Requirements

### Requirement: Owner manages recurrences in the app
O app SHALL permitir ao proprietário listar, criar, editar, desativar/reativar
e excluir recorrências pela ação "Recorrências" do menu do card da verba. Para
participantes, a ação MUST NOT ser exibida.

#### Scenario: Owner creates a recurrence on the phone
- **GIVEN** o proprietário com as recorrências da verba "Moradia" abertas pelo card
- **WHEN** ele cria "Aluguel" de "1800.00" no dia 5
- **THEN** a recorrência aparece como ativa com "R$ 1.800,00" e "todo dia 5"

#### Scenario: Participant does not see recurrences
- **GIVEN** um participante vendo o card da verba compartilhada "Casa"
- **WHEN** ele abre o menu do card
- **THEN** a ação "Recorrências" não aparece

### Requirement: Home lists pending and overdue occurrences
A tela inicial SHALL exibir as pendências do mês e, separadas por rótulo
textual, as vencidas, com verba, descrição, valor sugerido e data prevista.
Sem pendências, a seção MUST NOT aparecer.

#### Scenario: Owner sees pending items on the home screen
- **GIVEN** uma pendência "Aluguel" de "1800.00" e uma vencida "Internet" de "99.90"
- **WHEN** o proprietário abre o app
- **THEN** "Aluguel" aparece em pendentes e "Internet" em "Vencidas"

### Requirement: One-tap confirmation with optional adjustment
Cada pendência SHALL ter ação de confirmar com um toque usando o valor sugerido,
ação para ajustar valor e data antes de confirmar, e ação de descartar com
confirmação. Os alvos de toque MUST ter ao menos 48dp.

#### Scenario: One-tap confirm
- **GIVEN** a pendência "Aluguel" de "1800.00"
- **WHEN** o proprietário toca em confirmar
- **THEN** a pendência some da lista e o saldo da verba é atualizado

#### Scenario: Offline or API error keeps the item
- **GIVEN** a confirmação falha por rede ou erro da API
- **WHEN** o proprietário toca em confirmar
- **THEN** a pendência continua listada e uma mensagem explica a falha
