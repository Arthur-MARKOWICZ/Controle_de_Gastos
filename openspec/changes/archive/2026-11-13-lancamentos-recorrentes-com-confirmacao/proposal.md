# Proposal

## Why

Grande parte dos gastos do mês é previsível (aluguel, internet, assinaturas, aporte mensal), mas todo lançamento ainda é digitado à mão. A verba `FIXED` reinicia o valor-base a cada mês (ADR-0022), mas o gasto em si continua manual, e isso desestimula o hábito diário (issue #84 / NEG-002, `reports/analise-negocio-2026-11-12.md`). Lançar automaticamente um gasto que não aconteceu (assinatura cancelada) distorceria o saldo; por isso a v1 gera **pendências de confirmação**, preservando o princípio de registrar só gastos reais.

## What Changes

- Nova entidade **recorrência** no módulo `ledger`: verba, tipo (`EXPENSE` ou `CONTRIBUTION`), valor sugerido, dia do mês (1–31), descrição e flag de ativa.
- **Somente o proprietário da verba** cria, edita, desativa, vê e confirma recorrências e pendências. Participantes e terceiros não enxergam recorrências; participantes continuam vendo os lançamentos já confirmados na verba.
- Job diário (`@Scheduled`, já habilitado no backend) gera **uma ocorrência pendente por recorrência e competência**, de forma idempotente, quando a data prevista chega. Dia 31 (ou 29/30) em mês curto vira o último dia do mês. Dias perdidos são recuperados só dentro do mês corrente; não há preenchimento retroativo de meses anteriores.
- O proprietário **confirma** (com valor e data ajustáveis; o valor da recorrência é só sugestão), ou **descarta** a pendência. Só a confirmação cria um lançamento no ledger, pelo mesmo caminho do registro manual (saldo, alertas, meta, auditoria).
- Pendências de competências já encerradas continuam listadas como **vencidas** até serem confirmadas ou descartadas.
- Arquivar a verba desativa suas recorrências e descarta as pendências abertas.
- Visão geral exibe pendências do mês e vencidas na web, no Android e no desktop, com CRUD de recorrências nos três clientes.
- Novo ADR-0031 registrando modo pendente × automático, idempotência por competência, ausência de backfill e tabelas separadas do `ledger_entry`.

### Fora do escopo

- Lançamento **automático** sem confirmação (fica para evolução, depois da métrica).
- Periodicidades diferentes de mensal (semanal, anual, "a cada N meses"); gastos anuais seguem no fluxo do ADR-0016.
- Recorrências criadas por participantes de verbas compartilhadas.
- Canal **MCP** (sem ferramentas novas neste change).
- Push/lembrete de pendência (issue #85 / NEG-003); o alerta fica só na visão geral.
- `ShedLock` ou execução em múltiplas instâncias; cache, filas ou microserviços.
- Painel da métrica; a métrica é obtida por consulta (ocorrências confirmadas ÷ lançamentos do mês).

## Capabilities

### New Capabilities
- `ledger/recurring-entries`: cadastro de recorrências mensais por verba, geração idempotente de pendências por competência, confirmação/descarte e listagem de pendentes e vencidas via API, com autorização exclusiva do proprietário.
- `ui-web/recurring-entries`: gestão de recorrências e bloco de pendências/vencidas na visão geral da web.
- `ui-mobile/recurring-entries`: gestão de recorrências e pendências no app Android (KMP).
- `ui-desktop/recurring-entries`: gestão de recorrências e pendências no cliente desktop Tauri.

### Modified Capabilities
- (nenhuma — não há specs principais publicadas em `openspec/specs/`)

## Impact

- **Backend:** `ledger` (domínio, repositórios, serviço, job, controller), nova migration Flyway `V17`, `TitularRightsService` (exportação inclui recorrências e ocorrências), reação ao arquivamento de verba em `envelopes`.
- **API nova:** `/api/v1/envelopes/{id}/recurring-entries` (CRUD) e `/api/v1/recurring-occurrences` (listar pendentes/vencidas, confirmar, descartar). Contrato público novo → coberto pelo ADR-0031.
- **Clientes:** web (`web/src/app/dashboard.tsx`, área de verbas), Android (`mobile/shared` + `androidApp`), desktop (`desktop/src/screens`).
- **Documentação:** `docs/decisions/0031-*.md`, `docs/privacy/inventario-de-dados.md`, `docs/ideas/fundacao-tecnica.md` se citar o ledger só manual.
- **ADRs relacionados:** ADR-0001 (monólito modular), ADR-0006 (dinheiro decimal), ADR-0007 (registro de gastos e saldo), ADR-0022 (saldo mensal sem carry), ADR-0023 (exclusão do titular e FKs de autoria), ADR-0026 (compartilhamento assimétrico: só o dono aporta), ADR-0027 (auditoria de edições do ledger), ADR-0030 (padrão de testes). Nenhum precisa ser alterado; o ADR-0031 os complementa.
- **Dependências:** nenhuma nova (Spring Scheduling já está habilitado).
