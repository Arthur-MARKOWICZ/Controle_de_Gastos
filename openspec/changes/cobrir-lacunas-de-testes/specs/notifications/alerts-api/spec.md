# Spec Delta

## Purpose

Descreve o contrato HTTP dos alertas in-app do titular: listar os alertas ativos
e dispensar um alerta, sempre restrito aos alertas do próprio titular.

## ADDED Requirements

### Requirement: List active alerts of the current user
`GET /api/v1/alerts` SHALL retornar apenas os alertas ativos (não dispensados)
do titular autenticado, cada um com `id`, `type`, `envelopeId`, `envelopeName`,
`referenceMonth` no formato `AAAA-MM` e `createdAt`.

#### Scenario: Titular com alertas
- **GIVEN** o titular A tem um alerta ativo de saldo negativo na verba "Mercado" em 2026-11
- **WHEN** A chama `GET /api/v1/alerts`
- **THEN** a API responde `200` com esse alerta em `items` e `referenceMonth` igual a `"2026-11"`

#### Scenario: Alertas de outro titular não aparecem
- **GIVEN** o titular B tem alertas ativos
- **WHEN** o titular A chama `GET /api/v1/alerts`
- **THEN** a resposta não contém nenhum alerta de B

#### Scenario: Alerta dispensado não aparece
- **GIVEN** o titular A dispensou um alerta
- **WHEN** A lista os alertas
- **THEN** o alerta dispensado não está em `items`

#### Scenario: Sem sessão
- **WHEN** a listagem é chamada sem token de acesso
- **THEN** a API responde `401`

### Requirement: Dismiss an alert owned by the current user
`POST /api/v1/alerts/{id}/dismiss` SHALL marcar o alerta do titular autenticado
como dispensado e responder `204`. Alerta inexistente ou de outro titular SHALL
responder `404` com a mesma mensagem, sem revelar se o alerta existe.

#### Scenario: Dispensa bem-sucedida
- **GIVEN** o titular A tem um alerta ativo
- **WHEN** A dispensa o alerta
- **THEN** a API responde `204`
- **AND** o alerta deixa de aparecer na listagem de A

#### Scenario: Alerta de outro titular
- **GIVEN** um alerta ativo do titular B
- **WHEN** o titular A tenta dispensá-lo
- **THEN** a API responde `404` com a mensagem "Alerta não encontrado"
- **AND** o alerta continua ativo para B

#### Scenario: Alerta inexistente
- **WHEN** o titular A dispensa um `id` que não existe
- **THEN** a API responde `404` com a mesma mensagem usada para alerta de outro titular

#### Scenario: Identificador malformado
- **WHEN** o titular A chama a dispensa com um `id` que não é UUID
- **THEN** a API responde `400`

#### Scenario: Dispensa sem sessão
- **WHEN** a dispensa é chamada sem token de acesso
- **THEN** a API responde `401`
