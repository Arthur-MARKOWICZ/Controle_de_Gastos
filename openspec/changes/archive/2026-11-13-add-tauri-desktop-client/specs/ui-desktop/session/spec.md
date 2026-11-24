# Spec Delta

## Purpose

Autentica o titular no cliente desktop com e-mail, senha e MFA, restaura a
sessão no arranque e encerra o acesso sem expor o refresh à interface.

## ADDED Requirements

### Requirement: Email and password login
O cliente desktop SHALL autenticar com e-mail e senha contra a API. Em sucesso
sem MFA, SHALL apresentar a área autenticada. Em credencial inválida, SHALL
exibir mensagem genérica, sem indicar se o e-mail existe.

#### Scenario: Successful login without MFA
- **GIVEN** uma conta com senha e MFA desabilitado
- **WHEN** o usuário informa e-mail e senha corretos
- **THEN** o cliente entra na área autenticada

#### Scenario: Invalid credentials
- **WHEN** o usuário informa e-mail ou senha incorretos
- **THEN** o cliente recusa o acesso com mensagem genérica
- **AND** não revela se a conta existe

### Requirement: MFA challenge
Quando a API exigir segundo fator, o cliente SHALL pedir o código TOTP e só
concluir a sessão após verificação bem-sucedida. Código inválido SHALL manter o
desafio sem emitir sessão.

#### Scenario: TOTP succeeds
- **GIVEN** login que retorna desafio MFA
- **WHEN** o usuário informa um código TOTP válido
- **THEN** o cliente entra na área autenticada

#### Scenario: TOTP fails
- **GIVEN** um desafio MFA em curso
- **WHEN** o usuário informa um código inválido
- **THEN** o cliente permanece no desafio
- **AND** não abre a área autenticada

### Requirement: Session restore
No arranque, se houver refresh persistido no dispositivo, o cliente SHALL tentar
renovar a sessão. Refresh ausente, expirado ou revogado SHALL levar à tela de
login.

#### Scenario: Restore with valid refresh
- **GIVEN** um refresh válido guardado no dispositivo
- **WHEN** o aplicativo inicia
- **THEN** o titular autenticado vê a área autenticada sem informar a senha

#### Scenario: Restore with missing or invalid refresh
- **GIVEN** nenhum refresh válido no dispositivo
- **WHEN** o aplicativo inicia
- **THEN** o cliente mostra a tela de login

### Requirement: Logout
O logout SHALL revogar a sessão na API, apagar o refresh do dispositivo e voltar
à tela de login.

#### Scenario: User logs out
- **GIVEN** um titular autenticado
- **WHEN** o usuário escolhe sair
- **THEN** o cliente volta à tela de login
- **AND** um arranque seguinte não restaura essa sessão

### Requirement: Secrets stay off the UI and logs
O cliente desktop MUST NOT exibir, copiar para a interface ou registrar em logs
o access token, o refresh, a senha ou o código TOTP.

#### Scenario: Failed login is logged without secrets
- **WHEN** o login falha
- **THEN** qualquer registro técnico omite senha, tokens e código MFA
