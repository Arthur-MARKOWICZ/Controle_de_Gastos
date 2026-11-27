# cdb-positions Specification

## Purpose

Permite que o titular registre à mão posições de CDB prefixado e veja o saldo
estimado pelo rendimento contratado, separado do orçamento de verbas.

## Requirements

### Requirement: Titular registers a prefixed CDB position
A API SHALL permitir ao titular autenticado cadastrar um CDB com principal em
BRL, taxa anual prefixada em decimal, data de início e data de vencimento. A
resposta 201 MUST trazer o identificador e o `estimatedValue` calculado na
data atual em America/Sao_Paulo. Cada cadastro é uma posição própria.

#### Scenario: Titular registers a CDB
- **GIVEN** a data atual 2026-11-26 em America/Sao_Paulo
- **WHEN** ele cadastra principal "10000.00", taxa "0.1250", início 2025-11-26 e vencimento 2027-11-26
- **THEN** a API responde 201 com esses campos e um `estimatedValue` igual a principal × (1 + 0.1250) ^ (365/365), arredondado a duas casas com HALF_EVEN ("11250.00")

#### Scenario: Second CDB creates another position
- **GIVEN** o titular já tem um CDB
- **WHEN** ele cadastra outro CDB
- **THEN** a API responde 201 com um novo identificador e a listagem passa a ter duas posições

#### Scenario: Registering a CDB does not touch the budget
- **GIVEN** um titular com verbas e lançamentos
- **WHEN** ele cadastra um CDB
- **THEN** nenhum lançamento é criado e nenhum saldo de verba muda

### Requirement: Annual rate is a positive decimal with four places
A taxa anual MUST ser enviada e devolvida como string decimal positiva com
exatamente quatro casas (`0.1250` equivale a 12,50% a.a.). A API MUST NOT
aceitar percentual (`12.50`), escala diferente de quatro casas, zero ou
valor negativo.

#### Scenario: Rate follows ADR decimal form
- **WHEN** o titular cadastra taxa "0.1250"
- **THEN** a API responde 201 e a posição guarda "0.1250"

#### Scenario: Percent-style or wrong scale rate is rejected
- **WHEN** o titular envia taxa "12.50", "0.125", "0.12500", "0.0000" ou "-0.0100"
- **THEN** a API responde 400 sem criar a posição

### Requirement: Principal and dates are validated
O principal MUST ser dinheiro BRL maior que zero, com exatamente duas casas.
A data de início MUST ser uma data ISO não posterior a hoje em
America/Sao_Paulo. A data de vencimento MUST ser posterior à data de início.
Valores inválidos MUST resultar em 400, sem criar a posição.

#### Scenario: Non-positive or over-scaled principal is rejected
- **WHEN** o titular envia principal "0.00", "-1.00" ou "1000.001"
- **THEN** a API responde 400 sem criar a posição

#### Scenario: Future start date is rejected
- **GIVEN** a data atual 2026-11-26 em America/Sao_Paulo
- **WHEN** o titular envia início 2026-11-27
- **THEN** a API responde 400 sem criar a posição

#### Scenario: Start date today is accepted
- **GIVEN** a data atual 2026-11-26 em America/Sao_Paulo
- **WHEN** o titular envia início 2026-11-26 e vencimento 2027-11-26
- **THEN** a API responde 201 e o `estimatedValue` é igual ao principal

#### Scenario: Maturity on or before start is rejected
- **WHEN** o titular envia início 2025-01-01 e vencimento 2025-01-01 ou 2024-12-31
- **THEN** a API responde 400 sem criar a posição

### Requirement: Estimated balance follows the prefixed yield formula
O `estimatedValue` MUST ser recalculado a cada leitura: dias corridos entre
início e `min(hoje, vencimento)` em America/Sao_Paulo; fator
`(1 + taxa) ^ (dias / 365)`; resultado `principal × fator` arredondado a duas
casas com HALF_EVEN só no final. Nenhuma etapa MUST usar `double`. O valor é
bruto (sem IR nem IOF). No vencimento, o estimado MUST parar de crescer.

#### Scenario: Estimate grows for a full year of accrual
- **GIVEN** a data atual 2026-11-26 em America/Sao_Paulo e um CDB com principal "10000.00", taxa "0.1250", início 2025-11-26 e vencimento 2027-11-26
- **WHEN** o titular lista as posições
- **THEN** o item traz `estimatedValue` "11250.00"

#### Scenario: Estimate freezes at maturity
- **GIVEN** a data atual 2028-01-15 em America/Sao_Paulo e um CDB com principal "10000.00", taxa "0.1000", início 2025-01-15 e vencimento 2027-01-15
- **WHEN** o titular lista as posições
- **THEN** o `estimatedValue` é igual ao valor no vencimento (dois anos de 365 dias) e não usa a data 2028-01-15 no expoente

#### Scenario: Estimate on start date equals principal
- **GIVEN** a data atual 2026-11-26 e um CDB com início 2026-11-26
- **WHEN** o titular lista as posições
- **THEN** o `estimatedValue` é igual ao principal

### Requirement: Titular lists own CDB positions
A API SHALL listar as posições do titular autenticado ordenadas pela data de
início (mais antiga primeiro) e, em empate, pelo instante de criação. Cada
item MUST trazer identificador, principal, taxa, datas, `estimatedValue` e
instante de criação. Sem posições, a resposta MUST ser uma lista vazia.

#### Scenario: Positions are listed in start-date order
- **GIVEN** o titular tem um CDB com início 2026-10-01 e outro com início 2026-09-15
- **WHEN** ele lista as posições
- **THEN** a API responde 200 com o de 2026-09-15 antes do de 2026-10-01

#### Scenario: Titular without positions gets an empty list
- **WHEN** um titular sem CDBs lista as posições
- **THEN** a API responde 200 com uma lista vazia

### Requirement: Titular deletes a CDB position
A API SHALL permitir ao titular excluir uma das suas posições. A exclusão é
definitiva. Não existe edição de posição.

#### Scenario: Titular deletes a position
- **GIVEN** o titular tem um CDB
- **WHEN** ele exclui essa posição
- **THEN** a API responde 204 e a posição não aparece mais na listagem

#### Scenario: Deleting an unknown position
- **WHEN** o titular exclui um identificador que não existe
- **THEN** a API responde 404

### Requirement: CDB positions are visible only to their titular
Posições MUST ser acessíveis somente ao titular que as cadastrou. Não há
participantes nem compartilhamento. Outro usuário MUST receber 404, sem
revelar que a posição existe, e chamadas sem autenticação MUST receber 401.

#### Scenario: Another user does not see the titular's positions
- **GIVEN** o titular A tem um CDB
- **WHEN** o usuário B lista as posições
- **THEN** a lista de B não contém a posição de A

#### Scenario: Another user cannot delete the titular's position
- **GIVEN** o titular A tem um CDB
- **WHEN** o usuário B tenta excluir essa posição
- **THEN** a API responde 404 e a posição de A continua existindo

#### Scenario: Envelope participant gains no access
- **GIVEN** o usuário B é participante de uma verba do titular A, e A tem um CDB
- **WHEN** B lista ou tenta excluir a posição de A
- **THEN** B não vê a posição e a exclusão responde 404

#### Scenario: Unauthenticated access is refused
- **WHEN** uma chamada sem autenticação cria, lista ou exclui CDBs
- **THEN** a API responde 401

### Requirement: CDB positions follow the titular's data rights
A exportação de dados do titular MUST incluir as suas posições de CDB, com
principal, moeda, taxa, datas de início e vencimento e instante de criação,
sem o `estimatedValue` derivado. A exclusão da conta MUST apagar todas as
posições do titular. Logs MUST NOT registrar principal, taxa ou valores
estimados.

#### Scenario: Export includes CDB positions
- **GIVEN** o titular tem dois CDBs
- **WHEN** ele exporta seus dados
- **THEN** a exportação traz as duas posições com principal como string decimal com duas casas, moeda "BRL" e taxa com quatro casas, sem campo de valor estimado

#### Scenario: Account deletion erases CDB positions
- **GIVEN** o titular tem CDBs cadastrados
- **WHEN** ele exclui a conta
- **THEN** nenhuma posição dele permanece no sistema
