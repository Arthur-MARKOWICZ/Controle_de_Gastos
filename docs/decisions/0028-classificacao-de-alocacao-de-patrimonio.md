# ADR-028: Classificação de alocação de patrimônio na verba

## Status

Aceito

## Data

2026-11-10

## Contexto

Aportes registrados como `EXPENSE` em verbas comuns entram no total de
“gastos” do resumo do período e distorcem o orçamento: o titular não consegue
ler “consumi X / apliquei Y”. A intenção confirmada
([`docs/intent/alocacao-patrimonio.md`](../intent/alocacao-patrimonio.md))
pede classificar a **verba inteira**, sem módulo de investimentos, sem cotação
e sem marcar lançamento a lançamento.

`GOAL` e `SAVINGS_TARGET` já usam `CONTRIBUTION` para aportes, mas isso não
cobre o caso em que a saída real da conta (Pix, TED) é lançada como `EXPENSE`
numa verba de limite usada para investir.

## Decisão

- Adicionar o booleano `wealthAllocation` (coluna `wealth_allocation`) em
  `envelope`, padrão `false`.
- Na criação e na atualização da verba, o proprietário pode marcar ou
  desmarcar o flag. Não há propósito novo nem tela dedicada.
- Em `GET /api/v1/history/summary`:
  - `expenses` = soma de `EXPENSE` em verbas com `wealthAllocation = false`
    (consumo);
  - `applied` = soma de `EXPENSE` em verbas com `wealthAllocation = true`
    (alocação de patrimônio);
  - `netBalance` = `income − expenses − applied`;
  - totais mensais e por propósito agregam apenas consumo.
- Relatórios de “gastos” excluem saídas de verbas marcadas; elas não entram
  como consumo.
- Clientes (web, mobile, MCP) expõem o flag na verba e mostram consumo e
  alocação no resumo do período.

## Alternativas consideradas

### Novo propósito `INVESTMENT`

Rejeitada: muda a semântica de saldo/provisão e força o titular a trocar a
natureza da verba. O flag é ortogonal a `LIMIT`/`GOAL`/`FIXED`/etc.

### Marcar cada lançamento

Rejeitada pela intenção confirmada: atrito alto e fora do corte.

### Contar só `CONTRIBUTION` como “apliquei”

Rejeitada: não resolve o aporte lançado como `EXPENSE` — a dor atual.

### Área/módulo de investimentos na navegação

Rejeitada neste corte: o resultado desejado é o orçamento honesto no resumo.

## Consequências

- Migração Flyway, agregado `Envelope`, OpenAPI, inventário de dados e
  clientes precisam reconhecer `wealthAllocation` / `applied`.
- `expenses` no resumo histórico deixa de incluir saídas de verbas marcadas
  (mudança semântica documentada do contrato).
- Cotação, rentabilidade e carteira por ativo permanecem fora de escopo e
  exigiriam novo ADR.
