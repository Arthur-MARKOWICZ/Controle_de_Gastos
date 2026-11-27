# Proposal

## Why

A API do dia 1 de investimentos (#75–#79) já permite cadastrar lotes B3, CDB
prefixado e ler o resumo com totais e P&L, mas nenhum cliente expõe essa
área. Sem a UX, o titular não consegue cumprir o intent
(`docs/intent/rastreamento-investimentos.md`) nem o aceite da issue #80:
cadastrar posições e ver valores atualizados sem passar pelo fluxo de verbas.
Esta change fecha o dia 1 nos canais web e Android.

## What Changes

- Nova área **Carteira** nos clientes web e Android, isolada das telas de
  orçamento (verbas, histórico, metas, visão geral de gastos).
- **Hub único** alimentado por `GET /api/v1/investments/summary`: totais,
  P&L, ações agregadas por ticker e CDBs; cadastro de ação e de CDB em
  dialog/sheet; exclusão com confirmação destrutiva.
- **Navegação**: na web, destino de 1º nível na sidebar (`/carteira`, rótulo
  "Carteira"); no Android, entrada em **Mais** (sem 6ª aba na bottom bar),
  abrindo a mesma experiência de hub.
- **Rótulos obrigatórios do ADR-0034 / #79**: quando `complete` é `false`, a
  UI indica resumo **parcial**; valores de CDB aparecem como **estimado**
  (bruto, sem IR/IOF).
- Empty state quando não há posições: CTAs para cadastrar ação ou CDB.
  Qualquer usuário autenticado vê e gerencia a **própria** carteira (não há
  compartilhamento no módulo).
- Clientes chamam apenas os endpoints já existentes
  (`/investments/stocks`, `/investments/cdbs`, `/investments/summary`).
  Sem mudança de contrato de API.

### Fora do escopo

- MCP e desktop (ADR-0034, dia 1).
- Fundos, CDB pós-fixado, IR/IOF, import de corretora, gráficos elaborados.
- Edição de posição (a API não tem PATCH; só create/list/delete).
- Aba própria na bottom bar do Android.
- Lançamento automático em envelopes ao registrar investimento.
- Alterar backend, ADRs ou inventário LGPD.
- iOS validado (apenas preservar o alvo KMP; Android é o validável).

## Capabilities

### New Capabilities

- `ui-web/carteira`: hub autenticado de carteira na web — resumo, cadastro de
  ação e CDB, exclusão, empty state e rótulos de parcial/estimado, sem
  misturar com verbas.
- `ui-mobile/carteira`: mesma capacidade no Android (KMP), alcançável a
  partir de Mais, com o mesmo comportamento de hub e as mesmas restrições
  de isolamento.

### Modified Capabilities

- `ui-web/app-shell`: incluir "Carteira" como destino primário distinto dos
  rótulos de orçamento, alcançável na navegação principal e marcado como
  página atual em `/carteira`.

## Impact

- **ADRs**: ADR-0034 (área própria web/Android, isolada do orçamento, só
  titular da própria carteira, CDB rotulado estimado, MCP/desktop fora);
  ADR-0028 (complementar — não misturar com `wealthAllocation`); ADR-0006
  (dinheiro como string decimal na UI); ADR-0003 (sem valores detalhados em
  logs de cliente). Nenhum ADR precisa ser alterado.
- **Web**: novo destino no `AppShell`, rota `/carteira`, cliente HTTP dos
  endpoints de investments, componentes de hub e formulários.
- **Android**: nova `Route`, entrada em `MoreScreen`, tela/hub Compose,
  gateway/contrato HTTP no shared.
- **API**: nenhuma mudança; pressupõe #76–#79 presentes na branch
  `investimentos`.
- **Specs**: duas capacidades novas de UI; delta em `ui-web/app-shell`.
  Mobile não altera a regra de “rótulos cabem em 360 dp” porque Carteira não
  entra na bottom bar.
