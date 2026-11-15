# Proposal

## Why

O histórico de lançamentos (`GET /api/v1/history`) só filtra por período. Para responder "quanto gastei na farmácia X?" a pessoa precisa rolar páginas de dez itens e somar de cabeça. A issue #94 (NEG-014, origem `reports/analise-negocio-2026-11-12.md`) pede busca textual na descrição e filtros por verba e autor, com a mesma regra de acesso já usada no histórico.

## What Changes

- `GET /api/v1/history` passa a aceitar parâmetros opcionais `q` (texto contido na descrição, sem diferenciar maiúsculas), `envelopeId` e `author` (`ME` = lançados por mim, `OTHERS` = lançados por outras pessoas). Sem eles, o comportamento atual não muda. A mudança não é breaking.
- A resposta paginada do histórico ganha `matchedTotal` (Money BRL): soma dos gastos ativos (não excluídos) que atendem a todos os filtros no período. É isso que responde "quanto gastei em X".
- Migração Flyway `V18` cria a extensão `pg_trgm` e um índice GIN trigram em `ledger_entry.description`, para a busca `ILIKE '%termo%'` não depender de varredura sequencial.
- Novo ADR-0032 registra a adoção da extensão `pg_trgm`, porque ela é dependência nova de infraestrutura do banco.
- Web: a página de histórico ganha campo de busca seletor de verba e filtro de autor (todos / por mim / por outras pessoas). Os filtros ficam na URL, junto com `from`/`to`, e a soma dos resultados aparece quando algum filtro está ativo.
- Android: o histórico ganha campo de busca e filtro de verba, e mostra a soma filtrada.
- A busca respeita a mesma visibilidade de hoje: o dono vê as entradas das suas verbas, o participante vê as das verbas compartilhadas com ele, e um terceiro não vê nada. Os filtros só restringem esse conjunto.

## Capabilities

### New Capabilities
- `ledger/history-search`: busca textual e filtros (verba, autor) no histórico de gastos da API, com total filtrado e autorização por dono/participante.
- `ui-mobile/historico`: busca e filtro de verba na tela de histórico do Android.

### Modified Capabilities
- `ui-web/expense-history`: a página de histórico passa a oferecer busca por descrição, filtros de verba e autor persistidos na URL e a soma dos resultados filtrados.

## Fora do escopo

- Tags em lançamentos. A issue condiciona isso ao diário de validação, e hoje as verbas cumprem o papel de categoria.
- Busca insensível a acentos (`unaccent`). Ela exige mais uma extensão e uma função `IMMUTABLE` própria para indexar. "farmacia" não encontra "farmácia" neste corte.
- Busca no resumo (`/history/summary`). Os cards de período e o gráfico por finalidade continuam sem filtros.
- Cliente desktop (Tauri), que ainda não tem tela de histórico, e a ferramenta MCP `list_expenses`. Os dois continuam funcionando sem os novos parâmetros.
- Ordenação por relevância. A ordem continua data desc, criação desc.
- Busca em contribuições/aportes. O histórico continua listando só `EXPENSE`.

## ADRs relacionados

- ADR-0001 (monólito modular): a mudança fica dentro do módulo `ledger`, sem módulo novo.
- ADR-0003 (privacidade desde a concepção): o termo buscado é dado financeiro e não pode ir para logs. O nginx já usa `access_log off`.
- ADR-0006 (valores monetários decimais): `matchedTotal` usa string decimal com duas casas.
- ADR-0024 (modelagem do banco, sem RLS): o isolamento continua nas queries da aplicação (`owner_id` ou participante).
- ADR-0026 (compartilhamento assimétrico): o participante vê na busca exatamente o que já vê no histórico.
- **Novo ADR-0032**: adoção de `pg_trgm` para busca textual.

## Impact

- Backend: `ledger/web/HistoryController`, `ledger/application/LedgerService.history`, `ledger/infrastructure/LedgerEntryRepository.findHistory` e nova query de soma, migração `V18__add_ledger_description_trgm_index.sql`, testes de integração com Testcontainers (`postgres:18-alpine`).
- Web: `web/src/lib/api.ts` (`getHistory`, `HistoryPageDTO`) e `web/src/components/HistoryPage/`.
- Android/KMP: `LedgerGateway.loadHistory`, `AndroidApiGateway`, `LedgerContract` (`HistoryPageView`), `HistoryController`, `HistoryScreen` e testes em `commonTest`.
- Banco: extensão `pg_trgm`, que é *trusted* desde o PostgreSQL 13. Basta o role dono do banco ter `CREATE` no banco, sem superusuário.
- Docs: `docs/decisions/0032-busca-textual-com-pg-trgm.md`. Avaliar se `docs/privacy/inventario-de-dados.md` precisa mencionar o termo de busca (ele é transitório e não é persistido).
