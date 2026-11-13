# Spec Delta

## Purpose

Descreve o contrato HTTP de consulta do estado do MFA TOTP e de regeneração dos
códigos de recuperação do titular autenticado (ADR-0019).

## ADDED Requirements

### Requirement: Report MFA status of the current user
`GET /api/v1/mfa/status` SHALL responder `200` com `status` igual a
`DISABLED`, `PENDING` ou `ENABLED` e `pendingExpiresAt`, preenchido apenas
quando há cadastro pendente. Titular que nunca iniciou o cadastro SHALL receber
`DISABLED`.

#### Scenario: MFA habilitado
- **GIVEN** o titular concluiu o cadastro TOTP
- **WHEN** ele consulta o status
- **THEN** a API responde `200` com `status` igual a `ENABLED`

#### Scenario: Cadastro pendente
- **GIVEN** o titular iniciou o cadastro e ainda não confirmou o código
- **WHEN** ele consulta o status
- **THEN** a resposta traz `status` igual a `PENDING` e `pendingExpiresAt` preenchido

#### Scenario: Nunca cadastrado
- **WHEN** um titular sem credencial TOTP consulta o status
- **THEN** a resposta traz `status` igual a `DISABLED` e `pendingExpiresAt` nulo

#### Scenario: Status sem sessão
- **WHEN** o status é consultado sem token de acesso
- **THEN** a API responde `401`

### Requirement: Regenerate recovery codes with password confirmation
`POST /api/v1/mfa/recovery-codes` SHALL exigir a senha atual, invalidar os
códigos de recuperação ativos e retornar um novo conjunto. Senha ausente SHALL
responder `400` `INVALID_REQUEST`; senha errada `401`
`PASSWORD_REQUIRED_INVALID`; MFA não habilitado `409` `MFA_NOT_ENABLED`.

#### Scenario: Regeneração bem-sucedida
- **GIVEN** o titular tem MFA habilitado
- **WHEN** ele envia a senha correta
- **THEN** a API responde `200` com um novo conjunto de códigos
- **AND** os códigos anteriores deixam de funcionar no login de recuperação

#### Scenario: Senha incorreta
- **WHEN** o titular envia uma senha errada
- **THEN** a API responde `401` com código `PASSWORD_REQUIRED_INVALID`
- **AND** os códigos anteriores continuam válidos

#### Scenario: Senha em branco
- **WHEN** o corpo traz `password` vazio
- **THEN** a API responde `400` com código `INVALID_REQUEST`

#### Scenario: MFA não habilitado
- **GIVEN** o titular tem MFA desabilitado ou pendente
- **WHEN** ele envia a senha correta
- **THEN** a API responde `409` com código `MFA_NOT_ENABLED`

#### Scenario: Conta só com login social
- **GIVEN** uma conta criada via OAuth, sem senha
- **WHEN** ela tenta regenerar os códigos
- **THEN** a API responde `401` com código `PASSWORD_REQUIRED_INVALID`

#### Scenario: Regeneração sem sessão
- **WHEN** a regeneração é chamada sem token de acesso
- **THEN** a API responde `401`
