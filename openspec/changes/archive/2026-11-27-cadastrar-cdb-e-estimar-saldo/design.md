# Design

## Context

Ver `proposal.md` (Why) e `specs/investments/cdb-positions/spec.md`. O
ADR-0034 já fixou a fórmula do CDB prefixado, a base de 365 dias corridos, o
uso de `big-math` e o isolamento do orçamento. O módulo `investments` já
existe (#76/#77) com o padrão `domain` / `application` / `infrastructure` /
`web`, `MoneyJson` local, `@RestControllerAdvice` por recurso e exclusão
física com 404 cruzado. A última migration de investimentos é a `V23`
(`stock_quote`).

## Goals / Non-Goals

**Goals:**

- Reutilizar o padrão de lotes de ações para CDB, sem reorganizar o módulo.
- Manter a estimativa no domínio, testável sem Spring e sem `double`.
- Introduzir `big-math` só nesta change, como o ADR-0034 antecipou.

**Non-Goals:**

- Extrair `MoneyJson` para `shared`.
- Paginação da listagem.
- Edição ou exclusão lógica.
- Agregar CDBs no resumo da carteira (#79).

## Decisions

### D1. Entidade `CdbPosition` e estimativa sob demanda

`investments.domain.CdbPosition` guarda `id`, `ownerId`, `principal`
(`Money`), `annualRate` (`BigDecimal` com escala 4), `startDate`,
`maturityDate` e `createdAt`. O método `estimatedValue(LocalDate today)`
aplica a fórmula do ADR-0034 e devolve `Money`. O estimado **não** é coluna.

- *Alternativa: persistir o estimado.* Rejeitada: divergiria do tempo e do
  ADR ("recalculado pelo rendimento").
- *Alternativa: value object só para a taxa.* Opcional; um `AnnualRate` com
  `of(String)` espelha `B3Ticker` e concentra a validação de escala. Preferido
  se a validação ficar verbosa no service.

### D2. Taxa decimal com quatro casas, não `Money`

A taxa não é dinheiro. Persistência `NUMERIC(7, 4)` com `CHECK (annual_rate > 0)`.
Na API entra e sai como string `"0.1250"`. Recusar `12.50`, escalas diferentes
de 4 e não positivos. Nenhuma conversão percentual.

- *Alternativa: aceitar `12.50` e dividir por 100.* Rejeitada na exploração;
  o contrato fica alinhado ao ADR.

### D3. Cálculo com `big-math` e `MathContext.DECIMAL128`

```
dias     = ChronoUnit.DAYS.between(startDate, min(today, maturityDate))
fator    = BigDecimalMath.pow(ONE.add(annualRate), dias/365, DECIMAL128)
estimado = principal × fator → setScale(2, HALF_EVEN) só no resultado
```

`dias / 365` é `BigDecimal` (nunca `double`). Se `dias == 0`, o estimado é o
principal sem chamar `pow`. Dependência:
`implementation("ch.obermuhlner:big-math:2.3.2")` (ou a última estável 2.x
no Maven Central no momento da implementação).

- *Alternativa: `Math.pow` em `double`.* Rejeitada pelo ADR-0034.

### D4. Datas com `Clock` em `America/Sao_Paulo`

`startDate` ≤ hoje; `maturityDate` > `startDate`. O service passa `today` ao
domínio (cadastro e listagem), no mesmo padrão de `StockLot`.

### D5. API e JSON

```
POST /api/v1/investments/cdbs
{ "principal": { "amount": "10000.00", "currency": "BRL" },
  "annualRate": "0.1250",
  "startDate": "2025-11-26",
  "maturityDate": "2027-11-26" }

201 / item do GET
{ "id": "…",
  "principal": { "amount": "10000.00", "currency": "BRL" },
  "annualRate": "0.1250",
  "startDate": "2025-11-26",
  "maturityDate": "2027-11-26",
  "estimatedValue": { "amount": "11250.00", "currency": "BRL" },
  "createdAt": "…" }

GET    /api/v1/investments/cdbs       -> 200 [ … ]
DELETE /api/v1/investments/cdbs/{id}  -> 204 | 404 CDB_POSITION_NOT_FOUND
```

Reutilizar `investments.web.MoneyJson`. Listagem:
`ORDER BY start_date, created_at`. Sem `elapsedDays` / `asOf`.

### D6. Tabela e migration

```sql
-- V24__create_cdb_position.sql
CREATE TABLE cdb_position (
    id            UUID PRIMARY KEY,
    owner_id      UUID NOT NULL REFERENCES user_account(id),
    principal     NUMERIC(19, 2) NOT NULL CHECK (principal > 0),
    annual_rate   NUMERIC(7, 4) NOT NULL CHECK (annual_rate > 0),
    start_date    DATE NOT NULL,
    maturity_date DATE NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    CHECK (maturity_date > start_date)
);
CREATE INDEX cdb_position_owner_idx ON cdb_position (owner_id, start_date);
```

Sem `ON DELETE CASCADE`: a limpeza fica em `privacy`, como em `stock_lot`.

### D7. Direitos do titular em `privacy`

`TitularRightsService`:

- `eraseFinancialData`: `DELETE FROM cdb_position WHERE owner_id = ?`;
- exportação: chave `cdbPositions` com `id`, `principal` (`::text`),
  `currency`, `annualRate` (`::text`), `startDate`, `maturityDate`,
  `createdAt`. Sem `estimatedValue`.

### D8. Canais

- **API**: única afetada.
- **Web e Android**: #80.
- **MCP e desktop**: fora do dia 1 (ADR-0034).

## Risks / Trade-offs

- [Estimativa 365 dias diverge do extrato bancário (base 252)] → Aceito no
  ADR-0034; o cliente rotula como estimado. Troca futura não muda o contrato
  se a fórmula continuar documentada.
- [Valor bruto sem IR/IOF] → Explícito no ADR; UI (#80) deve rotular.
- [Sem edição, erro de digitação exige recriar] → Mesmo padrão dos lotes.
- [`privacy` esquecer a tabela] → Teste de integração de direitos do titular
  cobre exportação e exclusão com CDBs.
- [Versão exata do `big-math`] → Fixar no Gradle na implementação; API estável
  (`BigDecimalMath.pow`).

## Migration Plan

A `V24` só cria tabela nova. Deploy normal da API. Rollback: reverter o
código; a tabela vazia pode permanecer até uma migration de remoção, se
preciso.
