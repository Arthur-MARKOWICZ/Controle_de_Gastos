# Spec Delta

## ADDED Requirements

### Requirement: Prefill sheet from expense suggestions
Ao abrir a folha de lançamento de gasto no Android sem verba já forçada pelo
contexto (card de meta ou toque numa verba específica do widget), o aplicativo
SHALL consultar a API de sugestões e pré-selecionar a primeira verba com o
`suggestedAmount` correspondente quando não for nulo. A pessoa MUST poder
alterar os campos antes de confirmar.

#### Scenario: Open from in-app new expense with suggestions
- **GIVEN** a API devolve "Uber" com suggestedAmount "22.00" em primeiro lugar
- **WHEN** a pessoa autentica abre "Novo gasto" na Visão geral
- **THEN** a folha inicia com "Uber" e valor "22.00"
- **AND** confirmar ainda exige toque explícito

#### Scenario: Widget forced envelope wins over ranking
- **GIVEN** o widget abriu a folha com envelopeId de "Mercado"
- **AND** a API ranquearia "Almoço" em primeiro
- **WHEN** a folha abre
- **THEN** a verba selecionada é "Mercado"
- **AND** o valor pode vir do suggestedAmount de "Mercado" se a API o incluir

#### Scenario: Suggestions unavailable
- **GIVEN** a consulta de sugestões falha ou devolve lista vazia
- **WHEN** a pessoa abre "Novo gasto"
- **THEN** a folha permanece utilizável sem pré-preenchimento de sugestão
- **AND** não bloqueia o lançamento
