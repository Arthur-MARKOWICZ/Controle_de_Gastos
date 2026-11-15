# Spec Delta

## Purpose

Descreve o contrato HTTP de gestão de participantes de uma verba compartilhada
(ADR-0026): só o proprietário lista, convida e remove participantes.

## ADDED Requirements

### Requirement: Only the owner manages participants
`GET`, `POST` e `DELETE` em `/api/v1/envelopes/{envelopeId}/participants`
SHALL ser permitidos apenas ao proprietário da verba. Participante SHALL
receber `403`; terceiro sem vínculo SHALL receber `403` ou `404` sem dados da
verba; verba arquivada ou inexistente SHALL responder `404`; sem sessão `401`.

#### Scenario: Proprietário lista participantes
- **GIVEN** a verba do titular A compartilhada com B
- **WHEN** A lista os participantes
- **THEN** a API responde `200` com B, seu e-mail e a data de inclusão

#### Scenario: Participante tenta listar
- **GIVEN** B é participante da verba de A
- **WHEN** B lista os participantes
- **THEN** a API responde `403`

#### Scenario: Terceiro tenta convidar
- **GIVEN** C não tem vínculo com a verba de A
- **WHEN** C convida alguém para a verba
- **THEN** a API responde `403` ou `404`
- **AND** nenhum participante é incluído

#### Scenario: Verba arquivada
- **GIVEN** a verba de A está arquivada
- **WHEN** A lista os participantes
- **THEN** a API responde `404`

#### Scenario: Sem sessão
- **WHEN** qualquer uma das três operações é chamada sem token de acesso
- **THEN** a API responde `401`

### Requirement: Invite a verified user by email
`POST` SHALL incluir como participante o usuário verificado com o e-mail
informado e responder com seus dados. Convidar quem já participa SHALL ser
idempotente. Corpo sem `email` SHALL responder `400` `INVALID_REQUEST`; e-mail
desconhecido ou não verificado SHALL responder `400` com mensagem genérica que
não revela se a conta existe; convidar o proprietário SHALL responder `400`.

#### Scenario: Convite bem-sucedido
- **GIVEN** B é um usuário verificado
- **WHEN** A convida o e-mail de B para sua verba
- **THEN** a API responde com B como participante

#### Scenario: Convite repetido
- **GIVEN** B já participa da verba de A
- **WHEN** A convida B de novo
- **THEN** a API responde com sucesso sem duplicar o participante

#### Scenario: E-mail desconhecido
- **WHEN** A convida um e-mail sem conta
- **THEN** a API responde `400` com a mesma mensagem genérica usada para conta não verificada

#### Scenario: Corpo sem e-mail
- **WHEN** A envia o convite sem o campo `email`
- **THEN** a API responde `400` com código `INVALID_REQUEST`

#### Scenario: Convidar o proprietário
- **WHEN** A convida o próprio e-mail
- **THEN** a API responde `400` com a mensagem "Não é possível convidar o proprietário da verba"

### Requirement: Remove a participant
`DELETE /{userId}` SHALL remover o participante da verba; a partir daí ele
MUST NOT acessar a verba nem seus lançamentos.

#### Scenario: Remoção bem-sucedida
- **GIVEN** B participa da verba de A
- **WHEN** A remove B
- **THEN** a remoção é confirmada
- **AND** B passa a receber `403` ou `404` ao consultar a verba

#### Scenario: Participante tenta remover outro
- **GIVEN** B e C participam da verba de A
- **WHEN** B tenta remover C
- **THEN** a API responde `403`
- **AND** C continua participante
