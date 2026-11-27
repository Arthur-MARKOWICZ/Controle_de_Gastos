# Spec Delta

## ADDED Requirements

### Requirement: Prefill dialog from expense suggestions
Ao abrir o diálogo de captura sem verba já escolhida pelo contexto (ação numa
verba específica), o desktop SHALL consultar a API de sugestões e
pré-selecionar a primeira verba com o `suggestedAmount` quando não for nulo,
inclusive quando a abertura veio do atalho global, da bandeja ou de `Ctrl+N`.
A pessoa MUST poder alterar verba e valor antes de salvar.

#### Scenario: Open via global shortcut with suggestions
- **GIVEN** a API devolve "Almoço" com suggestedAmount "32.50"
- **WHEN** a pessoa abre o diálogo pelo atalho global
- **THEN** a verba selecionada é "Almoço" e o valor é "32.50"
- **AND** o envio só ocorre após confirmar

#### Scenario: Open from specific envelope action
- **GIVEN** a pessoa aciona registrar gasto na verba "Lazer" na lista
- **WHEN** o diálogo abre
- **THEN** a verba permanece "Lazer"
- **AND** o valor pode ser preenchido com o suggestedAmount de "Lazer" se a
  API o fornecer para essa verba

#### Scenario: Empty suggestions fall back to first spendable
- **GIVEN** a API devolve lista vazia
- **AND** existe ao menos uma verba spendable no resumo
- **WHEN** a pessoa abre o diálogo pelo atalho de janela
- **THEN** o cliente usa a primeira verba spendable como hoje
- **AND** o campo valor inicia vazio
