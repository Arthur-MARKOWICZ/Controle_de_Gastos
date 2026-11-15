# Design

## Context

Canais atuais: API Spring, web Next.js, Android KMP e MCP stdio. A web autentica
com cookie `HttpOnly` + `SameSite=Strict` no browser; mobile e MCP capturam o
refresh e reenviam `Cookie` no `POST /auth/refresh`. `AppShell` e páginas web
dependem de `next/link` e `next/navigation`. CORS de produção é só
`PUBLIC_APP_URL`. Ver `proposal.md` para a motivação. Requisitos observáveis:
`specs/ui-desktop/*/spec.md`.

Restrições que o desenho não pode relaxar: Money em string decimal com duas
casas; autorização na API; sem cache; sem regras financeiras no cliente; logs
sem token, senha, valor ou descrição.

## Goals / Non-Goals

**Goals:**

- Isolar HTTP autenticado no processo Rust, para o webview não depender de CORS
  nem de cookie do browser.
- Manter a UI em React + CSS modules com os tokens visuais da web, sem importar
  o App Router.
- Tratar Linux como evidência de aceite; Windows e macOS compilam no projeto
  Tauri, mas não entram no `make check` deste ambiente.

**Non-Goals:**

- Novo módulo Spring, novo endpoint ou alteração de JWT/cookie.
- Biblioteca compartilhada `packages/ui` neste change.
- Empacotar o Next.js dentro do webview.

## Decisions

### 1. Pacote `desktop/` no monorepo (Tauri 2 + Vite + React 19)

A UI vive em `desktop/` (Vite) e o binário em `desktop/src-tauri/`. É o mesmo
arranjo de `web/` e `mcp/`: aplicativo separado, sem workspace pnpm obrigatório
na raiz.

Alternativas: embutir o Next no Tauri (SSR e `next/link` no webview); UI nativa
em Rust (`egui`/`iced`, sem reaproveitar tokens nem a11y web já testada).
Rejeitadas no corte.

### 2. HTTP da sessão e dos recursos sai do Rust

O webview chama comandos Tauri (`login`, `verify_mfa`, `logout`, `restore`,
`api`). O crate faz o `fetch` equivalente ao MCP: lê `Set-Cookie` no login,
guarda o refresh, manda `Cookie` no refresh, manda `Authorization: Bearer` nas
demais rotas. Em `401`, uma renovação; se falhar, sessão limpa.

A UI recebe e-mail e DTOs, nunca o refresh. O access JWT permanece na memória
do processo Rust.

Alternativas: `fetch` no webview com CORS para `tauri.localhost` (exige
allowlist de produção e luta com `SameSite=Strict`); refresh no `localStorage`
(expõe o segredo ao JS). Rejeitadas.

### 3. Refresh no keyring do SO; tema em arquivo local

Refresh (valor + nome do cookie) no cofre do sistema (`secret-service` no
Linux; equivalentes nas outras plataformas). Logout apaga a entrada.

Tema `SYSTEM` | `LIGHT` | `DARK` em arquivo de config do app no dispositivo,
não no keyring e não na API — emenda ao ADR-013.

Alternativas: arquivo `0600` como o MCP (mais simples, pior para um app de
dinheiro); Stronghold com senha extra (atrito no arranque). Rejeitadas no
corte.

### 4. URL da API no build, no espírito do ADR-021

`desktop/.env` ignorado pelo Git, com `API_ENV=local|prod`,
`API_BASE_URL_LOCAL` e `API_BASE_URL_PROD`. Local: `http://localhost:8080`.
Prod: origem HTTPS sem porta. Falha no build se a URL for inválida.

### 5. Visual: tokens copiados, shell próprio, Phosphor outline

Copiar as variáveis de `web/src/app/globals.css` (teal, superfícies, propósitos,
perigos). Inter fica na pilha CSS, com fallback de sistema — sem requisição a
Google Fonts em tempo de execução (CSP Tauri e privacidade). Atenção: o change
`melhorar-ui-web-hierarquia` passa a web a carregar Inter por `next/font`
(arquivo auto-hospedado no build, também sem requisição a Google em runtime).
Portanto o desktop não deve afirmar paridade com "a web hoje": ou mantém só a
pilha de sistema, ou versiona o arquivo da fonte em `desktop/`. Ícones Phosphor `regular` 20px:
`House`, `Wallet`, `Plus`, `WarningCircle`, `Gear`. Não usar o ícone
`Envelope` (é e-mail).

Janela mínima 1100×700; titlebar custom na cor da sidebar; lista densa de
verbas; diálogo de gasto no mesmo contrato visual da web (máscara pt-BR, envio
`"45.90"`). Atalho na janela: `Ctrl+N` (e equivalente no desktop) abre a
captura. Densidade maior que a web; sem blur/glass.

### 6. Saldo e lista de verbas vêm de `/ledger/summary`, não de `/envelopes`

`GET /api/v1/envelopes` é o contrato de **configuração**: por decisão
documentada no próprio controller, ali `available` espelha `baseAmount`,
`isNegative` é sempre `false` e `goalProgress` é nulo. A fonte autoritativa de
saldo com carry e ledger é `GET /api/v1/ledger/summary`, que já devolve
`income`, `allocated`, `unallocated`, `usagePct` e a lista `envelopes` com
`available`, `isNegative` e `role` do mês.

Consequência: visão geral e lista de verbas consomem **a mesma** resposta de
`/ledger/summary` por mês selecionado. Isso não é cache: é uma requisição por
mês, cujo resultado vive no estado do componente enquanto a tela está aberta, e
é refeita após cada lançamento.

Alternativa rejeitada: ler `/envelopes` para a lista e `/ledger/summary` para o
resumo — mostraria `baseAmount` como se fosse disponível e nunca sinalizaria
verba negativa, contrariando `specs/ui-desktop/envelopes`.

### 7. Documentação do canal no mesmo change

ADR-0029 descreve o cliente desktop (Tauri 2, HTTP nativo, keyring, Linux
primeiro). Emendar fundação técnica, ADR-001, ADR-013, inventário de
privacidade, README e o `context` de `openspec/config.yaml` para incluir o
canal. Sem ADR, o canal contradiz a fonte de verdade.

### 8. Testes e verificação

TDD no adapter de sessão Rust (cookie, refresh único, logout apaga, log sem
segredo) e nos componentes React (login/MFA, lista, captura, atalho, alerta
negativo, tema). `make desktop-check` roda vitest e `cargo test`. `make check`
só inclui desktop se `cargo` estiver no PATH; caso contrário, o README declara
a omissão. Sem cache de HTTP.

## Risks / Trade-offs

- **[Risco] WebKitGTK no Linux** → titlebar custom e diálogos precisam de teste
  manual neste ambiente; evitar efeitos visuais que o GTK descardeia.
- **[Risco] Keyring ausente (Linux sem secret-service)** → falhar o login com
  mensagem de configuração, não cair para arquivo claro.
- **[Risco] Duplicação de tokens CSS com a web** → aceitável no corte; extração
  compartilhada só num change futuro, quando as duas UIs existirem.
- **[Risco] Access JWT só no Rust** → cada clique vira `invoke`; manter os
  comandos grosseiros (um `api` genérico) para não explodir a superfície IPC.
- **[Trade-off] Sem OAuth no v1** → quem só entra com Google/GitHub não usa o
  desktop até um change de handoff. `POST /api/v1/auth/mobile-handoff` já existe
  e é o ponto de partida natural desse change futuro. Registrado como fora de
  escopo.
- **[Risco] Três changes de UI em voo** (`melhorar-ui-web-hierarquia`,
  `melhorar-ui-ux-mobile`, este) → a cópia de tokens da tarefa 2.3 deve ser
  feita depois do change da web, ou ressincronizada; nenhuma outra tarefa deste
  change depende dos outros dois, e nenhum arquivo é compartilhado.

## Migration Plan

1. Publicar ADR-0029 e emendas documentais no mesmo change da implementação.
2. Entregar `desktop/` ao lado de `web/`; nenhum deploy de VPS muda.
3. Rollback: não distribuir o binário. A API e os outros clientes não dependem
   do desktop. Remover a entrada do keyring no logout ou desinstalando o app.

## Open Questions

- Nome do binário/pacote Linux (`.desktop`) pode seguir `verbas` ou
  `controle-gastos` na implementação, sem alterar os requisitos.
