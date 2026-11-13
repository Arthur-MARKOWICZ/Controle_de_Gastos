# recurring-entries Specification

## Purpose

Leva as recorrências e as pendências de confirmação para a web: o proprietário
gerencia recorrências na verba e trata pendentes e vencidas na visão geral.

## Requirements

### Requirement: Owner manages recurrences from the envelope card
O card da verba SHALL oferecer ao proprietário a ação "Recorrências", que abre
a lista de recorrências e formulários para criar, editar, desativar/reativar e
excluir, com valor em BRL e dia do mês entre 1 e 31. Para participantes, a
ação MUST NOT ser exibida.

#### Scenario: Owner creates a recurrence
- **GIVEN** o proprietário com as recorrências da verba "Moradia" abertas pelo card
- **WHEN** ele cria "Aluguel" de "1800.00" no dia 5
- **THEN** a recorrência aparece na lista como ativa, com "R$ 1.800,00" e "todo dia 5"

#### Scenario: Day 31 hint
- **WHEN** o proprietário escolhe o dia 29, 30 ou 31
- **THEN** o formulário informa que, em meses mais curtos, a data será o último dia do mês

#### Scenario: Participant does not see the recurrences action
- **GIVEN** um participante vendo o card da verba compartilhada "Casa"
- **WHEN** o card é exibido
- **THEN** a ação "Recorrências" não aparece

### Requirement: Overview shows pending and overdue occurrences
A visão geral SHALL exibir um bloco de pendências com as ocorrências do mês e,
separadas, as vencidas, cada uma com verba, descrição, valor sugerido e data
prevista. Sem pendências, o bloco MUST NOT ocupar espaço.

#### Scenario: Owner sees a pending rent
- **GIVEN** uma ocorrência pendente "Aluguel" de "1800.00" para 2026-12-05
- **WHEN** o proprietário abre a visão geral
- **THEN** o bloco lista "Aluguel", a verba, "R$ 1.800,00" e a data 05/12

#### Scenario: Overdue is labeled in text
- **GIVEN** uma ocorrência pendente da competência anterior
- **WHEN** o proprietário abre a visão geral
- **THEN** ela aparece no grupo "Vencidas", identificada por texto e não só por cor

#### Scenario: No pending occurrences
- **GIVEN** nenhuma ocorrência pendente
- **WHEN** o proprietário abre a visão geral
- **THEN** o bloco de pendências não é exibido

### Requirement: Confirm, adjust or discard from the overview
Cada pendência SHALL permitir confirmar com o valor sugerido, ajustar valor e
data antes de confirmar, ou descartar. Após sucesso, a pendência MUST sair da
lista e os saldos exibidos MUST ser recarregados; em erro, a pendência MUST
permanecer com mensagem acessível.

#### Scenario: Confirm with adjusted amount
- **GIVEN** a pendência "Energia" com sugestão "200.00"
- **WHEN** o proprietário ajusta para "237.45" e confirma
- **THEN** a pendência sai da lista e o saldo da verba reflete "237.45"

#### Scenario: Discard asks for confirmation
- **WHEN** o proprietário clica em descartar
- **THEN** a interface pede confirmação antes de enviar o descarte

#### Scenario: API refuses the confirmation
- **GIVEN** a API responde 409 à confirmação
- **WHEN** o proprietário confirma
- **THEN** a interface informa o erro e recarrega a lista de pendências
