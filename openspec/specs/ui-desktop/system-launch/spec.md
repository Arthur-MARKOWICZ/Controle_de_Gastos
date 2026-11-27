# system-launch Specification

## Purpose

Permite iniciar o lançamento de gasto no desktop a partir do sistema operacional
— atalho global, bandeja e início automático — sem depender da janela focada.

## Requirements

### Requirement: Global shortcut opens expense dialog
Com a pessoa autenticada e o aplicativo em execução, um atalho global de teclado
configurado pelo cliente (padrão `Ctrl+Alt+G` no Linux) SHALL trazer o aplicativo
à frente e abrir o diálogo de captura de gasto, mesmo que a janela estivesse
minimizada ou em segundo plano.

#### Scenario: Shortcut while window unfocused
- **GIVEN** a pessoa está autenticada no desktop Linux
- **AND** a janela do Verbas não está em foco
- **WHEN** ela pressiona o atalho global padrão
- **THEN** o diálogo de captura de gasto abre
- **AND** o foco vai ao formulário de lançamento

#### Scenario: Shortcut while signed out
- **GIVEN** o aplicativo está aberto sem sessão
- **WHEN** ela pressiona o atalho global
- **THEN** o cliente mostra a superfície de autenticação
- **AND** não abre o diálogo de gasto

### Requirement: Tray icon offers new expense
O cliente desktop SHALL exibir um ícone na bandeja do sistema (Linux validável)
com ao menos a ação de registrar gasto e a de mostrar a janela principal.
Clicar em registrar gasto MUST abrir o diálogo de captura quando autenticado.

#### Scenario: Tray new expense while signed in
- **GIVEN** a pessoa está autenticada
- **WHEN** ela escolhe "Novo gasto" no menu da bandeja
- **THEN** o diálogo de captura abre

#### Scenario: Tray show window
- **GIVEN** a janela principal está oculta
- **WHEN** ela escolhe mostrar a janela no menu da bandeja
- **THEN** a janela principal fica visível

### Requirement: Optional autostart on login
O cliente SHALL oferecer uma preferência local de iniciar automaticamente com a
sessão do sistema operacional (Linux validável). A preferência MUST ser
desligada por padrão e MUST NOT enviar o estado à API.

#### Scenario: Enable autostart
- **GIVEN** a preferência de autostart está desligada
- **WHEN** a pessoa a liga nas configurações locais do desktop
- **THEN** o sistema passa a iniciar o aplicativo no login da sessão
- **AND** a API não recebe essa preferência

#### Scenario: Default off
- **GIVEN** uma instalação nova do desktop
- **WHEN** a pessoa não alterou autostart
- **THEN** o aplicativo não se registra para iniciar no login

### Requirement: In-window shortcut remains
O atalho de janela focada já existente (`Ctrl+N` / `Cmd+N`) MUST continuar
abrindo o diálogo de captura quando a janela tem foco. Ele MUST NOT substituir
o atalho global de SO.

#### Scenario: Ctrl+N with focused window
- **GIVEN** a pessoa está autenticada e a janela do desktop tem foco
- **AND** existe ao menos uma verba spendable
- **WHEN** ela pressiona `Ctrl+N` (ou `Cmd+N` no macOS)
- **THEN** o diálogo de captura abre
- **AND** o atalho global de SO permanece disponível em paralelo
