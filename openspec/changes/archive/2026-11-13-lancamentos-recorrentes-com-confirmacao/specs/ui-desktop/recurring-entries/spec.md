# Spec Delta

## Purpose

Leva as recorrências e as pendências de confirmação ao cliente desktop Tauri,
com as mesmas regras da API e sem lógica financeira no cliente.

## ADDED Requirements

### Requirement: Owner manages recurrences on the desktop
A tela de verbas SHALL permitir ao proprietário listar, criar, editar,
desativar/reativar e excluir recorrências da verba selecionada. Para
participantes, a seção MUST NOT ser exibida.

#### Scenario: Owner creates a recurrence on the desktop
- **GIVEN** o proprietário com a verba "Moradia" selecionada
- **WHEN** ele cria "Aluguel" de "1800.00" no dia 5
- **THEN** a recorrência aparece como ativa com "R$ 1.800,00" e "todo dia 5"

#### Scenario: Participant does not see recurrences
- **GIVEN** um participante com a verba compartilhada "Casa" selecionada
- **WHEN** a tela carrega
- **THEN** a seção de recorrências não aparece

### Requirement: Overview lists pending and overdue occurrences
A visão geral do desktop SHALL exibir pendências do mês e vencidas, separadas
por rótulo textual, com ações de confirmar, ajustar e descartar. Após sucesso,
os saldos MUST ser recarregados; em erro, a pendência MUST permanecer.

#### Scenario: Owner confirms from the desktop overview
- **GIVEN** a pendência "Aluguel" de "1800.00"
- **WHEN** o proprietário confirma
- **THEN** a pendência sai da lista e o saldo da verba é recarregado

#### Scenario: Overdue label in text
- **GIVEN** uma pendência da competência anterior
- **WHEN** o proprietário abre a visão geral
- **THEN** ela aparece sob o rótulo "Vencidas"

#### Scenario: Keyboard-only confirmation
- **WHEN** o proprietário navega pela lista só com teclado
- **THEN** ele alcança e aciona confirmar, ajustar e descartar com foco visível
