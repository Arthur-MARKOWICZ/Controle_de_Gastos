# ADR-0029: Cliente desktop em Tauri como quarto canal

## Status

Aceito

## Data

2026-11-12

## Contexto

O hábito diário de registrar gastos no computador compete com as abas do
navegador. A fundação técnica separa os canais por uso — mobile para lançar, web
para configurar e relatar — mas não oferece um cliente de janela própria. Quem
trabalha o dia inteiro no computador precisa abrir a web, achar a aba e navegar
até a verba para lançar um gasto de trinta segundos.

Os canais existentes são API Spring (fonte das regras), web Next.js, Android KMP
e MCP stdio (ADR-023). A web autentica com cookie `HttpOnly` +
`SameSite=Strict`; mobile e MCP capturam o refresh da resposta de login e o
reenviam como `Cookie` em `POST /api/v1/auth/refresh`. O CORS de produção libera
apenas `PUBLIC_APP_URL`, de modo que um webview com origem própria
(`tauri.localhost`) não conseguiria chamar a API pelo `fetch` sem ampliar a
allowlist.

O access JWT dura cerca de 15 minutos, então o cliente precisa guardar o refresh
no dispositivo. Um aplicativo de dinheiro em desktop tem cofre de sistema
disponível (`secret-service` no Linux, Keychain no macOS, DPAPI no Windows), o
que é melhor do que o arquivo `0600` aceito para o MCP.

Este ambiente de desenvolvimento é Linux. Não há macOS/Xcode nem Windows para
declarar build verificado nas outras plataformas.

## Decisão

- Adicionar o pacote `desktop/` no monorepo: aplicativo **Tauri 2** com UI em
  **Vite + React 19 + TypeScript + CSS modules**, cliente da mesma API REST
  `/api/v1`. É um quarto cliente, não um substituto da web, do Android ou do
  MCP.
- **Nenhuma regra financeira no cliente.** Renda, saldo, carry, autorização por
  recurso e ledger permanecem na API. O desktop só apresenta e envia.
- **HTTP autenticado sai do processo Rust**, não do `fetch` do webview. O
  webview chama comandos Tauri (`login`, `verify_mfa`, `logout`, `restore`,
  `api`); o crate lê `Set-Cookie` no login, guarda o refresh, reenvia `Cookie`
  em `/auth/refresh` e `Authorization: Bearer` nas demais rotas. Em `401`,
  renova **uma** vez e repete a chamada; se falhar, limpa a sessão. O CORS de
  produção continua restrito à origem da web.
- **Access JWT só na memória do processo nativo**; o refresh (valor + nome do
  cookie) vai para o **cofre do sistema operacional**. Logout revoga na API e
  apaga a entrada. A interface recebe e-mail e DTOs, nunca o refresh nem o
  access.
- **Sem cofre disponível, o login falha** com mensagem de configuração. O
  cliente não cai para arquivo em texto claro.
- Preferência de tema `SYSTEM` | `LIGHT` | `DARK` em arquivo de configuração do
  aplicativo no dispositivo — não no cofre e não na API (emenda ao ADR-013).
- URL da API resolvida **em tempo de build**, no espírito do ADR-021:
  `API_ENV=local|prod`, `API_BASE_URL_LOCAL` e `API_BASE_URL_PROD` em
  `desktop/.env` (fora do Git). URL inválida falha o build.
- **Linux é o alvo validável.** O projeto Tauri mantém Windows e macOS no
  código, mas este repositório não declara esses builds testados. `make check`
  inclui o desktop apenas quando o toolchain está presente.
- Logs do cliente sem token, senha, código TOTP, valor ou descrição de gasto.
- Primeiro corte do produto: login e-mail/senha com MFA TOTP, visão geral do
  mês, lista de verbas e registro de gasto. Fora do corte: OAuth social,
  cadastro, recuperação de senha, relatórios, metas ricas, gastos anuais,
  participantes/convites, criar/editar verba, alterar renda, tray, atalho global
  de SO e publicação em lojas.
- Saldo e lista de verbas vêm de `GET /api/v1/ledger/summary`, **não** de
  `GET /api/v1/envelopes` — neste último, por contrato documentado, `available`
  espelha `baseAmount` e `isNegative` é sempre `false`.

## Alternativas consideradas

### Empacotar o Next.js dentro do webview

- Vantagem: reaproveitaria telas e componentes da web inteiros.
- Desvantagens: SSR e `next/link`/`next/navigation` dentro de um webview local;
  o roteador do App Router pressupõe servidor; o cookie `SameSite=Strict` não
  viaja para uma origem `tauri.localhost`.
- Rejeitado: o ganho de reuso não paga o acoplamento ao App Router.

### UI nativa em Rust (`egui`, `iced`)

- Vantagem: binário menor, sem webview e sem WebKitGTK no Linux.
- Desvantagem: joga fora os tokens visuais e a acessibilidade já testada na web;
  nenhum componente é reaproveitável.
- Rejeitado no corte.

### `fetch` no webview com CORS para a origem do Tauri

- Vantagem: dispensaria a ponte IPC; a UI falaria direto com a API.
- Desvantagens: exigiria liberar `tauri.localhost` no CORS de produção e lutar
  com `SameSite=Strict`; o refresh ficaria visível ao JavaScript.
- Rejeitado por superfície de ataque e por contrariar o CORS restrito.

### Refresh em `localStorage` do webview

- Vantagem: implementação trivial, sem cofre do sistema.
- Desvantagem: expõe o segredo de sessão a qualquer script do webview.
- Rejeitado.

### Arquivo `0600` como no MCP (ADR-023)

- Vantagem: mesmo mecanismo já em uso, sem dependência de cofre.
- Desvantagem: aceitável para um servidor stdio de uso pontual, pior para um
  aplicativo de dinheiro aberto o dia inteiro na sessão gráfica.
- Rejeitado para o desktop; o MCP continua como está.

### Stronghold com senha extra no arranque

- Vantagem: cofre criptografado independente do sistema operacional.
- Desvantagem: pede uma segunda senha a cada arranque, atrito justamente no
  fluxo que o canal existe para encurtar.
- Rejeitado no corte.

### Extrair `packages/ui` compartilhado com a web

- Vantagem: um só lugar para tokens e componentes.
- Desvantagem: refatoraria a web antes de a segunda UI existir, sem saber ainda
  o que é de fato comum.
- Adiado: no corte os tokens CSS são copiados, com a origem registrada no
  arquivo. A extração fica para um change futuro, quando as duas UIs existirem.

## Consequências

- O OpenAPI permanece a fonte de verdade dos contratos; o desktop só consome.
  Nenhuma mudança de REST, JWT, cookie, Money ou autorização na API.
- Cada interação da UI que precise da rede passa por `invoke`. Para não explodir
  a superfície IPC, os comandos ficam grosseiros: um `api` genérico em vez de um
  comando por rota.
- Os tokens visuais ficam duplicados entre `web/` e `desktop/` até a extração
  futura. O arquivo do desktop registra a data e o commit da cópia, porque o
  carregamento de Inter na web muda em change paralelo.
- WebKitGTK no Linux exige teste manual da titlebar personalizada e dos
  diálogos; efeitos visuais que o GTK descarta são evitados (sem blur/glass).
- A dependência de cofre do sistema é um ponto de falha novo: em Linux sem
  `secret-service` ativo, o canal simplesmente não autentica, por decisão.
- Quem entra apenas por Google/GitHub não usa o desktop até um change de
  handoff. `POST /api/v1/auth/mobile-handoff` já existe e é o ponto de partida
  natural.
- Rollback é não distribuir o binário: a API e os outros clientes não dependem
  do desktop. A entrada do cofre sai no logout ou ao desinstalar.
- Ampliar o corte (relatórios, criar verba, renda, OAuth, tray, atalho global de
  SO, publicação em loja) exige atualizar este ADR.
