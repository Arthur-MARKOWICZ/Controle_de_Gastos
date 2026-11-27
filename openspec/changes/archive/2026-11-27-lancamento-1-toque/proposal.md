# Proposal

## Why

Quanto menor o atrito, maior a chance de lançar todo dia. Hoje registrar um
gasto exige abrir o app e navegar até o formulário; no desktop o atalho
`Ctrl+N` só funciona com a janela focada, e o ADR-0029 deixou tray, atalho
global de SO e autostart fora do primeiro corte. A issue #86 (NEG-004) fecha o
"ciclo do hábito" junto com recorrentes e lembrete diário
(`reports/analise-negocio-2026-11-12.md`).

## What Changes

- **Sugestão de verba e valor na API:** consulta autenticada que, a partir do
  histórico de gastos do usuário (frequência por dia da semana e horário em
  `America/Sao_Paulo`), devolve as verbas mais prováveis e um valor sugerido
  por verba. Regra determinística, sem ML e sem cache.
- **Android — widget e Quick Settings:** widget Jetpack Glance com atalho
  “+ gasto” e as três verbas mais usadas (via sugestão); tile em Quick Settings
  que abre o lançamento rápido já existente (`EXTRA_QUICK_EXPENSE`).
- **Desktop — lançamento pelo sistema:** `tauri-plugin-global-shortcut` (ex.:
  `Ctrl+Alt+G`) abre o mini-formulário mesmo com a janela em segundo plano;
  ícone na bandeja (tray) com ação de novo gasto; opção de autostart no login
  da sessão. Amplia o corte do ADR-0029.
- **Clientes consomem a sugestão:** ao abrir o formulário rápido (web, Android,
  desktop), pré-selecionar verba e valor sugeridos quando houver histórico
  suficiente; a pessoa pode alterar antes de salvar.
- **ADR-0029:** emenda aceita para incluir tray, atalho global de SO e
  autostart no corte validável em Linux.

### Fora do escopo

- Machine learning, modelos remotos ou ranking personalizado além da regra de
  frequência descrita no design.
- Cache (Redis, Caffeine, Spring Cache ou equivalente) da sugestão.
- Widget / Quick Settings / Glance no iOS; iOS permanece no KMP sem validação
  neste ambiente.
- Publicação em lojas (Play Store, Flathub, etc.).
- Autostart ou tray em Windows/macOS como alvo validável (código pode existir;
  validação declarada só em Linux, no espírito do ADR-0029).
- Lançamento com um único toque sem confirmação (sem “undo”); o formulário
  mínimo continua a exigir confirmar/salvar.
- Alterar o lembrete diário ou Web Push (NEG-003 já entregue); o tile/widget
  apenas reutiliza o deep link de lançamento rápido.
- MCP: não ganha ferramenta nova de sugestão neste change (o agente já registra
  gasto; a pessoa não usa MCP como superfície de 1 toque).
- PWA / NEG-008 e demais itens NEG-005+.

### ADRs envolvidos

- **ADR-0029:** precisa ser emendado — tray, atalho global e autostart saem do
  "fora do corte" e entram no produto desktop validável em Linux.
- **ADR-0007:** mantém lançamento rápido, saldo negativo permitido e BRL exato;
  a sugestão só pré-preenche, não muda a regra de registro.
- **ADR-0001:** monólito modular; a sugestão vive no módulo `ledger`, sem
  serviço novo.
- **ADR-0003 / privacidade:** a consulta de sugestão não registra valores nem
  descrições em logs; resposta só para o autenticado sobre verbas que ele pode
  gastar.
- **ADR-0004:** Android validável; iOS preservado sem declaração de teste.
- **ADR-0033:** sem conflito; lembrete local e tray são preocupações distintas.

## Capabilities

### New Capabilities
- `ledger/expense-suggestions`: consulta autenticada de verbas e valores
  sugeridos para lançamento rápido, com base na frequência do histórico.
- `ui-mobile/home-screen-shortcuts`: widget Glance e tile de Quick Settings no
  Android que abrem o lançamento rápido, com as três verbas mais usadas.
- `ui-desktop/system-launch`: atalho global de SO, ícone na bandeja e autostart
  no cliente Tauri (Linux validável).
- `ui-web/expense-capture`: pré-seleção de verba e valor sugeridos no diálogo
  rápido de gasto da web.

### Modified Capabilities
- `ui-mobile/lancamento-rapido`: ao abrir a folha a partir do widget, do tile
  ou do atalho in-app, pré-selecionar verba e valor da sugestão quando
  disponíveis.
- `ui-desktop/expense-capture`: ao abrir o diálogo via system-launch (ou
  `Ctrl+N` na janela), pré-selecionar verba e valor da sugestão quando
  disponíveis.

## Impact

- **Backend:** novo endpoint (ou consulta) em `ledger` + testes TDD; SQL sobre
  `ledger_entry` filtrado por autor/visível e `EXPENSE`; sem tabela nova se a
  agregação em leitura bastar.
- **API:** contrato JSON com verbas ordenadas, `suggestedAmount` em string
  decimal de duas casas; OpenAPI atualizado.
- **Android:** módulo `androidApp` (Glance, `TileService`), deep link para
  `MainActivity.EXTRA_QUICK_EXPENSE` (e opcionalmente `envelopeId`).
- **Desktop:** plugins Tauri (`global-shortcut`, tray nativo, autostart),
  comandos Rust e UI do `ExpenseDialog` / `useExpenseCapture`.
- **Web:** `QuickExpenseDialog` e fluxo que o abre.
- **Docs:** emenda ao `docs/decisions/0029-cliente-desktop-tauri.md`.
- **Métrica de produto (não automatizada neste change):** tempo mediano abrir→salvar ≤ 5 s,
  medida fora do CI.
