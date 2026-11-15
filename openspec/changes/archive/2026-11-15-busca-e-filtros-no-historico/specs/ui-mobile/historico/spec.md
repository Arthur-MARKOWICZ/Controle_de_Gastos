# Spec Delta

## Purpose

Define a busca e os filtros da tela de histórico no Android, para a pessoa achar um gasto pelo texto ou pela verba e ver quanto somam os resultados, sem rolar o mês inteiro.

## ADDED Requirements

### Requirement: Busca por descrição no histórico
A tela de histórico SHALL ter um campo de busca com rótulo visível. A busca é aplicada quando a pessoa confirma pela ação de busca do teclado ou após uma pausa de digitação, nunca a cada tecla. Ela volta para a primeira página e mantém o mês selecionado.

#### Scenario: Buscar no mês corrente
- **GIVEN** o titular tem "Farmácia X" e "Mercado" no mês corrente
- **WHEN** ele digita "farmácia" e confirma pela ação de busca do teclado
- **THEN** a lista mostra só "Farmácia X"

#### Scenario: Trocar de mês mantém a busca
- **GIVEN** a busca "farmácia" está ativa
- **WHEN** o titular navega para o mês anterior
- **THEN** a lista mostra os gastos do mês anterior que casam com "farmácia"

### Requirement: Filtro de verba no histórico
A tela de histórico SHALL oferecer a escolha de uma verba visível, com a opção "Todas", para restringir a lista. As verbas oferecidas MUST ser as que a pessoa vê, próprias e compartilhadas.

#### Scenario: Participante filtra pela verba compartilhada
- **GIVEN** uma participante da verba "Casa"
- **WHEN** ela escolhe "Casa" no filtro
- **THEN** a lista mostra só gastos de "Casa"
- **AND** editar e excluir continuam ocultos nos gastos cujo papel não é OWNER

### Requirement: Soma e estado vazio da busca
Com busca ou filtro de verba ativos, a tela SHALL mostrar a soma dos resultados (`matchedTotal`) em BRL. Sem resultado, ela MUST mostrar um estado vazio que diz que nada corresponde aos filtros e oferece limpar os filtros.

#### Scenario: Soma dos resultados
- **GIVEN** a API devolve matchedTotal "48.00"
- **WHEN** a lista filtrada renderiza
- **THEN** a tela mostra "R$ 48,00" como total dos resultados

#### Scenario: Nada encontrado
- **WHEN** a busca não casa com nenhum gasto do mês
- **THEN** a tela diz que nenhum gasto corresponde aos filtros
- **AND** oferece limpar os filtros
