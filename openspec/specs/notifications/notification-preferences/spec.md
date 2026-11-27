# notification-preferences Specification

## Purpose

Guarda o consentimento da pessoa para cada tipo de notificação e o horário do
lembrete diário, e informa se já houve lançamento no dia, como fonte única para
web, Android e desktop.

## Requirements

### Requirement: Opt-in por tipo de notificação
A API SHALL manter, por conta, uma preferência booleana para cada tipo:
`DAILY_REMINDER`, `NEGATIVE_BALANCE` e `SHARED_EXPENSE`. Toda preferência SHALL
começar desligada. Nenhum aviso de um tipo desligado SHALL ser entregue por push
ou notificação do sistema.

#### Scenario: Conta nova sem consentimento
- **GIVEN** uma conta recém-criada
- **WHEN** a pessoa consulta suas preferências de notificação
- **THEN** os três tipos aparecem desligados

#### Scenario: Ativar um tipo
- **GIVEN** uma conta autenticada com `NEGATIVE_BALANCE` desligado
- **WHEN** a pessoa ativa `NEGATIVE_BALANCE`
- **THEN** a preferência fica ligada
- **AND** os demais tipos não mudam

### Requirement: Horário do lembrete diário
A API SHALL guardar o horário do lembrete diário como hora e minuto
(`HH:mm`, minutos múltiplos de 15) no fuso `America/Sao_Paulo`. O padrão SHALL
ser `20:00`. Horário fora do formato ou fora do passo de 15 minutos SHALL ser
recusado com erro de validação.

#### Scenario: Horário válido
- **WHEN** a pessoa define o lembrete para `21:30`
- **THEN** a API guarda `21:30`
- **AND** devolve o horário em consultas seguintes

#### Scenario: Horário fora do passo
- **WHEN** a pessoa define o lembrete para `21:10`
- **THEN** a API recusa com erro de validação
- **AND** mantém o horário anterior

### Requirement: Preferências são privadas da conta
Cada pessoa SHALL ler e alterar só as próprias preferências. A API SHALL
identificar a conta pela sessão, sem aceitar identificador de outra pessoa.
Requisição sem sessão SHALL ser recusada como não autenticada.

#### Scenario: Sem sessão
- **WHEN** uma requisição sem sessão consulta preferências de notificação
- **THEN** a API responde como não autenticada

#### Scenario: Participante não vê preferências do dono
- **GIVEN** a pessoa B participa de uma verba da pessoa A
- **WHEN** B consulta preferências de notificação
- **THEN** recebe apenas as próprias preferências, sem dado de A

### Requirement: Consulta "lançou hoje"
A API SHALL informar, para a pessoa autenticada, se existe ao menos um gasto não
excluído de sua autoria com data igual à data atual em `America/Sao_Paulo`. A
resposta SHALL trazer só a data considerada e um booleano, sem valores, verbas ou
descrições.

#### Scenario: Sem gasto hoje
- **GIVEN** a pessoa não registrou gasto com a data de hoje
- **WHEN** consulta "lançou hoje"
- **THEN** a resposta indica `false` e a data de hoje

#### Scenario: Gasto registrado hoje
- **GIVEN** a pessoa registrou hoje um gasto de `"12.50"`
- **WHEN** consulta "lançou hoje"
- **THEN** a resposta indica `true`
- **AND** não contém o valor nem a verba

#### Scenario: Gasto excluído não conta
- **GIVEN** o único gasto de hoje foi excluído logicamente
- **WHEN** a pessoa consulta "lançou hoje"
- **THEN** a resposta indica `false`

#### Scenario: Gasto de outra pessoa não conta
- **GIVEN** a pessoa A é dona de uma verba compartilhada com B
- **AND** só B registrou gasto hoje nessa verba
- **WHEN** A consulta "lançou hoje"
- **THEN** a resposta indica `false`

### Requirement: Preferências no ciclo de vida da conta
As preferências SHALL entrar na exportação do titular e SHALL ser apagadas com a
exclusão da conta.

#### Scenario: Exportação
- **GIVEN** a pessoa ativou `DAILY_REMINDER` às `21:00`
- **WHEN** solicita a exportação do titular
- **THEN** o arquivo contém os tipos ativos e o horário

#### Scenario: Exclusão da conta
- **WHEN** a conta é excluída
- **THEN** as preferências de notificação dela deixam de existir
