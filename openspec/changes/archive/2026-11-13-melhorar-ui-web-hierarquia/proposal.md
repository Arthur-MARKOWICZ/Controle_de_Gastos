# Proposal

## Why

A web já cobre as tarefas do canal (configurar, histórico, relatórios), mas a hierarquia visual e os rótulos obrigam o usuário a decifrar o modelo de verbas em vez de ver o próximo gasto. A auditoria estática mostrou nav com sete destinos colidentes, visão geral duplicada, card de verba sem ação primária, barra de progresso com metáforas opostas, página `/gastos` que promete registrar e não registra, e chrome (PageHeader, Inter, Configurações) inconsistente.

## What Changes

- Reorganizar a navegação web: rótulos que não colidem, grade responsiva que acomoda todos os destinos, e indicação de página atual preservada.
- Unificar o resumo da Visão geral em um único bloco (renda + reservado + não alocado + uso) e reduzir CTAs e preview de verbas ao que o mês pede.
- Dar ao card de verba uma ação primária, uma metáfora única de progresso por natureza (inclusive ajustar o texto de FIXED, que hoje reaproveita o mesmo percentual da barra em sentido oposto), e copy alinhada (Arquivar/Encerrar).
- Tornar `/gastos` honesta: CTA de lançamento ou copy só de consulta; datas em pt-BR; gráfico de tipo que seja gráfico ou deixe de se chamar gráfico.
- Aplicar `PageHeader`/`Button` em Histórico, Relatórios, Configurações e Segurança (removendo os imports mortos de `Verbas.module.css`, cujas classes `.pageHeader`/`.eyebrow` não existem); carregar Inter de fato; erros de verba/gasto no campo, não só no topo.
- Exibir alertas persistentes de saldo negativo também na Visão geral.

### Fora de escopo

- Redesign de paleta, glassmorphism, tipografia handwritten ou troca de Inter por outra família.
- App Android/iOS, MCP e mudanças de API, persistência ou regras de saldo/autorização.
- Relatórios visuais interativos; a exportação CSV/XLSX permanece o contrato.
- Biblioteca de cache, filas ou microserviços.
- Atualizar o ADR-007 (ainda Proposto); a paleta vigente continua em `globals.css`.

## Capabilities

### New Capabilities

- `ui-web/app-shell`: navegação, rótulos, chrome compartilhado (PageHeader, tipografia, Configurações/Segurança).
- `ui-web/envelope-card`: apresentação da verba, ação primária, progresso e copy de encerramento.
- `ui-web/overview`: composição da Visão geral (um resumo, preview, alertas, CTAs).
- `ui-web/expense-history`: página `/gastos` (lançamento, datas, gráficos honestos).
- `ui-web/forms`: associação de erro ao campo nos formulários de verba, gasto e renda.

### Modified Capabilities

- Nenhuma. `openspec/specs/` ainda não tem capacidades vigentes.

## Impact

- Código: `web/src/components/AppShell`, `Dashboard`, `EnvelopeCard`, `HistoryPage`, `ReportsPage`, `SettingsPage`, `SecuritySettings`, `PageHeader`, `EnvelopeForm`, `ExpenseForm`, `PersistentAlerts`, `globals.css` / carregamento de fonte, testes de componente e `accessibility.test.tsx`.
- Canais: somente web (Next.js). API, Android e MCP ficam fora.
- ADRs: ADR-007 (UX de lançamento rápido, dialog, WCAG, anti-AI-aesthetic) e a fundação técnica (web = configuração, histórico e relatórios) restringem o recorte; nenhum ADR novo. ADR-0022 continua definindo o reinício mensal por natureza — a barra de progresso deve refletir essa regra, não alterá-la.
- Dependências: `next/font/local` com Inter versionada em `web/src/app/fonts/`; sem pacote npm extra nem ícones obrigatório neste change.
- Sem mudança de contrato REST, Money ou autorização.
- Relação com os outros changes de UI em voo: `add-tauri-desktop-client` copia os tokens de `globals.css` e depende desta decisão de fonte; `melhorar-ui-ux-mobile` não compartilha arquivo com este change.
