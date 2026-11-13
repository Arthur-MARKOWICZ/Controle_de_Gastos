# Spec Delta

## Purpose

Descreve como o app mobile troca o código de uso único emitido ao fim do login
social (ADR-0020) por uma sessão com token de acesso e refresh.

## ADDED Requirements

### Requirement: Redeem a mobile handoff code once
`POST /api/v1/auth/oauth/mobile-handoff` SHALL aceitar um código válido, não
expirado e não consumido, marcá-lo como consumido e responder `200` com
`accessToken`, `tokenType` igual a `Bearer`, `expiresIn`, `refreshToken` e o
nome do cookie de refresh. O endpoint não exige sessão prévia.

#### Scenario: Código válido
- **GIVEN** um código de handoff emitido há instantes para o titular A
- **WHEN** o app envia o código
- **THEN** a API responde `200` com uma sessão de A e `tokenType` igual a `"Bearer"`

### Requirement: Reject invalid, expired or reused handoff codes
Código desconhecido, expirado ou já consumido SHALL responder `400` com código
`OAUTH_LOGIN_FAILED` e a mesma mensagem nos três casos, sem emitir sessão e sem
registrar o código em log. Corpo sem `code` SHALL responder `400`
`INVALID_REQUEST`.

#### Scenario: Código reutilizado
- **GIVEN** um código já resgatado com sucesso
- **WHEN** o app envia o mesmo código de novo
- **THEN** a API responde `400` com código `OAUTH_LOGIN_FAILED`
- **AND** nenhuma sessão nova é emitida

#### Scenario: Código expirado
- **GIVEN** um código emitido além do tempo de vida do handoff
- **WHEN** o app envia o código
- **THEN** a API responde `400` com código `OAUTH_LOGIN_FAILED`

#### Scenario: Código desconhecido
- **WHEN** o app envia um código que nunca foi emitido
- **THEN** a API responde `400` com código `OAUTH_LOGIN_FAILED` e a mesma mensagem do código expirado

#### Scenario: Resgates concorrentes
- **GIVEN** um código válido
- **WHEN** duas requisições tentam resgatá-lo ao mesmo tempo
- **THEN** no máximo uma recebe `200` e a outra recebe `400` `OAUTH_LOGIN_FAILED`

#### Scenario: Código ausente
- **WHEN** o corpo não traz `code`
- **THEN** a API responde `400` com código `INVALID_REQUEST`
