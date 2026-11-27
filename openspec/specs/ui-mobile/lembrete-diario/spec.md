# lembrete-diario Specification

## Purpose

Lembra a pessoa, no Android, de registrar os gastos do dia no horário escolhido
na API, com agendamento local e sem push remoto.

## Requirements

### Requirement: Configuração do lembrete na tela Mais
A tela "Mais" SHALL exibir o lembrete diário com ativação e horário em passos
de 15 minutos, lidos e salvos na API. Os demais tipos SHALL aparecer como
avisos só dentro do app neste canal.

#### Scenario: Ativar no Android
- **WHEN** a pessoa ativa o lembrete às `20:30`
- **THEN** a preferência é salva na API
- **AND** o lembrete local fica agendado para `20:30`

### Requirement: Permissão de notificação
No Android 13 ou superior, o app SHALL pedir a permissão de notificações ao
ativar o lembrete. Com permissão negada, o app SHALL manter a preferência na
API, não agendar e explicar como liberar a permissão.

#### Scenario: Permissão negada
- **GIVEN** Android 13
- **WHEN** a pessoa ativa o lembrete e nega a permissão
- **THEN** nenhum lembrete é agendado no aparelho
- **AND** o app explica como liberar a permissão

### Requirement: Disparo condicionado a "lançou hoje"
No horário agendado, o app SHALL consultar "lançou hoje" na API e notificar só
se a resposta for `false`. Sem sessão ou sem rede, o app MUST NOT notificar.
O disparo pode atrasar alguns minutos por restrições do sistema.

#### Scenario: Sem gasto no dia
- **GIVEN** lembrete ativo e "lançou hoje" igual a `false`
- **WHEN** chega o horário
- **THEN** o aparelho mostra "Nenhum lançamento hoje"

#### Scenario: Já lançou
- **GIVEN** "lançou hoje" igual a `true`
- **WHEN** chega o horário
- **THEN** nenhuma notificação aparece

#### Scenario: Toque abre o lançamento
- **WHEN** a pessoa toca na notificação
- **THEN** o app abre no lançamento rápido

### Requirement: Sincronização com a API
O app SHALL reagendar ao abrir e ao salvar preferências, conforme o horário e o
estado vindos da API. O logout SHALL cancelar o lembrete local.

#### Scenario: Horário alterado na web
- **GIVEN** o horário mudou de `20:00` para `22:00` na web
- **WHEN** a pessoa abre o app Android
- **THEN** o lembrete local passa a `22:00`

#### Scenario: Logout
- **WHEN** a pessoa sai da conta no Android
- **THEN** nenhum lembrete local permanece agendado
