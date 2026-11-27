# Design

## Context

Ver `proposal.md` (Why) e `specs/investments/stock-lots/spec.md`. O ADR-0034
fixa a fronteira do módulo e a regra de acesso só do titular. Pontos do código
atual que moldam a implementação:

- Os módulos seguem o layout `domain` / `application` / `infrastructure` /
  `web` (por exemplo, `ledger`). As entidades são JPA, com `UUID` atribuído na
  aplicação, `Money` mapeado por `MoneyJpaConverter` e `Clock` injetado (há o
  `FixedClockTestConfiguration` nos testes).
- O usuário atual vem de `identity.application.AuthenticationService.currentUserId()`.
- Os controllers recebem `JsonNode` e validam à mão. O helper `MoneyJson`
  (`{amount, currency}`, exatamente duas casas) é package-private em
  `ledger.web`, então não pode ser importado por outro módulo.
- `IllegalArgumentException` já vira 400 `INVALID_REQUEST` no
  `ApiExceptionHandler` global. O 404 de cada recurso usa um
  `@RestControllerAdvice` restrito aos controllers do módulo, com um `code`
  próprio.
- A exclusão da conta não apaga `user_account`: `AccountClosureService` só
  marca a conta como excluída. Por isso um `ON DELETE CASCADE` não basta, e
  `privacy.TitularRightsService.eraseFinancialData` apaga cada tabela com
  `DELETE` explícito. A exportação lê as tabelas via `JdbcTemplate`.
- A última migration é a `V21`.

## Goals / Non-Goals

**Goals:**

- Criar o módulo `investments` com uma estrutura que a #77 (cotação) e a #78
  (CDB) possam estender sem reorganizar pacotes.
- Manter a regra de negócio do lote (normalização do ticker, validações e
  custo) no domínio, testável sem Spring.

**Non-Goals:**

- Extrair `MoneyJson` para `shared` (fica para uma refatoração separada).
- Paginação da listagem. Uma carteira pessoal tem dezenas de lotes, não
  milhares.
- Controle de concorrência otimista. Não existe edição, só criação e exclusão.

## Decisions

### D1. Lote como entidade `StockLot`, sem agregado de posição

`investments.domain.StockLot` guarda `id`, `ownerId`, `ticker`, `quantity`
(`long`), `unitPrice` (`Money`), `purchaseDate` (`LocalDate`) e `createdAt`.
O método `acquisitionCost()` calcula `unitPrice × quantity` sob demanda e não
vai para o banco.

- *Alternativa: persistir o custo.* Rejeitada porque o custo é derivado e
  persistir os dois permitiria divergência.
- *Alternativa: agregado "posição por ticker".* Rejeitada na exploração. A
  agregação é leitura da #79.

`Money` não tem multiplicação. Adicionar `Money.multiply(long)` em `shared`
mantém a aritmética monetária num só lugar. O resultado continua exato com
duas casas, porque a quantidade é inteira. Se o custo passar de 17 dígitos
inteiros, `Money` lança `IllegalArgumentException`, o que vira 400.

### D2. Ticker como value object `B3Ticker`

`B3Ticker.of(String)` aplica `trim`, `toUpperCase(Locale.ROOT)`, remove um `F`
final quando o que sobra casa com `^[A-Z]{4}\d{1,2}$` e então exige esse
padrão. Ele é persistido como `VARCHAR(6)`. Assim a #77 e a #79 recebem o
ticker já normalizado e podem agrupar por igualdade simples.

- *Alternativa: `String` validada no service.* Rejeitada porque a regra se
  repetiria na #77 ao consultar a cotação.

### D3. Validação da data com `Clock` no fuso `America/Sao_Paulo`

A criação compara `purchaseDate` com `LocalDate.now(clock.withZone(SAO_PAULO))`.
A regra fica no service de aplicação (`StockLotService.register`), que tem o
`Clock`. O construtor do domínio recebe a data "hoje" como argumento, para os
testes de unidade não dependerem do relógio.

### D4. Exclusão física com 404 para quem não é dono

A consulta do repositório já filtra pelo dono:
`findByIdAndOwnerId(id, ownerId)`. Se o lote não existe ou é de outro titular,
lança `StockLotNotFoundException`, que vira 404 `STOCK_LOT_NOT_FOUND`. Depois
vem o `delete`. Não há `deleted_at`, porque nada depende do lote e o ADR-0034
não pede trilha de auditoria da carteira.

- *Alternativa: exclusão lógica, como no `ledger_entry`.* Rejeitada. Lá ela
  existe porque o lançamento afeta saldos e tem histórico, o que não vale para
  o lote.

### D5. API e JSON

```
POST /api/v1/investments/stocks
{ "ticker": "PETR4", "quantity": 100,
  "unitPrice": { "amount": "38.47", "currency": "BRL" },
  "purchaseDate": "2026-11-03" }

201 / item do GET
{ "id": "…", "ticker": "PETR4", "quantity": 100,
  "unitPrice":       { "amount": "38.47",   "currency": "BRL" },
  "acquisitionCost": { "amount": "3847.00", "currency": "BRL" },
  "purchaseDate": "2026-11-03", "createdAt": "…" }

GET    /api/v1/investments/stocks          -> 200 [ … ]
DELETE /api/v1/investments/stocks/{id}     -> 204 | 404
```

`quantity` precisa ser um JSON inteiro (`isIntegralNumber`), porque `1.5` e
`"100"` são recusados. Será criado um `investments.web.MoneyJson` duplicado do
de `ledger` (com cerca de 40 linhas). O formato do dinheiro já é o padrão do
ADR-0006. A listagem usa `ORDER BY purchase_date, ticker, created_at`.

- *Alternativa: tornar `ledger.web.MoneyJson` público.* Rejeitada porque
  criaria uma dependência `investments -> ledger`, que o ADR-0034 proíbe.
- *Alternativa: movê-lo para `shared` agora.* Adiada para não mexer em
  `ledger` nesta change.

### D6. Tabela e migration

```sql
-- V22__create_stock_lot.sql
CREATE TABLE stock_lot (
    id            UUID PRIMARY KEY,
    owner_id      UUID NOT NULL REFERENCES user_account(id),
    ticker        VARCHAR(6) NOT NULL CHECK (ticker ~ '^[A-Z]{4}[0-9]{1,2}$'),
    quantity      BIGINT NOT NULL CHECK (quantity > 0),
    unit_price    NUMERIC(19, 2) NOT NULL CHECK (unit_price > 0),
    purchase_date DATE NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX stock_lot_owner_idx ON stock_lot (owner_id, purchase_date);
```

O `CHECK` repete as invariantes do domínio como defesa em profundidade, no
mesmo estilo de `recurring_entry`. A FK não usa `CASCADE`, já que a linha de
`user_account` nunca é apagada. A limpeza acontece em `privacy`.

### D7. Direitos do titular em `privacy`

`TitularRightsService` ganha:

- em `eraseFinancialData`: `DELETE FROM stock_lot WHERE owner_id = ?`;
- em `exportCurrentTitular`: a chave `stockLots`, com `id`, `ticker`,
  `quantity`, `unitPrice` (`::text`), `currency`, `purchaseDate` e
  `createdAt`, ordenada por `purchase_date`.

`privacy` já usa SQL direto sobre as tabelas de todos os módulos. Por isso
`investments` não precisa expor API pública nem eventos. A exportação não
inclui o custo de aquisição, por ser um dado derivado.

### D8. Fronteiras do Spring Modulith

O `package-info.java` do módulo traz
`@ApplicationModule(displayName = "Investimentos")`. O `ModularityTest`
existente passa a cobrir o módulo novo automaticamente. Também entra um
`ApplicationModuleTest` (ou uma asserção no `ModularityTest`) garantindo que
`investments` só depende de `shared` e `identity`.

### Canais

- **API**: única afetada.
- **Web e Android**: ficam para a #80.
- **MCP e desktop**: fora do dia 1 (ADR-0034).

## Risks / Trade-offs

- [O formato aceita tickers que não existem (por exemplo, "ABCD3")] → A
  validação de existência chega com a cotação (#77). Um ticker inválido fica
  sem cotação, e o usuário pode excluir o lote.
- [Sem edição, um erro de digitação exige excluir e recriar] → Foi uma decisão
  explícita do titular. Os dados de um lote são poucos.
- [`MoneyJson` duplicado pode divergir do original] → É pequeno e coberto pelos
  testes de API dos dois módulos. A extração para `shared` está registrada
  como refatoração futura.
- [Mudança em `shared.Money` (`multiply`) afeta todos os módulos] → É um
  método novo, aditivo e coberto por testes de unidade próprios.
- [Lotes ficam fora da exportação se `privacy` esquecer a tabela] → Um teste
  de integração de `TitularRightsApiIntegrationTest` cobre a exportação e a
  exclusão com lotes.

## Migration Plan

A `V22` só cria uma tabela nova, sem alterar dados existentes, e o deploy é o
normal da API. Para desfazer, basta reverter o código. A tabela vazia pode
ficar até uma migration de remoção, se um dia for preciso.
