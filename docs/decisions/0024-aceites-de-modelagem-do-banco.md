# ADR-0024: Aceites de modelagem do banco (owner_id sem FK, sem RLS, índice de saldo)

## Status

Aceito

## Data

2026-11-05

## Contexto

A análise de banco (2026-10-30) registrou três itens que não são defeitos a
corrigir no corte atual: ausência de FK em `ledger_entry.owner_id` (DB-009),
ausência de Row Level Security (DB-010) e hipótese de índice parcial para
`sumAmountUpTo` (DB-007).

## Decisões

### DB-009 — `owner_id` do ledger sem FK

Aceitar a denormalização intencional do ADR-007. Mutações passam só pela API;
o valor é copiado do envelope no registro. Revisar FK somente com novo ADR se
houver escrita fora da API ou inconsistências medidas.

### DB-010 — ausência de RLS

Aceitar isolamento por queries da aplicação (`owner_id` OR participante) com
um único role SQL de aplicação. Reavaliar RLS se o modelo de acesso ao banco
mudar (roles adicionais ou SQL ad hoc em produção).

### DB-007 — índice para `sumAmountUpTo`

Adiar a criação do índice parcial
`(envelope_id, kind, occurred_at) WHERE deleted_at IS NULL` até haver
`EXPLAIN` em ambiente de teste com volume sintético. Índices atuais
`(envelope_id, occurred_at)`, `(envelope_id, kind)` e o parcial de histórico
permanecem. Sem evidência de latência, não criar índice “por reflexo”.

## Alternativas consideradas

- Adicionar FK em `owner_id` agora: rejeitada sem revisar ADR-007.
- Introduzir RLS agora: rejeitada no monólito modular com role único.
- Criar o índice sem medir: rejeitada (custo de escrita sem ganho comprovado).

## Consequências

- Issues DB-007, DB-009 e DB-010 podem ser fechadas como aceite documentado
  neste ADR, mantendo DB-007 reabrível quando houver medição.
- Nenhuma migration neste ADR.

## Referências

- Issues: [#29](https://github.com/Arthur-MARKOWICZ/Controle_de_Gastos/issues/29),
  [#31](https://github.com/Arthur-MARKOWICZ/Controle_de_Gastos/issues/31),
  [#32](https://github.com/Arthur-MARKOWICZ/Controle_de_Gastos/issues/32)
- Relatório: `reports/relatorio-analise-banco-dados-2026-10-30.pdf`
