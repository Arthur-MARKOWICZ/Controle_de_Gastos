# web-push-delivery Specification

## Purpose

Entrega avisos ao navegador da pessoa por Web Push padrão (VAPID), sem provedor
proprietário, com payload mínimo e descarte de assinaturas que deixaram de
valer.

## Requirements

### Requirement: Chave pública VAPID
A API SHALL expor a chave pública VAPID para que o navegador crie a assinatura.
A chave privada MUST ficar só no runtime do backend e nunca SHALL aparecer em
respostas, logs ou no repositório.

#### Scenario: Consulta da chave pública
- **WHEN** um cliente autenticado consulta a chave pública de Web Push
- **THEN** recebe a chave pública em Base64 URL-safe
- **AND** a resposta não contém a chave privada

### Requirement: Registro de assinatura
A pessoa autenticada SHALL registrar a assinatura do navegador (endpoint e
chaves `p256dh` e `auth`). Registrar de novo o mesmo endpoint SHALL atualizar a
assinatura existente, sem duplicar. O endpoint SHALL usar `https`.

#### Scenario: Primeiro registro
- **WHEN** a pessoa registra uma assinatura válida
- **THEN** a API guarda a assinatura vinculada à conta dela

#### Scenario: Mesmo endpoint de novo
- **GIVEN** um endpoint já registrado pela pessoa
- **WHEN** ela registra o mesmo endpoint com chaves novas
- **THEN** existe uma única assinatura com as chaves novas

#### Scenario: Endpoint sem https
- **WHEN** a pessoa registra um endpoint `http://`
- **THEN** a API recusa com erro de validação

#### Scenario: Endpoint registrado por outra conta
- **GIVEN** o endpoint E pertence à conta A
- **WHEN** a conta B registra o endpoint E
- **THEN** a assinatura passa a pertencer só a B
- **AND** A deixa de receber avisos nesse endpoint

### Requirement: Remoção de assinatura
A pessoa SHALL remover uma assinatura própria pelo endpoint. Remover endpoint
inexistente ou de outra conta SHALL responder como sucesso sem efeito, sem
revelar a quem pertence. O logout SHALL remover a assinatura do navegador que
está saindo, quando o cliente informar o endpoint.

#### Scenario: Desativar neste navegador
- **GIVEN** a pessoa tem uma assinatura ativa
- **WHEN** remove a assinatura pelo endpoint
- **THEN** o navegador deixa de receber avisos

#### Scenario: Terceiro tenta remover
- **GIVEN** o endpoint E pertence à conta A
- **WHEN** a conta B pede a remoção de E
- **THEN** a API responde sucesso sem efeito
- **AND** a assinatura de A continua ativa

### Requirement: Payload mínimo
O payload de push SHALL conter só a categoria do aviso, o tipo da verba quando
houver (`LIMIT`, `GOAL`, `FIXED`, `SAVINGS_TARGET`, `ANNUAL_EXPENSE`) e o
caminho de destino no app. MUST NOT conter nome de verba, valores, descrição,
e-mail ou identificador de outra pessoa.

#### Scenario: Aviso de verba negativa
- **GIVEN** a verba "Mercado" do tipo `LIMIT` ficou com saldo `"-35.20"`
- **WHEN** o aviso é enviado por push
- **THEN** o payload indica a categoria `NEGATIVE_BALANCE` e o tipo `LIMIT`
- **AND** não contém "Mercado" nem `"-35.20"`

#### Scenario: Lembrete diário
- **WHEN** o lembrete diário é enviado por push
- **THEN** o payload indica a categoria `DAILY_REMINDER`
- **AND** não indica tipo de verba

### Requirement: Envio a todas as assinaturas da conta
Um aviso de um tipo ativo SHALL ser enviado a todas as assinaturas da conta. A
falha em uma assinatura MUST NOT impedir o envio às demais.

#### Scenario: Dois navegadores
- **GIVEN** a pessoa tem assinaturas em dois navegadores e o tipo está ativo
- **WHEN** um aviso desse tipo é gerado
- **THEN** os dois navegadores recebem o aviso

#### Scenario: Tipo desligado
- **GIVEN** a pessoa tem uma assinatura e `NEGATIVE_BALANCE` desligado
- **WHEN** uma verba dela fica negativa
- **THEN** nenhum push é enviado

### Requirement: Lembrete diário por push
No horário escolhido (`America/Sao_Paulo`), a API SHALL enviar o lembrete às
assinaturas da pessoa com `DAILY_REMINDER` ativo e sem gasto de sua autoria
na data. O lembrete SHALL sair no máximo uma vez por pessoa e por dia, mesmo
após reinício do backend.

#### Scenario: Sem lançamento no dia
- **GIVEN** `DAILY_REMINDER` ativo às `20:00` e nenhum gasto hoje
- **WHEN** chega `20:00` em `America/Sao_Paulo`
- **THEN** a pessoa recebe o lembrete por push

#### Scenario: Já lançou
- **GIVEN** `DAILY_REMINDER` ativo às `20:00` e um gasto de `"8.00"` hoje
- **WHEN** chega `20:00`
- **THEN** nenhum lembrete é enviado

#### Scenario: Reinício no horário
- **GIVEN** o lembrete de hoje já foi enviado
- **WHEN** o backend reinicia e o agendamento roda de novo no mesmo dia
- **THEN** a pessoa não recebe um segundo lembrete

### Requirement: Descarte de assinatura inválida
Quando o push service responder que a assinatura não existe mais (HTTP 404 ou
410), a API SHALL apagar a assinatura. Outras falhas MUST NOT apagar a
assinatura.

#### Scenario: Assinatura expirada
- **GIVEN** o push service responde 410 para uma assinatura
- **WHEN** a API tenta enviar um aviso
- **THEN** a assinatura é apagada

#### Scenario: Falha temporária
- **GIVEN** o push service responde 503
- **WHEN** a API tenta enviar um aviso
- **THEN** a assinatura continua registrada

### Requirement: Assinaturas no ciclo de vida da conta
As assinaturas SHALL entrar na exportação do titular sem as chaves `p256dh` e
`auth` e SHALL ser apagadas com a exclusão da conta. Endpoint e chaves MUST NOT
aparecer em logs.

#### Scenario: Exportação sem segredo
- **GIVEN** a pessoa tem uma assinatura
- **WHEN** solicita a exportação do titular
- **THEN** o arquivo contém o endpoint e a data de criação
- **AND** não contém `p256dh` nem `auth`

#### Scenario: Exclusão da conta
- **WHEN** a conta é excluída
- **THEN** as assinaturas dela deixam de existir
