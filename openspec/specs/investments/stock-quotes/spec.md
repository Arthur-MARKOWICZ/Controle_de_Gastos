# stock-quotes Specification

## Purpose

Mantém a última cotação válida de cada ativo da B3 cadastrado nos lotes e
expõe ao titular o valor atual de cada lote, sem que uma falha do provedor
afete o custo de aquisição ou a disponibilidade da carteira.

## Requirements

### Requirement: Lot listing exposes current quote and value
Cada item da listagem de lotes do titular SHALL trazer a última cotação válida
do ticker (preço em BRL com duas casas e o instante da cotação) e o valor
atual, igual a `quantidade × preço da cotação`. Quando o ticker ainda não tem
cotação, os dois campos MUST vir nulos. O custo de aquisição MUST continuar
presente e inalterado.

#### Scenario: Quoted lot shows current value
- **GIVEN** um lote do titular com 100 unidades de "PETR4" a "38.47"
- **AND** a última cotação de "PETR4" é "41.20" em 2026-11-26T17:00:00-03:00
- **WHEN** o titular lista os lotes
- **THEN** o item traz cotação "41.20" com o instante 2026-11-26T20:00:00Z, valor atual "4120.00" e custo de aquisição "3847.00"

#### Scenario: Lot without quote has null quote and value
- **GIVEN** um lote do titular de um ticker que ainda não tem cotação
- **WHEN** o titular lista os lotes
- **THEN** o item traz cotação e valor atual nulos e o custo de aquisição normal

#### Scenario: Registering a lot does not call the quote provider
- **WHEN** o titular cadastra um lote de um ticker sem cotação
- **THEN** a API responde 201 com cotação e valor atual nulos, sem consultar o provedor

#### Scenario: Another titular's lots stay hidden
- **GIVEN** o titular A e o titular B têm lotes de "PETR4" e a cotação existe
- **WHEN** B lista os lotes
- **THEN** a resposta traz só os lotes de B, com a cotação compartilhada aplicada a eles

### Requirement: Quotes refresh during trading hours
O sistema SHALL atualizar a cotação de cada ticker distinto presente em algum
lote a cada 30 minutos, de segunda a sexta, das 10h às 18h em
America/Sao_Paulo. A cotação gravada MUST guardar o instante de mercado
informado pelo provedor, não o instante da consulta.

#### Scenario: Refresh updates every held ticker
- **GIVEN** lotes de "PETR4" e "VALE3", de titulares diferentes
- **WHEN** a rodada do pregão executa e o provedor responde para os dois
- **THEN** as cotações de "PETR4" e "VALE3" passam a ter o novo preço e o instante informado pelo provedor

#### Scenario: Same ticker held by many lots is fetched once
- **GIVEN** três lotes de "PETR4"
- **WHEN** a rodada do pregão executa
- **THEN** o provedor é consultado uma única vez para "PETR4"

### Requirement: Missing quotes are fetched outside trading hours
O sistema SHALL buscar, de hora em hora e em qualquer dia, a cotação dos
tickers presentes em lotes que ainda não têm cotação gravada. Tickers que já
têm cotação MUST NOT ser consultados nessa rodada.

#### Scenario: Weekend lot gets the last close
- **GIVEN** um lote de "ITUB4" cadastrado num sábado e sem cotação
- **WHEN** a rodada de tickers sem cotação executa
- **THEN** a cotação de "ITUB4" é gravada com o instante de fechamento informado pelo provedor

#### Scenario: Already quoted tickers are skipped
- **GIVEN** "PETR4" já tem cotação e "ITUB4" não tem
- **WHEN** a rodada de tickers sem cotação executa
- **THEN** só "ITUB4" é consultado

### Requirement: Provider failure keeps the last valid quote
Quando o provedor falha, responde com erro ou não conhece o ticker, a última
cotação válida MUST continuar valendo e nenhum dado do lote MUST mudar. Ao
receber limite de requisições excedido, a rodada MUST parar sem consultar os
tickers restantes. Erros de credencial ou de plano MUST NOT ser repetidos na
mesma rodada.

#### Scenario: Timeout keeps the previous quote
- **GIVEN** "PETR4" tem cotação "41.20"
- **WHEN** a consulta de "PETR4" estoura o tempo limite
- **THEN** a cotação continua "41.20" com o instante anterior e o custo de aquisição dos lotes não muda

#### Scenario: Unknown ticker stays without quote
- **GIVEN** um lote de "ABCD3", que o provedor não conhece
- **WHEN** a rodada executa
- **THEN** "ABCD3" continua sem cotação, os demais tickers são atualizados e o lote continua listado

#### Scenario: Rate limit stops the run
- **GIVEN** lotes de "PETR4", "VALE3" e "ITUB4"
- **WHEN** o provedor responde limite excedido na consulta de "VALE3"
- **THEN** "ITUB4" não é consultado nessa rodada e as cotações anteriores de "VALE3" e "ITUB4" continuam valendo

#### Scenario: Invalid credential stops the run
- **WHEN** o provedor recusa a credencial na primeira consulta
- **THEN** a rodada para sem consultar os demais tickers e nenhuma cotação é alterada

### Requirement: Quote price is normalized to two decimals
O preço recebido do provedor MUST ser convertido sem passar por ponto
flutuante binário e arredondado a duas casas pelo arredondamento bancário
(meio para o par). Preço ausente, zero ou negativo MUST ser tratado como falha
daquele ticker.

#### Scenario: Floating noise is rounded
- **WHEN** o provedor informa o preço 38.470001 para "PETR4"
- **THEN** a cotação gravada é "38.47"

#### Scenario: Half is rounded to even
- **WHEN** o provedor informa os preços 10.125 e 10.135
- **THEN** as cotações gravadas são "10.12" e "10.14"

#### Scenario: Non-positive price is ignored
- **GIVEN** "PETR4" tem cotação "41.20"
- **WHEN** o provedor informa preço 0 para "PETR4"
- **THEN** a cotação continua "41.20"

### Requirement: Quotes without lots are removed
O sistema SHALL apagar a cotação de um ticker quando nenhum lote de nenhum
titular usa mais esse ticker. A remoção MUST acontecer no máximo na rodada
seguinte à exclusão do último lote.

#### Scenario: Last lot deleted removes the quote
- **GIVEN** só um lote usa "MGLU3" e ele tem cotação
- **WHEN** o titular exclui o lote e a próxima rodada executa
- **THEN** não existe mais cotação de "MGLU3"

#### Scenario: Account erasure leaves no orphan quote
- **GIVEN** o único titular com lotes de "MGLU3" exclui a conta
- **WHEN** a próxima rodada executa
- **THEN** não existe mais cotação de "MGLU3"

### Requirement: Quote refresh is disabled without credential
Sem credencial do provedor configurada, o sistema MUST NOT consultar o
provedor e a API SHALL continuar funcionando, listando os lotes com as
cotações já gravadas ou nulas.

#### Scenario: No token configured
- **GIVEN** a credencial do provedor está vazia
- **WHEN** as rodadas agendadas executam
- **THEN** nenhuma requisição é feita ao provedor e a listagem de lotes responde 200

### Requirement: Quote handling keeps sensitive data out of logs
Os logs das rodadas MUST conter só contagens e códigos de erro. Eles MUST NOT
conter ticker associado a preço, quantidade, valor, a resposta bruta do
provedor ou a credencial.

#### Scenario: Run log has counts only
- **WHEN** uma rodada termina com sucessos e falhas
- **THEN** o log registra quantos tickers foram consultados, atualizados e falharam, e os códigos de falha, sem preços, tickers com valores ou o token
