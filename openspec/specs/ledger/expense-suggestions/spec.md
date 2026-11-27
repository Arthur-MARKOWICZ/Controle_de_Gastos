# expense-suggestions Specification

## Purpose

Devolve às pessoas autenticadas sugestões de verba e valor para acelerar o
lançamento de gasto, com base na frequência do próprio histórico, sem ML.

## Requirements

### Requirement: Authenticated suggestion query
A API SHALL expor uma consulta autenticada de sugestões de gasto. A resposta
MUST listar até três verbas em que a pessoa pode registrar `EXPENSE`, ordenadas
da mais para a menos provável no momento da consulta, cada uma com um
`suggestedAmount` em BRL (string decimal com duas casas) ou nulo quando não
houver valor confiável.

#### Scenario: Owner with recurring weekday pattern
- **GIVEN** o titular registrou nos últimos 90 dias vários gastos em "Almoço"
  às terças perto do meio-dia, no valor "32.50"
- **AND** a consulta ocorre numa terça perto do meio-dia em America/Sao_Paulo
- **WHEN** ele pede as sugestões de lançamento
- **THEN** a primeira verba é "Almoço"
- **AND** o suggestedAmount de "Almoço" é "32.50"

#### Scenario: Participant only sees spendable shared envelopes
- **GIVEN** a participante pode gastar na verba compartilhada "Casa" e não tem
  outras verbas
- **AND** o histórico dela em "Casa" justifica uma sugestão
- **WHEN** ela pede as sugestões de lançamento
- **THEN** a lista contém só "Casa"
- **AND** não inclui verbas de outros titulares às quais ela não tem acesso

#### Scenario: Third party gets empty list for foreign pattern
- **GIVEN** um terceiro sem verbas próprias nem compartilhadas
- **WHEN** ele pede as sugestões de lançamento
- **THEN** a API responde 200 com lista vazia

### Requirement: Frequency rule without machine learning
A ordenação SHALL usar apenas contagens do histórico de gastos `EXPENSE` ativos
do usuário autenticado como autor, em uma janela recente fixa, ponderando dia da
semana da data de negócio e faixa de horário do instante de criação em
America/Sao_Paulo. A API MUST NOT usar modelo treinado, serviço externo de
ranking nem cache da resposta.

#### Scenario: Same envelope preferred on matching weekday and hour
- **GIVEN** o titular tem 5 gastos em "Uber" às sextas entre 18h e 19h e 2
  gastos em "Mercado" no mesmo horário
- **WHEN** ele consulta numa sexta às 18h30 America/Sao_Paulo
- **THEN** "Uber" aparece antes de "Mercado"

#### Scenario: Fallback when no hour match
- **GIVEN** o titular só tem histórico de "Café" em manhãs de segunda
- **WHEN** ele consulta numa segunda à noite
- **THEN** "Café" ainda pode aparecer pela frequência no dia da semana
- **AND** a lista não falha com erro

### Requirement: Suggested amount is the mode of recent amounts
Para cada verba ranqueada, o `suggestedAmount` SHALL ser o valor que mais se
repetiu nos gastos `EXPENSE` ativos do autor naquela verba dentro da janela
considerada. Empate MUST escolher o valor mais recente. Sem ocorrências
suficientes, `suggestedAmount` MUST ser nulo (a pessoa digita o valor).

#### Scenario: Modal amount wins
- **GIVEN** o titular tem em "Café" os valores "8.00", "8.00", "8.00" e "12.00"
- **WHEN** as sugestões incluem "Café"
- **THEN** suggestedAmount é "8.00"

#### Scenario: Tie broken by most recent
- **GIVEN** o titular tem em "Padaria" dois gastos "10.00" e dois "15.00", sendo
  "15.00" o mais recente
- **WHEN** as sugestões incluem "Padaria"
- **THEN** suggestedAmount é "15.00"

#### Scenario: Insufficient history yields null amount
- **GIVEN** o titular tem um único gasto em "Farmácia"
- **WHEN** as sugestões incluem "Farmácia" só pela contagem mínima de ranqueamento
  de verba, se aplicável
- **THEN** suggestedAmount de "Farmácia" é nulo ou a verba só entra quando a
  regra de mínimo de ocorrências for atendida de forma consistente documentada
  no design

### Requirement: Only spendable non-archived envelopes
A lista MUST omitir verbas arquivadas e verbas em que a pessoa não pode
registrar gasto (por exemplo, meta de aporte em que só o dono aporta e o fluxo
de gasto não se aplica). Terceiros MUST NOT descobrir existência de verbas
alheias pela sugestão.

#### Scenario: Archived envelope excluded
- **GIVEN** a verba "Viagem" do titular está arquivada e dominava o histórico
- **WHEN** ele pede sugestões
- **THEN** "Viagem" não aparece na lista

### Requirement: Suggestion query does not log financial detail
A API MUST NOT registrar em logs o corpo da resposta, valores sugeridos,
identificadores de verba em massa ou descrições de gastos ao atender a consulta.

#### Scenario: Successful query leaves no amount in logs
- **WHEN** o titular obtém sugestões com suggestedAmount "32.50"
- **THEN** os logs da requisição não contêm "32.50" nem a descrição dos gastos
  usados no cálculo
