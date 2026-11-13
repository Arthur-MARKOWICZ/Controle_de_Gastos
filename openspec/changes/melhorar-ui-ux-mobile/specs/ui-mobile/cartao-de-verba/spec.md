# Spec Delta

## Purpose

Garante que o card de verba no Android mostre o saldo e uma ação primária óbvia, sem esconder nem comprimir as demais ações em uma linha que estoura a largura.

## ADDED Requirements

### Requirement: Ação primária visível
O card SHALL exibir uma única ação primária com alvo de toque de pelo menos 48 dp: "Aportar" para o proprietário de meta de aporte ou acumulação, e "Registrar" nas demais verbas em que a pessoa pode lançar.

#### Scenario: Proprietário de limite
- **GIVEN** a pessoa é proprietária de uma verba de limite de gasto
- **WHEN** o card aparece na lista
- **THEN** a ação primária é "Registrar"
- **AND** o alvo tem altura mínima de 48 dp

#### Scenario: Proprietário de meta
- **GIVEN** a pessoa é proprietária de uma verba de meta de aporte
- **WHEN** o card aparece na lista
- **THEN** a ação primária é "Aportar"

#### Scenario: Participante
- **GIVEN** a pessoa é participante, não proprietária
- **WHEN** o card aparece na lista
- **THEN** a ação primária é "Registrar"
- **AND** não há ação de editar, encerrar nem gerenciar participantes

### Requirement: Ações secundárias fora da linha principal
Lançamentos, participantes, editar e encerrar SHALL permanecer alcançáveis por um controle "Mais" (ou equivalente) no próprio card, sem depender só de gesto, e MUST NÃO ficar todos na mesma linha que a ação primária.

#### Scenario: Menu do proprietário
- **GIVEN** a pessoa é proprietária
- **WHEN** ela abre "Mais" no card
- **THEN** vê Lançamentos, Participantes, Editar e Encerrar
- **AND** Encerrar pede confirmação que explica que os lançamentos já feitos permanecem no histórico

#### Scenario: Largura de telefone estreito
- **GIVEN** a largura útil do card é a de um telefone de 360 dp
- **WHEN** o card de um proprietário é exibido
- **THEN** nenhum rótulo de ação é cortado horizontalmente
- **AND** os alvos adjacentes têm pelo menos 8 dp de separação

### Requirement: Estado negativo sem emoji estrutural
Saldo negativo e meta alcançada SHALL ser comunicados por texto e cor (e ícone vetorial, se houver), não por emoji como único indicador.

#### Scenario: Verba negativa
- **GIVEN** a verba tem disponível negativo, por exemplo `"-3.50"`
- **WHEN** o card é exibido
- **THEN** o estado "saldo negativo" é lido por texto
- **AND** a cor de erro reforça o aviso sem substituir o texto
