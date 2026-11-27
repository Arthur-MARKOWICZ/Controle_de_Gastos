# Design

## Context

Ver `proposal.md` (Why) e `specs/investments/portfolio-summary/spec.md`. O
ADR-0034 já fixou o resumo (total, detalhe por ativo/posição, P&L =
valor atual − custo) e o isolamento do orçamento. Nesta branch o módulo
`investments` já tem lotes (`StockLot` + `StockLotView` com cotação),
cotações (`stock_quote`) e CDBs (`CdbPosition.estimatedValue`). Não há
endpoint de agregação.

## Goals / Non-Goals

**Goals:**

- Agregar na aplicação, sem SQL analítico nem tabela de resumo.
- Reutilizar repositórios e regras já existentes (`acquisitionCost`,
  `currentValue`, `estimatedValue`).
- Documentar o `HALF_EVEN` do preço médio no design, sem ADR novo.

**Non-Goals:**

- Extrair `MoneyJson` para `shared`.
- Paginação.
- Alterar contratos de `/stocks` ou `/cdbs`.
- UI (#80).

## Decisions

### D1. Serviço de leitura `PortfolioSummaryService`

Novo serviço em `investments.application` que, para o titular autenticado:

1. lista lotes (`StockLotRepository.findListing`) e cotações dos tickers;
2. lista CDBs (`CdbPositionRepository.findListing`) e estima com
   `LocalDate.now(clock.withZone(America/Sao_Paulo))`;
3. agrupa lotes por ticker e monta o DTO de resumo.

Não chama `QuoteProvider`. Não grava nada. `@Transactional(readOnly = true)`.

- *Alternativa: estender `StockLotService.list`.* Rejeitada: misturaria
  agregação com o contrato de listagem de lotes.
- *Alternativa: view SQL / materialized.* Rejeitada: overengineering e cheira
  a cache.

### D2. Preço médio com `HALF_EVEN` (sem ADR novo)

```
averagePrice = sum(acquisitionCost).amount
                 .divide(BigDecimal.valueOf(totalQty), 2, HALF_EVEN)
             → Money.brl(...)
```

A divisão quase nunca é exata em duas casas. O arredondamento fica só nesse
derivado, no mesmo espírito do estimado do CDB (ADR-0034) e da cotação
(ADR-0035). Valores digitados pelo titular continuam com `UNNECESSARY` via
`Money`. Não adicionar `Money.divide` genérico: o cálculo fica no agregador
do resumo (ou num helper de pacote `investments`), para a exceção não virar
API pública de `shared`.

- *Alternativa: ADR filho só para o preço médio.* Desnecessário: o precedente
  e o modo já existem; o design registra o uso.

### D3. Totais e flag `complete`

```
quoted stocks + all CDBs  →  totalCost, totalCurrent, totalPnl
complete = nenhum ticker de ação do titular está sem cotação
           (carteira sem ações ⇒ complete true)
```

Cotação é por ticker: ou todos os lotes do ticker têm valor atual, ou nenhum.
Ticker sem cotação aparece em `stocks` com `currentValue`/`pnl` (e dos lotes)
null, fora dos totais.

P&L de lote/ticker/CDB/total: `current − cost` (CDB: `estimated − principal`),
usando `Money.subtract` (pode ser negativo).

### D4. API e JSON

```
GET /api/v1/investments/summary -> 200

{
  "complete": true,
  "totalCost":    { "amount": "15847.00", "currency": "BRL" },
  "totalCurrent": { "amount": "17300.00", "currency": "BRL" },
  "totalPnl":     { "amount": "1453.00",  "currency": "BRL" },
  "stocks": [
    {
      "ticker": "PETR4",
      "quantity": 150,
      "averagePrice":    { "amount": "38.98", "currency": "BRL" },
      "acquisitionCost": { "amount": "5847.00", "currency": "BRL" },
      "currentValue":    { "amount": "6180.00", "currency": "BRL" },
      "pnl":             { "amount": "333.00",  "currency": "BRL" },
      "lots": [
        {
          "id": "…",
          "quantity": 100,
          "unitPrice":       { "amount": "38.47", "currency": "BRL" },
          "acquisitionCost": { "amount": "3847.00", "currency": "BRL" },
          "purchaseDate": "2026-11-03",
          "currentValue": { "amount": "4120.00", "currency": "BRL" },
          "pnl":          { "amount": "273.00",  "currency": "BRL" }
        }
      ]
    }
  ],
  "cdbs": [
    {
      "id": "…",
      "principal":      { "amount": "10000.00", "currency": "BRL" },
      "estimatedValue": { "amount": "11250.00", "currency": "BRL" },
      "pnl":            { "amount": "1250.00",  "currency": "BRL" }
    }
  ]
}
```

`PortfolioSummaryController` em `/api/v1/investments/summary`. Reutilizar
`MoneyJson`. Ordenação: `stocks` por ticker ascendente; lotes como na
listagem (`purchase_date`, `created_at`); `cdbs` por `start_date`,
`created_at`. Campos monetários nulos serializam como `null` (mesmo padrão
de `currentQuote`/`currentValue` nos lotes).

- *Alternativa: `GET /api/v1/investments`.* Rejeitada na exploração; `/summary`
  deixa explícito que é leitura agregada.

### D5. Fronteiras e canais

Tudo dentro de `investments`; dependências continuam `shared` e `identity`.
Sem migration, sem inventário LGPD novo (não cria dado pessoal).
**API** só. **Web/Android**: #80. **MCP/desktop**: fora do dia 1.

## Risks / Trade-offs

- [Totais parciais confundem o cliente] → Mitigação: flag `complete` e
  detalhe com nulls explícitos; a UI (#80) rotula "parcial".
- [Preço médio arredondado ≠ reconstrução exata dos lotes] → Aceito: é o
  preço unitário de referência; o custo agregado continua sendo a soma exata
  dos `acquisitionCost`.
- [Carteira grande em memória] → Aceitável no dia 1 (limite operacional de
  ~30 tickers no ADR-0035); sem paginação.

## Migration Plan

Sem migration. Deploy só de código. Rollback: remover o endpoint; lotes e
CDBs inalterados.

## Open Questions

Nenhuma. Decisões de contrato e arredondamento fechadas na exploração.
