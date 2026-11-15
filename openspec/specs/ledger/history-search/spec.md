# history-search Specification

## Purpose

Permite encontrar gastos do histórico pelo texto da descrição e restringir a lista por verba e por autor. O conjunto visível continua sendo o que o dono e os participantes de cada verba já podem ver, e a soma dos resultados também é devolvida.

## Requirements

### Requirement: Busca textual na descrição
O histórico SHALL aceitar um termo de busca opcional `q`. Quando ele é informado, a lista MUST conter só gastos cuja descrição contém o termo, sem diferenciar maiúsculas de minúsculas. Os espaços nas pontas são ignorados, e termo vazio equivale a não filtrar. Os caracteres `%` e `_` MUST ser tratados literalmente, não como curingas.

#### Scenario: Termo encontra descrição sem diferenciar caixa
- **GIVEN** o titular tem os gastos "Farmácia São João" de "35.90" e "Mercado" de "120.00" no período
- **WHEN** ele consulta o histórico do período com q "farmácia"
- **THEN** a lista contém só o gasto "Farmácia São João"

#### Scenario: Termo vazio não filtra
- **GIVEN** o titular tem dois gastos no período
- **WHEN** ele consulta o histórico com q "   "
- **THEN** a lista contém os dois gastos

#### Scenario: Curinga é tratado como texto
- **GIVEN** o titular tem os gastos "Desconto 10%" e "Desconto 100 reais"
- **WHEN** ele consulta o histórico com q "10%"
- **THEN** a lista contém só "Desconto 10%"

#### Scenario: Gasto sem descrição não casa com termo
- **GIVEN** o titular tem um gasto sem descrição no período
- **WHEN** ele consulta o histórico com q "mercado"
- **THEN** esse gasto não aparece na lista

### Requirement: Termo de busca tem tamanho limitado
A API SHALL recusar com 400 um termo `q` que, depois de remover os espaços das pontas, tenha mais de 140 caracteres, que é o tamanho máximo da descrição. O termo MUST NOT ser registrado em logs nem persistido.

#### Scenario: Termo longo demais
- **WHEN** o titular consulta o histórico com um q de 141 caracteres
- **THEN** a API responde 400 com mensagem em português

### Requirement: Filtro por verba
O histórico SHALL aceitar um `envelopeId` opcional que restringe a lista aos gastos daquela verba. O filtro MUST NOT ampliar a visibilidade. Uma verba que a pessoa não pode ver resulta em lista vazia, sem revelar se a verba existe. Um `envelopeId` que não é UUID válido resulta em 400.

#### Scenario: Dono filtra pela própria verba
- **GIVEN** o titular tem gastos nas verbas "Mercado" e "Farmácia"
- **WHEN** ele consulta o histórico com o envelopeId de "Farmácia"
- **THEN** a lista contém só gastos da verba "Farmácia"

#### Scenario: Participante filtra pela verba compartilhada
- **GIVEN** a verba "Casa" do titular está compartilhada com a participante
- **WHEN** a participante consulta o histórico com o envelopeId de "Casa"
- **THEN** a lista contém os gastos ativos de "Casa" no período

#### Scenario: Terceiro filtra por verba alheia
- **GIVEN** uma verba de outro titular que não foi compartilhada com o terceiro
- **WHEN** o terceiro consulta o histórico com o envelopeId dessa verba
- **THEN** a API responde 200 com lista vazia e matchedTotal "0.00"

#### Scenario: Identificador inválido
- **WHEN** o titular consulta o histórico com envelopeId "abc"
- **THEN** a API responde 400

### Requirement: Filtro por autor
O histórico SHALL aceitar um `author` opcional com os valores `ME` (gastos lançados pela pessoa autenticada) ou `OTHERS` (gastos lançados por outras pessoas em verbas visíveis). Qualquer outro valor MUST resultar em 400.

#### Scenario: Titular vê só o que lançou
- **GIVEN** a verba "Casa" do titular tem um gasto lançado por ele e um lançado pela participante
- **WHEN** o titular consulta o histórico com author "ME"
- **THEN** a lista contém só o gasto lançado pelo titular

#### Scenario: Titular vê o que a participante lançou
- **GIVEN** a mesma verba "Casa"
- **WHEN** o titular consulta o histórico com author "OTHERS"
- **THEN** a lista contém só o gasto lançado pela participante

#### Scenario: Valor de autor inválido
- **WHEN** o titular consulta o histórico com author "TODOS"
- **THEN** a API responde 400

### Requirement: Filtros combinados respeitam a visibilidade existente
Período, `q`, `envelopeId`, `author` e `includeDeleted` SHALL ser combinados com E lógico e aplicados sobre o conjunto que a pessoa já pode ver no histórico. Esse conjunto é formado pelos gastos das verbas próprias e das verbas compartilhadas com ela. Excluídos aparecem só para o dono da verba, e a ordem continua data desc, criação desc.

#### Scenario: Terceiro não encontra gasto alheio pela descrição
- **GIVEN** outro titular tem o gasto "Farmácia X" numa verba não compartilhada
- **WHEN** o terceiro consulta o histórico com q "farmácia"
- **THEN** a lista não contém esse gasto

#### Scenario: Participante não vê excluídos na busca
- **GIVEN** a verba compartilhada "Casa" tem o gasto excluído "Farmácia X"
- **WHEN** a participante consulta o histórico com q "farmácia" e includeDeleted true
- **THEN** a lista não contém o gasto excluído

#### Scenario: Dono vê excluídos na busca
- **GIVEN** a mesma verba "Casa" e o gasto excluído "Farmácia X"
- **WHEN** o titular consulta o histórico com q "farmácia" e includeDeleted true
- **THEN** a lista contém o gasto excluído com deletedAt preenchido

#### Scenario: Sem filtros novos o histórico não muda
- **GIVEN** qualquer pessoa autenticada
- **WHEN** ela consulta o histórico só com from, to, page e size
- **THEN** a lista e a paginação são as mesmas de antes desta mudança

### Requirement: Soma dos gastos filtrados
A página do histórico SHALL incluir `matchedTotal` (Money BRL, string decimal com duas casas). Ele é a soma dos gastos **ativos** que atendem a todos os filtros no período, considerando todas as páginas e não só a atual. Gastos excluídos MUST NOT entrar na soma, mesmo com `includeDeleted` true.

#### Scenario: Quanto gastei na farmácia
- **GIVEN** o titular tem no período os gastos "Farmácia X" de "35.90" e "Farmácia X" de "12.10", e "Mercado" de "200.00"
- **WHEN** ele consulta o histórico com q "farmácia x" e size 1
- **THEN** matchedTotal é "48.00" com currency "BRL"
- **AND** hasNext é true

#### Scenario: Excluído não soma
- **GIVEN** o titular tem "Farmácia X" ativo de "35.90" e "Farmácia X" excluído de "50.00"
- **WHEN** ele consulta com q "farmácia" e includeDeleted true
- **THEN** a lista mostra os dois gastos
- **AND** matchedTotal é "35.90"

#### Scenario: Nenhum resultado
- **WHEN** o titular consulta com um termo que não casa com nada
- **THEN** a lista é vazia e matchedTotal é "0.00"
