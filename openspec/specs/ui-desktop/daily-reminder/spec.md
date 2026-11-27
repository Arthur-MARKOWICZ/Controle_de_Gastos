# daily-reminder Specification

## Purpose

Lembra a pessoa, no cliente desktop aberto, de registrar os gastos do dia no
horário escolhido na API, usando a notificação do sistema operacional.

## Requirements

### Requirement: Configuração do lembrete no desktop
O desktop SHALL exibir o lembrete diário com ativação e horário em passos de 15
minutos, lidos e salvos na API pelo processo nativo. A tela SHALL avisar que o
lembrete só dispara com o aplicativo aberto.

#### Scenario: Ativar no desktop
- **WHEN** a pessoa ativa o lembrete às `19:00`
- **THEN** a preferência é salva na API
- **AND** a tela informa que o app precisa estar aberto

### Requirement: Disparo com o app aberto
Com o app aberto e sessão válida, o desktop SHALL consultar "lançou hoje" no
horário e mostrar notificação do sistema só se a resposta for `false`. Com o
app fechado, nada SHALL ser exibido nem recuperado depois.

#### Scenario: App aberto sem gasto
- **GIVEN** o app está aberto, lembrete ativo às `19:00` e sem gasto hoje
- **WHEN** chega `19:00`
- **THEN** o sistema operacional mostra "Nenhum lançamento hoje"

#### Scenario: App aberto após o horário
- **GIVEN** o lembrete era às `19:00` e o app foi aberto às `21:00`
- **WHEN** o app inicia
- **THEN** nenhuma notificação atrasada é exibida

#### Scenario: Sem sessão
- **GIVEN** a sessão expirou e não foi restaurada
- **WHEN** chega o horário
- **THEN** nenhuma notificação é exibida

### Requirement: Permissão e logout
O desktop SHALL pedir a permissão de notificação ao ativar, quando o sistema
exigir. O logout SHALL cancelar o lembrete.

#### Scenario: Logout
- **WHEN** a pessoa sai da conta no desktop
- **THEN** o lembrete deixa de disparar
