# Design

## Context

- O histórico é servido por `HistoryController.history` → `LedgerService.history` → `LedgerEntryRepository.findHistory`, uma query JPQL que já aplica a visibilidade: `owner_id = :userId` ou participante via `EXISTS` em `EnvelopeParticipant`. Excluídos aparecem só ao dono, e a ordem é `occurredAt DESC, createdAt DESC`. A paginação aceita `size` de 1 a 100.
- `ledger_entry.description` é `VARCHAR(140)` e aceita nulo. Os índices atuais cobrem `owner_id`, `author_id`, `(envelope_id, occurred_at)` e um índice parcial de ativos por data. Nenhuma extensão do PostgreSQL está em uso. A última migração é `V16`.
- O repositório já tem um padrão de filtro opcional em JPQL com flags booleanas (`findFiltered`: `:filterKind = false OR ...`).
- Web: `HistoryPage.tsx` lê `from`, `to`, `page` e `includeDeleted` de `useSearchParams` e navega com `router.push('/gastos?…')`. `api.getHistory` monta a query string.
- Android: `HistoryController` (KMP, `commonMain`) guarda `month`, `page` e `includeDeleted` e chama `LedgerGateway.loadHistory(from, to, page, includeDeleted)`. `HistoryPageView` não tem total.
- Os clientes não conhecem o `userId` da pessoa autenticada. Por isso o filtro de autor é semântico (`ME`/`OTHERS`), não por UUID.
- Postgres 18 (compose e Testcontainers). `pg_trgm` é extensão *trusted*.

## Goals / Non-Goals

**Goals:**
- Estender o contrato de `/api/v1/history` de forma aditiva, com parâmetros opcionais e um campo novo na resposta, sem endpoint novo.
- Reaproveitar a regra de visibilidade existente numa única query, para não duplicar a autorização em código Java.
- Seguir TDD: os testes de integração da busca começam falhando.

**Non-Goals:**
- Não criar módulo nem serviço de busca. Nada de Elasticsearch, full-text `tsvector` ou cache.
- Não mudar `/history/summary`.
- Não adicionar autor ao seletor por pessoa (lista de participantes). Ficamos com `ME`/`OTHERS`.

## Decisions

### D1. `LIKE` sobre `lower(description)` com índice GIN trigram
Migração `V18__add_ledger_description_trgm_index.sql`:
```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX ledger_entry_description_trgm_idx
    ON ledger_entry USING gin (lower(description) gin_trgm_ops);
```
Na JPQL, o filtro é `LOWER(e.description) LIKE :pattern ESCAPE '\'`. O `pattern` é montado em Java: `trim`, `toLowerCase(Locale.ROOT)`, escape de `\`, `%` e `_`, e depois `%…%`. Usar `LOWER` + `LIKE` mantém a query em JPQL portátil, e o índice de expressão é o que o planner pode usar.
- *Alternativa*: `ILIKE` em query nativa com índice em `description`. Foi descartada porque obrigaria a reescrever `findHistory` como SQL nativo e perder o mapeamento de entidade.
- *Alternativa*: `tsvector`/`to_tsquery`. Foi descartada porque busca por palavra inteira e com stemming não encontra trechos como "farm", e o pedido é "contém".

### D2. Filtros opcionais na mesma JPQL, com flags
`findHistory` ganha `filterText/pattern`, `filterEnvelope/envelopeId` e `authorMode` (`ANY`, `ME`, `OTHERS`), seguindo o padrão de `findFiltered`. Com `ME`, a condição é `e.authorId = :userId`. Com `OTHERS`, é `e.authorId <> :userId`. Uma assinatura `default` no repositório converte nulos em flags. A lista continua com uma única query JPQL; a soma (D3) precisa de uma segunda query.
- *Alternativa*: Spring Data `Specification`. Foi descartada por ora: ela exigiria reescrever o `EXISTS` de participante em Criteria API, com mais código para o mesmo resultado. Vale reconsiderar se surgirem mais filtros.

### D3. `matchedTotal` por query de soma separada
`LedgerEntryRepository.sumHistory(...)` é SQL nativo com as mesmas condições de `findHistory` (visibilidade por dono ou participante, período, texto, verba e autor), mas força `deleted_at IS NULL` e faz `COALESCE(SUM(e.amount), 0)`. É nativo porque `amount` é mapeado com o conversor de `Money`, e o SQL nativo devolve `BigDecimal` sem depender de como o Hibernate trata `SUM` sobre um atributo convertido. O repositório já segue esse padrão em `sumAmountUpTo` e `sumAmountBetween`. A consequência é que as condições existem em duas queries, que precisam mudar juntas. A paridade é garantida pelos testes de integração, que verificam lista e soma com dono, participante, terceiro e excluídos (`HistorySearchApiIntegrationTest`). O resultado vira `Money` em BRL no service. `HistoryPage` (record do service) ganha `matchedTotal`, e `HistoryPageResponse` passa a expor `matchedTotal: {amount, currency}`. O total vem sempre, mesmo sem filtros. A UI decide quando exibir.
- *Alternativa*: somar só os itens da página. Foi descartada porque não responde "quanto gastei em X" quando há mais de uma página.

### D4. Validação na fronteira (controller/service)
- `q` vazio ou só com espaços conta como ausente. Com mais de 140 caracteres depois do `trim`, a resposta é `IllegalArgumentException` → 400. O handler existente já traduz a mensagem em português.
- `envelopeId` que não é UUID resulta em 400. Uma verba invisível não gera erro: a lista sai vazia (spec). Não é preciso consulta extra, porque a visibilidade já filtra.
- `author` é convertido para um enum interno. Valor desconhecido resulta em 400.
- O termo não é logado. Nenhum `log.*` novo recebe `q`. O nginx já opera com `access_log off`.

### D5. Web: filtros na URL e busca com debounce
`HistoryPage` lê `q`, `envelopeId` e `author` de `searchParams` e os escreve com o mesmo `router.push` usado para período e página, removendo os vazios e zerando `page`. O campo de busca usa estado local, faz *debounce* de cerca de 400 ms e também responde a Enter. O seletor de verba usa as verbas já carregadas em `envelopes` (inclui as compartilhadas). `api.getHistory` passa a receber um objeto de filtros opcional, e `HistoryPageDTO` ganha `matchedTotal`. O estado vazio distingue "nenhum gasto no período" de "nenhum gasto corresponde aos filtros".

### D6. Android: estado de filtro no `HistoryController`
`HistoryController` ganha `query: String` e `envelopeId: String?`, além de `search(text)`, `filterEnvelope(id)` e `clearFilters()`, que zeram `page` e recarregam. Trocar de mês preserva os filtros. `LedgerGateway.loadHistory` recebe um `HistoryFilter(query, envelopeId)` com default vazio, para os chamadores atuais não quebrarem. `HistoryPageView` ganha `matchedTotal: Money`. `HistoryScreen` adiciona o campo (`ImeAction.Search` + debounce com `LaunchedEffect`) e um seletor de verba. O filtro de autor fica fora do Android neste corte, para não poluir a tela pequena. A API já o suporta.

### D7. ADR-0032
Registrar em `docs/decisions/0032-busca-textual-com-pg-trgm.md` a adoção da extensão, o índice de expressão em `lower(description)`, a limitação de acentos e o critério para revisar (`unaccent` ou `tsvector`).

### Canais
- **API**: afetada (D1–D4).
- **Web**: afetado (D5).
- **Android**: afetado (D6). iOS compartilha `commonMain`, mas não é validado sem macOS/Xcode.
- **MCP**: fora. `list_expenses` continua chamando sem os parâmetros novos e só passa a receber `matchedTotal` a mais no JSON.
- **Desktop (Tauri)**: fora, porque não tem tela de histórico.

## Risks / Trade-offs

- [Condições duplicadas entre `findHistory` (JPQL) e `sumHistory` (SQL nativo)] → Os testes de integração verificam lista e soma juntas para cada papel. Qualquer mudança de visibilidade ou de filtro precisa alterar as duas queries.

- [Com parâmetros ligados, o planner pode preferir um plano genérico e ignorar o índice trigram por causa das flags `OR`] → No volume de um usuário pessoal, o filtro por `owner_id`/participante já reduz muito as linhas. O índice atende ao critério de aceite e passa a valer quando o volume crescer. Se medições mostrarem lentidão, migrar para query montada com `Specification` (D2).
- [Termos de 1 ou 2 caracteres não aproveitam trigramas] → Funcionam com varredura limitada pelo filtro de visibilidade. Não impomos mínimo para não frustrar buscas curtas como "uber".
- [`lower()` no Postgres depende da collation e `toLowerCase(Locale.ROOT)` em Java pode divergir em casos raros] → Para o português com UTF-8 os dois produzem o mesmo resultado. Há cenário de teste com "Farmácia"/"farmácia".
- [Acentos: "farmacia" não acha "farmácia"] → Limitação documentada no ADR-0032 e na proposal. Revisitar com `unaccent` se o diário de validação apontar.
- [`CREATE EXTENSION` falhar em produção por falta de privilégio] → `pg_trgm` é *trusted*, então o role dono do banco consegue criar. Validar no ambiente de produção antes do deploy (tarefa explícita).
- [Uma query extra de soma por página] → É uma agregação simples sobre o mesmo conjunto filtrado e aceitável no volume atual, sem cache (regra do projeto).

## Migration Plan

1. Deploy do backend com `V18`. O Flyway cria a extensão e o índice. O índice GIN sobre uma tabela pequena é criado em segundos. Se o volume crescer no futuro, prefira `CREATE INDEX CONCURRENTLY` em migração não transacional.
2. A API é retrocompatível, então web e Android podem sair depois do backend.
3. Rollback: os clientes antigos ignoram `matchedTotal`. Para reverter o banco, use uma migração `V18` com `DROP INDEX ledger_entry_description_trgm_idx` (a extensão pode ficar).

## Open Questions

- O `docs/privacy/inventario-de-dados.md` precisa citar o termo de busca como dado transitório? O termo não é persistido nem logado. A resposta não muda specs nem tarefas de código.
