# Spec Delta

## ADDED Requirements

### Requirement: History search and filters
A página de histórico SHALL oferecer um campo de busca por descrição, um seletor de verba (com a opção "Todas") e um filtro de autor ("Todos", "Lançados por mim", "Lançados por outras pessoas"). Cada controle MUST ter rótulo visível. A busca MUST ser aplicada ao enviar o formulário, com Enter ou botão, ou após uma pausa de digitação, e nunca a cada tecla.

#### Scenario: Buscar por descrição
- **GIVEN** um titular com gastos "Farmácia X" e "Mercado" no período
- **WHEN** ele digita "farmácia" no campo de busca e confirma
- **THEN** a lista mostra só "Farmácia X"
- **AND** a paginação volta para a primeira página

#### Scenario: Filtrar por verba
- **GIVEN** um titular com verbas "Mercado" e "Farmácia"
- **WHEN** ele escolhe "Farmácia" no seletor de verba
- **THEN** a lista mostra só gastos de "Farmácia"

#### Scenario: Participante filtra lançados por mim
- **GIVEN** uma participante da verba compartilhada "Casa"
- **WHEN** ela escolhe "Lançados por mim"
- **THEN** a lista mostra só os gastos que ela lançou
- **AND** continuam sem editar/excluir os gastos cujo papel não é OWNER

### Requirement: History filters live in the URL
Os filtros ativos (`q`, `envelopeId`, `author`) SHALL ser refletidos nos parâmetros da URL, junto com `from` e `to`. Assim, recarregar a página ou voltar no navegador restaura o mesmo resultado. Filtros vazios MUST NOT aparecer na URL.

#### Scenario: Recarregar mantém a busca
- **GIVEN** o titular buscou "farmácia" na verba "Saúde"
- **WHEN** ele recarrega a página
- **THEN** o campo de busca mostra "farmácia", o seletor mostra "Saúde" e a lista é a mesma

#### Scenario: Limpar filtros
- **GIVEN** há busca e filtro de verba ativos
- **WHEN** o titular aciona "Limpar filtros"
- **THEN** q, envelopeId e author saem da URL e a lista volta a mostrar todo o período

### Requirement: Filtered total is shown
Quando qualquer filtro de busca, verba ou autor estiver ativo, a página SHALL exibir a soma dos gastos encontrados (`matchedTotal`) em BRL formatado para `pt-BR`. O texto MUST deixar claro que a soma considera todas as páginas e não inclui excluídos.

#### Scenario: Soma visível com busca ativa
- **GIVEN** a API devolve matchedTotal "48.00" para a busca "farmácia"
- **WHEN** a lista renderiza
- **THEN** a página mostra "R$ 48,00" identificado como total dos resultados

#### Scenario: Sem filtro não mostra soma filtrada
- **GIVEN** nenhum filtro de busca, verba ou autor ativo
- **WHEN** a página renderiza
- **THEN** a soma de resultados filtrados não aparece

### Requirement: Empty search result is distinguishable
Quando há filtros ativos e nenhum gasto atende a eles, o estado vazio SHALL dizer que nenhum gasto corresponde à busca e oferecer "Limpar filtros". Ele MUST NOT usar a mensagem de período sem gastos.

#### Scenario: Busca sem resultado
- **GIVEN** o titular tem gastos no período
- **WHEN** ele busca um termo que não existe
- **THEN** o estado vazio diz que nenhum gasto corresponde aos filtros
- **AND** oferece "Limpar filtros"
