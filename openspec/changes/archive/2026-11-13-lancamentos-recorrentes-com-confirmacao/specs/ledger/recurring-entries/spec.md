# Spec Delta

## Purpose

Permite que o proprietário de uma verba cadastre lançamentos mensais previsíveis
(aluguel, assinaturas, aporte) e receba, a cada competência, uma pendência para
confirmar, ajustar ou descartar, sem que nada entre no saldo antes da confirmação.

## ADDED Requirements

### Requirement: Owner manages recurring entries of an envelope
A API SHALL permitir ao proprietário da verba criar, listar, editar, desativar,
reativar e excluir recorrências mensais com tipo (`EXPENSE` ou `CONTRIBUTION`),
valor sugerido positivo em BRL com duas casas, dia do mês entre 1 e 31 e
descrição opcional de até 140 caracteres.

#### Scenario: Owner creates a monthly rent recurrence
- **GIVEN** o proprietário da verba "Moradia"
- **WHEN** ele cria uma recorrência `EXPENSE` de "1800.00" no dia 5 com descrição "Aluguel"
- **THEN** a API responde 201 com a recorrência ativa, valor "1800.00" e dia 5

#### Scenario: Owner creates a monthly contribution recurrence
- **GIVEN** o proprietário de uma meta de aporte "Reserva"
- **WHEN** ele cria uma recorrência `CONTRIBUTION` de "500.00" no dia 10
- **THEN** a API responde 201 com a recorrência ativa

#### Scenario: Invalid day or amount is rejected
- **WHEN** o proprietário envia dia 0, dia 32, valor "0.00" ou valor negativo
- **THEN** a API responde 400 sem criar a recorrência

#### Scenario: Archived envelope does not accept new recurrences
- **GIVEN** uma verba arquivada
- **WHEN** o proprietário tenta criar uma recorrência nela
- **THEN** a API recusa a criação

### Requirement: Recurring entries are visible only to the envelope owner
Recorrências e suas ocorrências MUST ser acessíveis somente ao proprietário da
verba. Participantes MUST receber 403 e terceiros MUST receber 404, sem revelar
a existência da verba ou da recorrência.

#### Scenario: Participant cannot see or create recurrences
- **GIVEN** um participante da verba compartilhada "Casa"
- **WHEN** ele lista ou tenta criar recorrências em "Casa"
- **THEN** a API responde 403

#### Scenario: Stranger cannot reach recurrences
- **GIVEN** um usuário sem relação com a verba "Casa"
- **WHEN** ele lista recorrências de "Casa" ou tenta confirmar uma ocorrência dela
- **THEN** a API responde 404

#### Scenario: Participant does not see owner's pending occurrences
- **GIVEN** uma ocorrência pendente na verba compartilhada "Casa"
- **WHEN** um participante lista suas pendências
- **THEN** a ocorrência de "Casa" não aparece

### Requirement: One pending occurrence per recurrence and competence
O sistema SHALL gerar, uma vez por dia, uma ocorrência pendente para cada
recorrência ativa cuja data prevista no mês corrente (America/Sao_Paulo) já
chegou. MUST existir no máximo uma ocorrência por recorrência e competência,
mesmo com execuções repetidas. Competências anteriores à criação da
recorrência, ou cujo dia previsto já passou na criação, MUST NOT ser geradas.

#### Scenario: Occurrence is generated on the due day
- **GIVEN** uma recorrência ativa de "1800.00" no dia 5
- **WHEN** a geração roda em 2026-12-05
- **THEN** existe uma ocorrência pendente da competência 2026-12 com data prevista 2026-12-05 e valor sugerido "1800.00"

#### Scenario: Repeated generation does not duplicate
- **GIVEN** a ocorrência da competência 2026-12 já gerada
- **WHEN** a geração roda novamente no mesmo mês, uma ou mais vezes
- **THEN** continua existindo uma única ocorrência para essa recorrência em 2026-12

#### Scenario: Discarded occurrence is not regenerated
- **GIVEN** a ocorrência da competência 2026-12 descartada pelo proprietário
- **WHEN** a geração roda novamente em dezembro
- **THEN** nenhuma nova ocorrência de 2026-12 é criada

#### Scenario: Missed days are recovered within the month
- **GIVEN** uma recorrência no dia 5 e nenhuma geração entre 2026-12-04 e 2026-12-08
- **WHEN** a geração roda em 2026-12-09
- **THEN** a ocorrência de 2026-12 é criada com data prevista 2026-12-05

#### Scenario: Recurrence created after the due day starts next month
- **GIVEN** uma recorrência criada em 2026-12-20 com dia 5
- **WHEN** a geração roda em 2026-12-21
- **THEN** nenhuma ocorrência de 2026-12 nem de meses anteriores é criada
- **AND** a primeira ocorrência é a de 2027-01

#### Scenario: Inactive recurrence generates nothing
- **GIVEN** uma recorrência desativada
- **WHEN** a geração roda após o dia previsto
- **THEN** nenhuma ocorrência é criada para ela

### Requirement: Due day adapts to short months
Quando o dia configurado não existir no mês, a data prevista SHALL ser o último
dia daquele mês.

#### Scenario: Day 31 in February
- **GIVEN** uma recorrência ativa no dia 31
- **WHEN** a geração roda em 2027-02-28
- **THEN** a ocorrência de 2027-02 tem data prevista 2027-02-28

#### Scenario: Day 31 in April
- **GIVEN** uma recorrência ativa no dia 31
- **WHEN** a geração roda em 2027-04-30
- **THEN** a ocorrência de 2027-04 tem data prevista 2027-04-30

### Requirement: Pending occurrences do not affect balances
Ocorrências pendentes ou descartadas MUST NOT alterar saldo, disponível,
progresso de meta, resumo mensal, relatórios ou alertas de saldo.

#### Scenario: Pending rent leaves balance unchanged
- **GIVEN** a verba "Moradia" com disponível "1800.00" e uma ocorrência pendente de "1800.00"
- **WHEN** o proprietário consulta o saldo da verba
- **THEN** o disponível continua "1800.00"

### Requirement: Owner confirms a pending occurrence
O proprietário SHALL confirmar uma ocorrência pendente informando valor e data,
que por padrão são o valor sugerido e a data prevista. A confirmação MUST criar
exatamente um lançamento do tipo da recorrência, com as mesmas regras de um
lançamento manual (saldo, alertas, meta e auditoria).

#### Scenario: Confirm with the suggested amount
- **GIVEN** uma ocorrência pendente de "1800.00" com data prevista 2026-12-05
- **WHEN** o proprietário confirma sem ajustes
- **THEN** um lançamento `EXPENSE` de "1800.00" em 2026-12-05 é criado na verba
- **AND** a ocorrência passa a confirmada e referencia esse lançamento

#### Scenario: Confirm with an adjusted amount
- **GIVEN** uma ocorrência pendente de energia com valor sugerido "200.00"
- **WHEN** o proprietário confirma com valor "237.45"
- **THEN** o lançamento criado tem valor "237.45"
- **AND** a recorrência mantém o valor sugerido "200.00"

#### Scenario: Confirming twice does not duplicate
- **GIVEN** uma ocorrência já confirmada
- **WHEN** o proprietário tenta confirmá-la novamente
- **THEN** a API responde 409 e nenhum lançamento novo é criado

#### Scenario: Future date is rejected
- **WHEN** o proprietário confirma informando uma data posterior ao dia seguinte de hoje
- **THEN** a API responde 400 e a ocorrência continua pendente

### Requirement: Owner discards a pending occurrence
O proprietário SHALL descartar uma ocorrência pendente; o descarte MUST NOT
criar lançamento e MUST impedir confirmação posterior da mesma ocorrência.

#### Scenario: Discard a cancelled subscription
- **GIVEN** uma ocorrência pendente de "39.90" da recorrência "Streaming"
- **WHEN** o proprietário a descarta
- **THEN** nenhum lançamento é criado e a ocorrência passa a descartada

#### Scenario: Discarded occurrence cannot be confirmed
- **GIVEN** uma ocorrência descartada
- **WHEN** o proprietário tenta confirmá-la
- **THEN** a API responde 409

### Requirement: Pending and overdue occurrences are listed
A API SHALL listar ao usuário autenticado as ocorrências pendentes das verbas
de que é proprietário, separando as da competência corrente das vencidas
(competências anteriores), com verba, descrição, valor sugerido e data prevista.

#### Scenario: Overdue occurrence stays listed
- **GIVEN** uma ocorrência pendente da competência 2026-11 não tratada
- **WHEN** o proprietário lista pendências em 2026-12-02
- **THEN** a ocorrência aparece como vencida, com data prevista em 2026-11

#### Scenario: Confirming an overdue occurrence keeps its original date
- **GIVEN** uma ocorrência vencida com data prevista 2026-11-05
- **WHEN** o proprietário a confirma sem ajustar a data
- **THEN** o lançamento é criado em 2026-11-05, na competência 2026-11

### Requirement: Recurrence changes do not rewrite generated occurrences
Editar, desativar ou excluir uma recorrência MUST NOT alterar ocorrências já
confirmadas; ocorrências pendentes mantêm o valor sugerido da geração, e a
exclusão da recorrência MUST descartar suas pendentes.

#### Scenario: Editing the amount keeps the pending suggestion
- **GIVEN** uma ocorrência pendente gerada com "1800.00"
- **WHEN** o proprietário altera a recorrência para "1900.00"
- **THEN** a ocorrência pendente continua sugerindo "1800.00"
- **AND** a próxima competência é gerada com "1900.00"

#### Scenario: Deleting a recurrence keeps confirmed entries
- **GIVEN** uma recorrência com uma ocorrência confirmada e outra pendente
- **WHEN** o proprietário exclui a recorrência
- **THEN** o lançamento confirmado permanece no ledger
- **AND** a ocorrência pendente deixa de ser listada

### Requirement: Archiving an envelope stops its recurrences
Arquivar uma verba MUST desativar suas recorrências e descartar as ocorrências
pendentes dela.

#### Scenario: Archive stops generation
- **GIVEN** a verba "Streaming" com uma recorrência ativa e uma ocorrência pendente
- **WHEN** o proprietário arquiva a verba
- **THEN** a recorrência fica inativa, a pendente deixa de ser listada e nenhuma nova ocorrência é gerada

### Requirement: Recurring data follows titular rights
A exportação de dados do titular SHALL incluir suas recorrências e ocorrências,
e a exclusão do titular MUST removê-las junto com as verbas de que é
proprietário. Logs da geração MUST NOT conter valores, descrições ou e-mails.

#### Scenario: Export includes recurrences
- **GIVEN** um titular com uma recorrência "Aluguel" de "1800.00"
- **WHEN** ele exporta seus dados
- **THEN** o arquivo contém a recorrência com valor "1800.00" e suas ocorrências
