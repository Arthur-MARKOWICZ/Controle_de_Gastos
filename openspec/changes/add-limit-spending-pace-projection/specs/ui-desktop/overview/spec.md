# Spec Delta

## ADDED Requirements

### Requirement: Desktop overview shows LIMIT spending pace
A visão geral do desktop SHALL listar, para verbas LIMIT do mês selecionado com dados de ritmo da API, o dia previsto de estouro quando existir, ou a allowance diária restante. Os valores MUST ser os da API de spending-pace (strings com duas casas); o cliente MUST NOT recalcular.

#### Scenario: Titular vê estouro projetado
- **GIVEN** a API de ritmo devolve "Combustível" com dia previsto "2026-11-20" no mês "2026-11"
- **WHEN** o titular abre a visão geral desse mês
- **THEN** a visão geral cita "Combustível" e a data "2026-11-20"

#### Scenario: Titular vê allowance diária
- **GIVEN** a API de ritmo devolve "Lazer" sem dia de estouro e com allowance "9.55"
- **WHEN** o titular abre a visão geral
- **THEN** a visão geral mostra allowance diária "9.55" para "Lazer"

#### Scenario: Sem verbas LIMIT
- **GIVEN** o mês sem verbas LIMIT visíveis
- **WHEN** o titular abre a visão geral
- **THEN** nenhum indicador de ritmo de LIMIT é inventado
