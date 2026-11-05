# ADR-0025: Validação de vencimento anual inválido

## Status

Aceito

## Data

2026-11-05

## Contexto

O CHECK de `V8` aceita `annual_due_day BETWEEN 1 AND 31` independentemente do
mês, o que permite combinações inválidas (ex.: 31/02) se alguém escrever via
SQL direto. A API tipada já rejeita via `MonthDay.of` em
`EnvelopeController.dueDateField` (issue DB-006).

## Decisão

Confiar na validação de domínio/API neste corte. Não endurecer o CHECK SQL com
expressão calendárica (cara de manter e pouco usual em PostgreSQL sem função
auxiliar). Documentar que o banco sozinho não é a última linha de defesa para
datas anuais inválidas.

## Alternativas consideradas

### CHECK/trigger calendárico no PostgreSQL

- Vantagem: defesa no banco.
- Desvantagem: complexidade de migration e manutenção de regra de calendário.
- Adiada; reavaliar se aparecer escrita fora da API.

## Consequências

- Manter/cobrir rejeição na API com teste de integração.
- Sem migration neste ADR.

## Referências

- Issue: [#28 DB-006](https://github.com/Arthur-MARKOWICZ/Controle_de_Gastos/issues/28)
