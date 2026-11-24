# Spec Delta

## Purpose

Define a janela autenticada do desktop: chrome nativo, navegação do primeiro
corte, tema local e operação completa por teclado.

## ADDED Requirements

### Requirement: Authenticated window chrome
A janela autenticada SHALL unificar titlebar e navegação lateral. A navegação do
primeiro corte MUST oferecer Visão geral e Verbas, com indicação da tela atual.
Tema e saída de conta MUST permanecer acessíveis sem destinos fora deste corte.

#### Scenario: Owner sees first-cut navigation
- **GIVEN** um titular autenticado
- **WHEN** a área autenticada abre
- **THEN** a navegação lista Visão geral e Verbas
- **AND** a tela atual é anunciada ao usuário

#### Scenario: Participant uses the same destinations
- **GIVEN** um usuário autenticado apenas como participante de verbas
- **WHEN** a área autenticada abre
- **THEN** a navegação lista os mesmos destinos do primeiro corte

### Requirement: Local theme preference
O cliente SHALL oferecer `SYSTEM`, `LIGHT` e `DARK`, persistir a escolha só no
dispositivo e não enviá-la à API. Sem escolha prévia, SHALL seguir o sistema.

#### Scenario: User selects dark theme
- **GIVEN** um titular autenticado
- **WHEN** o usuário escolhe o tema escuro
- **THEN** as superfícies passam ao tema escuro
- **AND** um arranque seguinte restaura essa escolha sem consultar a API

#### Scenario: System preference with no stored choice
- **GIVEN** nenhuma preferência de tema no dispositivo
- **WHEN** o aplicativo inicia
- **THEN** o tema segue a preferência do sistema operacional

### Requirement: Keyboard operation
Toda ação do primeiro corte MUST ser operável sem ponteiro. O atalho de
registrar gasto na janela focada SHALL abrir a captura. Escape SHALL fechar o
diálogo ou paleta aberta. O foco visível MUST permanecer sem ficar coberto pelo
chrome fixo.

#### Scenario: Shortcut opens expense capture
- **GIVEN** a área autenticada focada e ao menos uma verba em que o usuário pode
  gastar
- **WHEN** o usuário aciona o atalho de registrar gasto
- **THEN** o diálogo de captura abre e o foco vai para o formulário

#### Scenario: Escape closes the dialog
- **GIVEN** o diálogo de captura aberto
- **WHEN** o usuário pressiona Escape
- **THEN** o diálogo fecha
- **AND** o foco volta ao controle que o abriu

### Requirement: Skip to content
O shell SHALL oferecer um atalho de teclado para pular a navegação e ir ao
conteúdo principal.

#### Scenario: Keyboard user skips navigation
- **WHEN** o usuário ativa o link de pular para o conteúdo
- **THEN** o foco move para a região principal da tela atual
