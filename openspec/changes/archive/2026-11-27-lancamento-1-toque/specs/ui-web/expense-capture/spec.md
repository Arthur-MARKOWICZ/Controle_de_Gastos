# Spec Delta

## Purpose

Define como a web abre o registro rápido de gasto com verba e valor pré-
selecionados a partir da sugestão da API, sem pular a confirmação da pessoa.

## ADDED Requirements

### Requirement: Prefill from expense suggestions
Ao abrir o diálogo rápido de gasto, a web SHALL consultar a API de sugestões e,
quando houver ao menos uma verba, pré-selecionar a primeira e preencher o valor
com o `suggestedAmount` dessa verba se ele não for nulo. A pessoa MUST poder
alterar verba e valor antes de salvar.

#### Scenario: Suggestion available
- **GIVEN** a API devolve "Almoço" com suggestedAmount "32.50" em primeiro lugar
- **WHEN** a pessoa abre o diálogo rápido de gasto
- **THEN** a verba selecionada é "Almoço"
- **AND** o campo valor mostra "32.50"
- **AND** ela pode trocar a verba ou o valor antes de confirmar

#### Scenario: Null suggested amount
- **GIVEN** a API devolve "Farmácia" com suggestedAmount nulo
- **WHEN** a pessoa abre o diálogo rápido de gasto e essa verba é a primeira
- **THEN** "Farmácia" fica selecionada
- **AND** o campo valor permanece vazio para digitação

#### Scenario: Empty suggestions
- **GIVEN** a API devolve lista vazia
- **WHEN** a pessoa abre o diálogo rápido de gasto
- **THEN** o diálogo usa a heurística já existente de escolha de verba
  spendable (se houver)
- **AND** não bloqueia o registro

### Requirement: Confirmation still required
A web MUST NOT registrar gasto só porque a sugestão preencheu os campos. O
envio SHALL ocorrer apenas após ação explícita de confirmar/salvar.

#### Scenario: Prefill does not auto-submit
- **GIVEN** o diálogo abriu com verba e valor sugeridos
- **WHEN** a pessoa não confirma
- **THEN** nenhum gasto é enviado à API
