# Tasks

## 1. Documentação do canal

- [x] 1.1 Escrever `docs/decisions/0029-cliente-desktop-tauri.md` (Tauri 2, HTTP no Rust, keyring, Linux primeiro, sem regras financeiras no cliente) e verificar que as seções batem com os ADRs existentes (Status, Contexto, Decisão, Alternativas, Consequências)
- [x] 1.2 Emendar `docs/ideas/fundacao-tecnica.md`, `docs/decisions/0001-monorepo-e-monolito-modular.md`, `docs/decisions/0013-tema-adaptativo-e-preferencia-local.md`, `docs/privacy/inventario-de-dados.md`, `README.md` e o `context` de `openspec/config.yaml` para incluir o quarto cliente e o store local de tema; verificar que a lista de canais cita desktop (Tauri) e que o inventário menciona o refresh no keyring

## 2. Esqueleto `desktop/`

- [x] 2.1 Criar `desktop/` com Vite + React 19 + TypeScript + CSS modules e `desktop/src-tauri/` (Tauri 2), janela mínima 1100×700, e verificar que `desktop/package.json` e `desktop/src-tauri/Cargo.toml` existem
- [x] 2.2 Versionar `desktop/.env.example` (`API_ENV`, `API_BASE_URL_LOCAL`, `API_BASE_URL_PROD`) e ignorar `desktop/.env`; verificar que o build falha com URL inválida e aceita `http://localhost:8080` em `local`
- [x] 2.3 Copiar os tokens de `web/src/app/globals.css` para o CSS do desktop (teal, superfícies, propósitos, perigo, `tabular-nums`, Inter com fallback de sistema, sem Google Fonts em tempo de execução) e verificar no arquivo que `--accent` e `--purpose-limit` coincidem com a web; registrar no CSS do desktop a data/commit da cópia, porque o change `melhorar-ui-web-hierarquia` altera o carregamento da fonte na web

## 3. Sessão nativa (TDD)

- [x] 3.1 Escrever teste falhando do parser de `Set-Cookie` de login (nome + valor do refresh, access só no corpo) e verificar que o teste falha antes da implementação
- [x] 3.2 Implementar persistência do refresh no keyring e verificar que o teste de round-trip passa e que o access token não é gravado no cofre
- [x] 3.3 Escrever teste de renovação: `401` dispara um `POST /api/v1/auth/refresh` com `Cookie` e uma única repetição; verificar que o teste cobre sucesso e falha (sessão limpa)
- [x] 3.4 Implementar logout (revoga na API, apaga keyring, próxima restauração vai ao login) e verificar o teste correspondente
- [x] 3.5 Garantir que logs do adapter omitem senha, tokens e código TOTP e verificar com um teste que captura a saída
- [x] 3.6 Sem secret-service no Linux, o login falha com mensagem de configuração (não grava arquivo claro); verificar o teste desse ramo

## 4. Ponte IPC e API

- [x] 4.1 Expor comandos Tauri `login`, `verify_mfa`, `logout`, `restore` e `api` devolvendo só e-mail/DTOs; verificar testes Rust de que o refresh nunca entra na resposta IPC
- [x] 4.2 `api` reenvia `Authorization: Bearer` e, em `401`, renova uma vez; verificar teste com servidor fake cobrindo `GET /api/v1/users/me` e `POST /api/v1/envelopes/{id}/entries`

## 5. Login e MFA (UI)

- [x] 5.1 Tela de e-mail/senha: sucesso sem MFA entra na área autenticada; credencial inválida mostra mensagem genérica; verificar testes React desses dois cenários
- [x] 5.2 Desafio TOTP: código válido conclui a sessão; inválido permanece no desafio; verificar testes React
- [x] 5.3 Arranque com refresh válido restaura a sessão; sem refresh mostra login; verificar teste de restauração

## 6. App shell e tema

- [x] 6.1 Shell autenticado com titlebar na cor da sidebar, navegação Visão geral e Verbas, `aria-current` na tela atual, skip link para `#conteudo`; verificar teste de navegação e skip link
- [x] 6.2 Tema `SYSTEM` / `LIGHT` / `DARK` persistido só no arquivo local do app, sem chamada à API; verificar testes de escolha escura e de fallback ao sistema
- [x] 6.3 Logout no rodapé volta ao login; verificar teste do fluxo Sair

## 7. Visão geral

- [x] 7.1 Resumo do mês via `GET /api/v1/ledger/summary` (renda, alocado, não alocado como strings `"8000.00"`, mais `envelopes`) e seletor de mês `America/Sao_Paulo`; verificar testes de mês atual, troca de mês e renda não configurada
- [x] 7.2 Alertas de saldo negativo via `GET /api/v1/alerts` com nome da verba em texto (não só cor); verificar que o alerta de "Combustível" aparece em texto e que outro titular não vê alerta alheio
- [x] 7.3 Dispensar alerta via `POST /api/v1/alerts/{id}/dismiss` e recarregar a lista; verificar teste de que o alerta dispensado sai da visão geral e que a falha do POST mantém o alerta visível com mensagem de erro

## 8. Lista de verbas

- [x] 8.1 Lista densa do mês a partir do campo `envelopes` de `GET /api/v1/ledger/summary` (nome, natureza, `available`, `isNegative`, `role`), **não** de `GET /api/v1/envelopes` — onde `available` espelha `baseAmount` e `isNegative` é sempre `false` por contrato documentado; verificar testes de dono, participante e ausência de verba de terceiro
- [x] 8.2 Verba negativa mostra texto + ícone `WarningCircle` e mantém ação de registrar gasto; verificar teste de `"Lazer"` com `available` `"-15.00"` e `isNegative` verdadeiro vindos do resumo
- [x] 8.3 Estado vazio sem verbas, sem valores inventados; verificar teste da lista vazia
- [x] 8.4 Ícones Phosphor outline 20px (`House`, `Wallet`, `Plus`, `WarningCircle`, `Gear`); verificar que o código não importa `Envelope`

## 9. Captura de gasto

- [x] 9.1 Escrever primeiro os testes falhando da máscara pt-BR e da serialização (`"45,90"` digitado → `"45.90"` enviado; valor sem duas casas não chama a API; descrição ≤140), depois implementar o diálogo de gasto com envio `BRL` e data de hoje em `America/Sao_Paulo`, e verificar que os testes passam
- [x] 9.2 Participante registra `"20.00"`; recusa da API para terceiro mostra erro e não afirma sucesso; verificar os dois testes
- [x] 9.3 Escrever o teste falhando do saldo projetado (`"10.00"` − `"25.00"` = `"-15.00"`, sem arredondar e sem bloquear o envio), implementar o cálculo de apresentação e o aviso em texto, e verificar que o teste passa
- [x] 9.4 Submit inválido foca o resumo de erro no topo e mantém erro inline; Escape/cancelar devolve o foco ao gatilho; verificar testes de a11y
- [x] 9.5 Atalho `Ctrl+N` (e equivalente) na janela focada abre a captura quando há verba gastável; verificar teste do atalho

## 10. Verificação integrada

- [x] 10.1 Acrescentar `make desktop-test` / `make desktop-check` (vitest + `cargo test`) e incluir no `make check` somente se `cargo` estiver no PATH; verificar que `make desktop-check` passa neste Linux e que o README descreve a omissão quando não há Rust
- [x] 10.2 Rodar `make check` (backend, web, mcp e desktop se houver cargo) e verificar saída de sucesso sem regressão dos outros canais

## Workflow follow-up

- Arquivar o change depois da revisão humana e da implementação aceita.
- Não fazer commit, push ou PR neste fluxo.
