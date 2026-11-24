# Proposal

## Why

O hábito diário de registrar gastos no computador compete com dezenas de abas do
browser. A fundação já separa canais (web para configurar e relatar, mobile para
lançar), mas não oferece um cliente de janela própria. Um quarto cliente Tauri,
falando a mesma API, dá captura rápida por teclado sem duplicar regras
financeiras.

## What Changes

- Acrescentar o canal desktop ao monorepo: app Tauri 2 com UI em Vite + React 19
  e CSS modules, cliente da API REST `/api/v1`.
- Primeiro corte de produto: login e-mail/senha + MFA TOTP, visão geral do mês,
  lista de verbas e registro de gasto (diálogo + atalho na janela).
- Resumo e lista lidos de `GET /api/v1/ledger/summary` (fonte autoritativa de
  saldo), alertas de `GET /api/v1/alerts` com dispensa em
  `POST /api/v1/alerts/{id}/dismiss`.
- Sessão no estilo mobile/MCP: access JWT em memória no processo nativo; refresh
  opaco no keyring do SO; renovação em `401` uma vez. A UI webview não vê o
  refresh.
- Tema `SYSTEM` / `LIGHT` / `DARK` só no dispositivo, alinhado ao ADR-013.
- Documentar o canal: ADR novo, emendas na fundação, ADR-001, ADR-013 e
  inventário de privacidade. Linux é o alvo validável; Windows e macOS ficam no
  código Tauri sem afirmação de teste neste ambiente.
- Reusar tokens visuais da web (teal, Inter, `tabular-nums`, naturezas de
  verba). Shell, navegação e densidade são próprios do desktop.

### Fora de escopo

- Substituir a web, o Android ou o MCP; o desktop é cliente adicional.
- Regras de renda, saldo, autorização ou ledger em Rust.
- OAuth Google/GitHub, recuperação de senha e cadastro (abrem no browser quando
  necessário; cadastro permanece na web).
- Tray, atalho global de SO, paleta `Ctrl+K` e menu nativo completo.
- Relatórios, metas ricas, gastos anuais, participantes/convites, criar/editar
  verba, alterar renda.
- Extrair `packages/ui` compartilhado com a web (só tokens copiados ou um CSS
  mínimo).
- Glassmorphism, paleta crypto, troca de Inter, cache, filas ou microserviços.
- Publicação em lojas (Flathub, Microsoft Store, App Store).

## Capabilities

### New Capabilities

- `ui-desktop/session`: autenticação e-mail/senha, desafio MFA, restauração e
  encerramento de sessão no cliente desktop.
- `ui-desktop/app-shell`: janela, titlebar, navegação do corte, tema local,
  atalhos de teclado e chrome acessível.
- `ui-desktop/overview`: resumo do mês (renda, alocado, não alocado, alertas de
  saldo negativo) para o titular autenticado.
- `ui-desktop/envelopes`: listagem das verbas do mês visíveis ao usuário
  (proprietário ou participante).
- `ui-desktop/expense-capture`: registro de gasto em verba autorizada, inclusive
  com saldo negativo, sem arredondar BRL.

### Modified Capabilities

- Nenhuma. `openspec/specs/` ainda não tem capacidades vigentes.

## Impact

- Código novo em `desktop/` (frontend Vite/React + crate Rust Tauri). Sem
  mudança de contrato REST, Money ou autorização na API.
- Contratos consumidos: `POST /api/v1/auth/login`, `/auth/mfa/verify`,
  `/auth/refresh`, `/auth/logout`, `GET /api/v1/ledger/summary`,
  `GET /api/v1/alerts`, `POST /api/v1/alerts/{id}/dismiss` e
  `POST /api/v1/envelopes/{id}/entries`. `GET /api/v1/envelopes` **não** é usado
  para saldo: por contrato documentado, ali `available` espelha `baseAmount` e
  `isNegative` é sempre `false`.
- Ordem entre changes de UI: a cópia de tokens CSS depende de
  `melhorar-ui-web-hierarquia` (que muda o carregamento de Inter na web); as
  demais tarefas são independentes de `melhorar-ui-ux-mobile`.
- Chamadas HTTP autenticadas saem do processo Rust (como MCP/mobile), não do
  `fetch` do webview: CORS de produção permanece a origem da web.
- URL da API em tempo de build, no espírito do ADR-021 (`local` / `prod`).
- Dependências novas só no pacote desktop: Tauri 2, Vite, React 19, Phosphor
  (outline), plugin de secrets/keyring. Sem Redis, cache ou fila.
- ADRs: novo ADR-0029 (canal desktop); atualizar ADR-001 (clientes do monorepo),
  ADR-013 (store de tema no desktop), fundação técnica e `openspec/config.yaml`
  (lista de canais). ADR-005/011 (sessão), ADR-006 (Money), ADR-007 (lançamento
  e alerta sem bloqueio), ADR-019 (MFA) restringem o comportamento.
- Privacidade: inventário ganha o refresh no keyring do dispositivo; logs do
  cliente sem token, senha, valor ou descrição de gasto.
- Verificação: testes do frontend desktop (vitest) e do adapter Rust de sessão;
  `make check` passa a incluir o cliente quando o toolchain Rust estiver
  presente. Linux validado; Windows/macOS não declarados testados.
