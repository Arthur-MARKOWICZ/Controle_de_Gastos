# stock-lots Specification

## Purpose

Permite que o titular registre à mão cada compra de ativo da B3 (ação, FII,
ETF, unit ou BDR) como um lote, para acompanhar a carteira separada do
orçamento de verbas.

## Requirements

### Requirement: Titular registers a B3 stock lot
A API SHALL permitir ao titular autenticado cadastrar um lote com ticker,
quantidade, preço unitário em BRL e data da compra. A resposta 201 MUST trazer
o identificador do lote e o custo de aquisição, igual a `quantidade × preço
unitário`, como dinheiro com duas casas. Cada compra é um lote próprio, mesmo
quando o ticker se repete.

#### Scenario: Titular registers a purchase
- **GIVEN** um titular autenticado
- **WHEN** ele cadastra 100 unidades de "PETR4" a "38.47" compradas em 2026-11-03
- **THEN** a API responde 201 com ticker "PETR4", quantidade 100, preço unitário "38.47", custo de aquisição "3847.00" e data "2026-11-03"

#### Scenario: Second purchase of the same ticker creates another lot
- **GIVEN** o titular já tem um lote de "PETR4"
- **WHEN** ele cadastra outra compra de "PETR4"
- **THEN** a API responde 201 com um novo identificador e a listagem passa a ter dois lotes de "PETR4"

#### Scenario: Registering a lot does not touch the budget
- **GIVEN** um titular com verbas e lançamentos
- **WHEN** ele cadastra um lote
- **THEN** nenhum lançamento é criado e nenhum saldo de verba muda

### Requirement: Ticker follows the B3 format
O ticker MUST ser normalizado (sem espaços nas pontas, em maiúsculas e sem o
sufixo `F` do mercado fracionário) e, depois disso, MUST ter quatro letras
seguidas de um ou dois dígitos. A API MUST NOT verificar se o ticker é
negociado na B3.

#### Scenario: Lowercase and fractional tickers are normalized
- **WHEN** o titular cadastra um lote com ticker " petr4f "
- **THEN** a API responde 201 e o lote é gravado com ticker "PETR4"

#### Scenario: Units, ETFs, FIIs and BDRs are accepted
- **WHEN** o titular cadastra lotes com os tickers "TAEE11", "BOVA11", "MXRF11" e "AAPL34"
- **THEN** a API responde 201 em todos

#### Scenario: Malformed ticker is rejected
- **WHEN** o titular envia o ticker "PETR", "PETR123", "PE4" ou "PETR-4"
- **THEN** a API responde 400 sem criar o lote

### Requirement: Lot values are validated
A quantidade MUST ser um inteiro maior que zero. O preço unitário MUST ser
dinheiro BRL maior que zero, com exatamente duas casas. A data da compra MUST
ser uma data ISO que não seja posterior à data atual em America/Sao_Paulo.
Valores inválidos MUST resultar em 400, sem criar o lote.

#### Scenario: Zero or fractional quantity is rejected
- **WHEN** o titular envia quantidade 0, -5 ou 1.5
- **THEN** a API responde 400 sem criar o lote

#### Scenario: Non-positive price is rejected
- **WHEN** o titular envia preço unitário "0.00" ou "-1.00"
- **THEN** a API responde 400 sem criar o lote

#### Scenario: Price with more than two decimals is rejected
- **WHEN** o titular envia preço unitário "38.475"
- **THEN** a API responde 400 sem criar o lote

#### Scenario: Future purchase date is rejected
- **GIVEN** a data atual 2026-11-26 em America/Sao_Paulo
- **WHEN** o titular envia a data da compra 2026-11-27
- **THEN** a API responde 400 sem criar o lote

#### Scenario: Purchase made today is accepted
- **GIVEN** a data atual 2026-11-26 em America/Sao_Paulo
- **WHEN** o titular envia a data da compra 2026-11-26
- **THEN** a API responde 201

### Requirement: Titular lists own lots
A API SHALL listar os lotes do titular autenticado ordenados pela data da
compra (mais antiga primeiro) e, em caso de empate, pelo ticker. Cada item
MUST trazer identificador, ticker, quantidade, preço unitário, custo de
aquisição e data da compra. Sem lotes, a resposta MUST ser uma lista vazia.

#### Scenario: Lots are listed in purchase order
- **GIVEN** o titular tem "VALE3" comprada em 2026-10-01 e "ITUB4" e "BBAS3" compradas em 2026-09-15
- **WHEN** ele lista os lotes
- **THEN** a API responde 200 com "BBAS3", "ITUB4" e "VALE3", nessa ordem

#### Scenario: Titular without lots gets an empty list
- **WHEN** um titular sem lotes lista os lotes
- **THEN** a API responde 200 com uma lista vazia

### Requirement: Titular deletes a lot
A API SHALL permitir ao titular excluir um dos seus lotes. A exclusão é
definitiva: o lote deixa de aparecer na listagem e não pode ser recuperado. Não
existe edição de lote.

#### Scenario: Titular deletes a lot
- **GIVEN** o titular tem um lote de "PETR4"
- **WHEN** ele exclui esse lote
- **THEN** a API responde 204 e o lote não aparece mais na listagem

#### Scenario: Deleting an unknown lot
- **WHEN** o titular exclui um identificador de lote que não existe
- **THEN** a API responde 404

### Requirement: Lots are visible only to their titular
Lotes MUST ser acessíveis somente ao titular que os cadastrou. O módulo de
investimentos não tem participantes nem compartilhamento. Outro usuário MUST
receber 404, sem revelar que o lote existe, e chamadas sem autenticação MUST
receber 401.

#### Scenario: Another user does not see the titular's lots
- **GIVEN** o titular A tem um lote de "PETR4"
- **WHEN** o usuário B lista os lotes
- **THEN** a lista de B não contém o lote de A

#### Scenario: Another user cannot delete the titular's lot
- **GIVEN** o titular A tem um lote de "PETR4"
- **WHEN** o usuário B tenta excluir esse lote
- **THEN** a API responde 404 e o lote de A continua existindo

#### Scenario: Envelope participant gains no access
- **GIVEN** o usuário B é participante de uma verba do titular A, e A tem um lote
- **WHEN** B lista ou tenta excluir o lote de A
- **THEN** B não vê o lote e a exclusão responde 404

#### Scenario: Unauthenticated access is refused
- **WHEN** uma chamada sem autenticação cria, lista ou exclui lotes
- **THEN** a API responde 401

### Requirement: Lots follow the titular's data rights
A exportação de dados do titular MUST incluir os seus lotes, com ticker,
quantidade, preço unitário, moeda, data da compra e instante de criação. A
exclusão da conta MUST apagar todos os lotes do titular. Logs MUST NOT
registrar ticker, quantidade ou valores dos lotes.

#### Scenario: Export includes lots
- **GIVEN** o titular tem dois lotes
- **WHEN** ele exporta seus dados
- **THEN** a exportação traz os dois lotes com preço unitário como string decimal com duas casas e moeda "BRL"

#### Scenario: Account deletion erases lots
- **GIVEN** o titular tem lotes cadastrados
- **WHEN** ele exclui a conta
- **THEN** nenhum lote dele permanece no sistema
