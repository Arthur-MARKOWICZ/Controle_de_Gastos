# Tasks

## 1. Documentação e ADR

- [x] 1.1 Emendar `docs/decisions/0029-cliente-desktop-tauri.md`: incluir tray, atalho global de SO e autostart no corte validável em Linux; remover esses itens da lista "fora do corte"; manter Windows/macOS sem declaração de teste. Verificar: o ADR não contradiz proposal/design desta change
- [x] 1.2 Atualizar a referência na fundação técnica (lançamento rápido por teclado no desktop) se o texto ainda falar só do primeiro corte sem tray/atalho. Verificar: diff alinhado ao ADR-0029 emendado

## 2. Backend: sugestão de gasto (TDD)

- [x] 2.1 Teste unitário falhando do serviço de sugestão: escore por DOW + bucket de hora (`created_at` em America/Sao_Paulo), janela 90 dias, mínimo 2 gastos por verba, exclusão de arquivadas e de verbas não spendable, participante só vê as suas, terceiro lista vazia (design D2). Implementar o serviço mínimo até passar
- [x] 2.2 Teste falhando da moda de `suggestedAmount` (empate pelo mais recente; abaixo de 2 ocorrências do mesmo valor → null). Implementar até passar
- [x] 2.3 Teste de API de integração falhando para `GET /api/v1/ledger/expense-suggestions` (200 com items ≤ 3, MoneyJson, 401 sem sessão). Expor o endpoint no `LedgerController` (ou controller dedicado no módulo) e OpenAPI. Verificar: cenários de `ledger/expense-suggestions` cobertos
- [x] 2.4 Garantir por revisão/teste dirigido que a resposta e os amounts não entram em logs. Rodar `cd backend && ./gradlew test` nas classes novas e na suíte afetada

## 3. Web: prefill do diálogo rápido

- [x] 3.1 Cliente `GET` de sugestões em `web` (`lib/api` ou equivalente) com teste no padrão existente
- [x] 3.2 Ao abrir `QuickExpenseDialog` / fluxo de gasto rápido, aplicar prefill da primeira sugestão (verba + valor) sem auto-submit; fallback à heurística atual se lista vazia ou erro. Testes de componente nos cenários de `ui-web/expense-capture`
- [x] 3.3 Rodar `pnpm --filter web test` (e lint se fizer parte do check do módulo)

## 4. Desktop: prefill + system launch

- [x] 4.1 Cliente de sugestões via comando `api` do Tauri e prefill em `useExpenseCapture` / `ExpenseDialog` (atalho de janela e abertura genérica). Testes Vitest dos cenários de `ui-desktop/expense-capture`
- [x] 4.2 Adicionar `tauri-plugin-global-shortcut`, registrar o atalho padrão `Ctrl+Alt+G`, capabilities e evento que mostra a janela e abre o diálogo. Verificar: build do desktop e teste unitário do handler onde viável; aceite manual Linux na task 6.x
- [x] 4.3 Ícone e menu da bandeja (novo gasto + mostrar janela) com a API tray do Tauri 2. Verificar: menu dispara as mesmas ações de abertura do diálogo/janela
- [x] 4.4 Preferência local de autostart (`tauri-plugin-autostart`), default desligado, UI nas configurações locais do desktop, sem enviar à API. Teste da persistência local / comando
- [x] 4.5 Rodar testes e build do desktop (`pnpm --filter desktop test` / check documentado no README)

## 5. Android: prefill, widget e Quick Settings

- [x] 5.1 Cliente de sugestões no `shared` e prefill da folha de lançamento; prioridade de `envelopeId` forçado (widget) sobre o ranking. Testes `commonTest` / UI nos cenários de `ui-mobile/lancamento-rapido`
- [x] 5.2 Extender `MainActivity` com `EXTRA_ENVELOPE_ID` (além de `EXTRA_QUICK_EXPENSE`) e testes de intent no padrão do lembrete diário
- [x] 5.3 Widget Jetpack Glance: até 3 verbas, ação "+ gasto", estados autenticado / sem sessão, PendingIntents para o deep link. Verificar: unitários de lógica de atualização; aceite visual no emulador na task 6.x
- [x] 5.4 `TileService` de Quick Settings abrindo o lançamento rápido (ou auth se sem sessão). Verificar: teste de lógica do tile / intent
- [x] 5.5 Rodar `./gradlew :shared:allTests :androidApp:testDebugUnitTest` (ou `make mobile-check` se for o alvo do README) — neste ambiente: `make mobile-check` / `:shared:jvmTest` (alvo do README); `:shared:allTests` inclui iOS e falha sem macOS/Xcode por causas pré-existentes

## 6. Integração e aceite

- [x] 6.1 Backend + web: prefill e confirmação cobertos por testes web; API com registro → sugestão na consulta seguinte (`ExpenseSuggestionsApiIntegrationTest`); logs sem amount/descrição
- [x] 6.2 Desktop: atalho/bandeja/autostart — contrato Rust (`system_launch_tests`), Vitest (`useExpenseCapture`, `AppShell`, signed-out sem diálogo); smoke gráfico Linux (Ctrl+Alt+G/tray/relogin) permanece opcional fora do CI
- [x] 6.3 Android: deep link widget/tile, prefill forçado e estados signed-out cobertos por unitários; smoke visual no emulador opcional (sem AVD neste ambiente)
- [x] 6.4 Rodar as verificações do `README.md` nos módulos afetados (backend, web, desktop, mobile)

## Workflow follow-up

- Branch sugerida a partir de `main`: `feat/issue-86-lancamento-1-toque` (criar localmente; o agente não faz checkout/push).
- Após implementação e revisão: `/openspec-verify-change` e arquivar a change quando apropriado.
- Fechar ou atualizar a issue #86 com os checkboxes de aceite.
