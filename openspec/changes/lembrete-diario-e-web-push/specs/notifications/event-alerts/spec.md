# Spec Delta

## Purpose

Transforma mudanças no ledger em alertas para o dono da verba: saldo negativo e
gasto de participante em verba compartilhada. O alerta aparece in-app e, com
consentimento, também por push.

## ADDED Requirements

### Requirement: Alerta de saldo negativo
Quando um gasto criado, editado ou excluído levar o saldo de uma verba de
não-negativo para negativo no mês, o sistema SHALL criar um alerta in-app ativo
para o dono da verba, no máximo um ativo por verba e mês. Quando o saldo voltar
a não-negativo, o alerta ativo SHALL ser resolvido. O alerta MUST NOT guardar
valores monetários.

#### Scenario: Verba fica negativa
- **GIVEN** a verba do tipo `LIMIT` com saldo `"10.00"` no mês
- **WHEN** o dono registra um gasto de `"25.00"`
- **THEN** existe um alerta in-app ativo `NEGATIVE_BALANCE` para o dono

#### Scenario: Segunda transação negativa no mesmo mês
- **GIVEN** já há alerta ativo de saldo negativo para a verba no mês
- **WHEN** outro gasto deixa o saldo mais negativo
- **THEN** continua existindo um único alerta ativo

#### Scenario: Saldo volta ao positivo
- **GIVEN** um alerta ativo de saldo negativo
- **WHEN** a exclusão de um gasto deixa o saldo em `"5.00"`
- **THEN** o alerta fica resolvido

### Requirement: Alerta de gasto de participante
Quando um participante registrar um gasto em verba compartilhada, o sistema
SHALL criar um alerta in-app `SHARED_EXPENSE` para o dono da verba. Gasto
registrado pelo próprio dono MUST NOT gerar esse alerta. O alerta MUST NOT
guardar valor, descrição nem identificação do participante.

#### Scenario: Participante registra gasto
- **GIVEN** a pessoa B participa da verba da pessoa A
- **WHEN** B registra um gasto de `"40.00"` nessa verba
- **THEN** A tem um alerta in-app ativo `SHARED_EXPENSE`
- **AND** o alerta não contém `"40.00"` nem dado de B

#### Scenario: Dono registra gasto
- **GIVEN** a verba de A é compartilhada com B
- **WHEN** A registra um gasto nela
- **THEN** nenhum alerta `SHARED_EXPENSE` é criado

#### Scenario: Participante não recebe alerta
- **GIVEN** B participa da verba de A
- **WHEN** B registra um gasto nela
- **THEN** B não recebe alerta `SHARED_EXPENSE`

### Requirement: Alertas visíveis só ao dono
A listagem e a dispensa de alertas SHALL alcançar só os alertas da pessoa
autenticada. Dispensar alerta de outra conta SHALL responder como não
encontrado.

#### Scenario: Terceiro tenta dispensar
- **GIVEN** o alerta X pertence à conta A
- **WHEN** a conta B pede a dispensa de X
- **THEN** a API responde como não encontrado
- **AND** X continua ativo para A

### Requirement: Entrega por push com consentimento
Ao criar um alerta, o sistema SHALL enviar push ao dono somente se o tipo
correspondente estiver ativo nas preferências. A resolução de alerta MUST NOT
gerar push.

#### Scenario: Tipo ativo
- **GIVEN** A ativou `SHARED_EXPENSE` e tem assinatura Web Push
- **WHEN** um participante registra gasto na verba de A
- **THEN** A recebe push `SHARED_EXPENSE` com o tipo da verba

#### Scenario: Tipo desligado
- **GIVEN** A desligou `SHARED_EXPENSE`
- **WHEN** um participante registra gasto na verba de A
- **THEN** o alerta in-app é criado
- **AND** nenhum push é enviado

### Requirement: Alerta não depende do sucesso do push
O gasto e o alerta in-app SHALL ser confirmados mesmo que o envio do push
falhe. Um envio que falhou por motivo temporário SHALL ser tentado de novo após
reinício do backend.

#### Scenario: Push service fora do ar
- **GIVEN** o push service está indisponível
- **WHEN** um gasto deixa a verba negativa
- **THEN** o gasto é salvo e o alerta in-app existe
- **AND** o envio pendente é retomado depois
