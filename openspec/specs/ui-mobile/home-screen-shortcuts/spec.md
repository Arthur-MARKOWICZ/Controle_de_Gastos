# home-screen-shortcuts Specification

## Purpose

Oferece no Android atalhos fora do app — widget na tela inicial e tile em Quick
Settings — para abrir o lançamento rápido com o mínimo de navegação.

## Requirements

### Requirement: Glance widget lists top envelopes
O aplicativo Android SHALL disponibilizar um widget Jetpack Glance que, com a
pessoa autenticada, mostra até três verbas sugeridas pela API de sugestões e uma
ação explícita de novo gasto. Sem sessão, o widget MUST explicar que é preciso
entrar no aplicativo.

#### Scenario: Authenticated widget shows three suggestions
- **GIVEN** a pessoa está autenticada e a API devolve três verbas sugeridas
- **WHEN** o widget é atualizado
- **THEN** ele exibe o nome das até três verbas
- **AND** cada verba ou a ação "+ gasto" abre o app no lançamento rápido

#### Scenario: Signed-out widget
- **GIVEN** não há sessão no dispositivo
- **WHEN** o widget é exibido
- **THEN** ele pede para abrir o app e entrar
- **AND** não mostra saldos nem valores de gasto

### Requirement: Widget opens quick expense with optional envelope
Tocar numa verba do widget SHALL abrir o aplicativo Android com o fluxo de
lançamento rápido, pré-selecionando aquela verba quando o identificador for
informado. Tocar só em "+ gasto" MUST abrir o lançamento rápido sem forçar
verba além da sugestão padrão da folha.

#### Scenario: Tap suggested envelope
- **GIVEN** o widget mostra "Almoço"
- **WHEN** a pessoa toca em "Almoço"
- **THEN** o app abre a folha de lançamento com "Almoço" selecionada

#### Scenario: Tap generic new expense
- **GIVEN** o widget mostra a ação "+ gasto"
- **WHEN** a pessoa toca nessa ação
- **THEN** o app abre a folha de lançamento rápido

### Requirement: Quick Settings tile opens quick expense
O aplicativo SHALL oferecer um tile de Quick Settings que, ao ser acionado com
a pessoa autenticada, abre o lançamento rápido (mesmo contrato de deep link do
lembrete diário). Sem sessão, MUST abrir o app na superfície de autenticação.

#### Scenario: Tile while signed in
- **GIVEN** a pessoa está autenticada
- **WHEN** ela aciona o tile de Quick Settings do Verbas
- **THEN** o app abre o lançamento rápido

#### Scenario: Tile while signed out
- **GIVEN** não há sessão
- **WHEN** ela aciona o tile
- **THEN** o app abre pedindo autenticação
- **AND** não registra gasto

### Requirement: Android is the validated mobile target
Os requisitos desta capacidade MUST ser validados no emulador ou dispositivo
Android. O projeto KMP MAY preservar stubs iOS, mas este change MUST NOT
declarar widget iOS testado.

#### Scenario: Acceptance on Android emulator
- **GIVEN** um build Android do aplicativo
- **WHEN** o aceite do widget e do tile é executado
- **THEN** a validação ocorre em emulador ou dispositivo Android
- **AND** nenhum artefato desta change afirma que widget iOS foi testado
