# Tasks

## 1. Cliente HTTP e tipos (web)

- [x] 1.1 Testes falhando em `web/src/lib/api.test.ts` (ou arquivo dedicado) para `getPortfolioSummary`, `registerStockLot`, `deleteStockLot`, `registerCdb` e `deleteCdb` contra os paths `/api/v1/investments/summary|stocks|cdbs`, com dinheiro como string de duas casas. Implementar os métodos em `web/src/lib/api.ts` até passarem
- [x] 1.2 Tipar DTOs do summary (complete, totais, stocks com lots, cdbs) sem usar number para dinheiro. Verificar: TypeScript compila e os testes de 1.1 passam

## 2. Navegação e hub web

- [x] 2.1 Teste falhando do `AppShell`: destino "Carteira" visível, rótulo distinto, `aria-current` em `/carteira`. Incluir `carteira` em `Destination` e na lista de destinos (antes de Configurações). Verificar: `AppShell.test.tsx` passa
- [x] 2.2 Criar rota `/carteira` com `ProtectedPage`, `AppShell current="carteira"` e `PageHeader` (um `h1` "Carteira"). Teste de página/título falhando, depois verde. Verificar: visitante sem sessão não vê o hub
- [x] 2.3 Testes falhando do hub: empty state com CTAs; totais e listas a partir do summary; rótulo **parcial** quando `complete` false; CDB rotulado **estimado**; `currentValue`/`pnl` null sem inventar número. Implementar o componente do hub até passarem
- [x] 2.4 Testes falhando dos formulários em `Dialog`: cadastrar ação e CDB (sucesso refresca summary; 400 mostra erro); exclusão com `ConfirmDialog` de risco e cancelamento sem DELETE. Implementar até passarem. Verificar: fluxo feliz não navega para verbas/histórico

## 3. Android — contrato, rota e hub

- [x] 3.1 Testes falhando do contrato/gateway de investments (summary, register stock/CDB, delete) com parsing de dinheiro em string. Implementar `InvestmentsGateway` (ou equivalente) em `VerbasGateways` + HTTP no `androidApp` até passarem
- [x] 3.2 Adicionar `Route.Carteira` (não TopLevel), entrada "Carteira" em `MoreScreen` e título na TopAppBar. Teste de navegação: Mais → Carteira; bottom bar permanece com 5 itens. Verificar: `NavigationTest` / UI test correspondente passa
- [x] 3.3 Testes falhando da tela Compose: empty state; totais; parcial; estimado; cadastro ação/CDB; exclusão com confirmação de risco e cancelamento. Implementar a tela no `shared` até passarem (`jvmTest`)
- [x] 3.4 Cenário de participante de verba alheia: carteira própria vazia permite cadastro; UI não oferece carteira do dono das verbas. Verificar: teste jvm cobre o comportamento

## 4. Verificação integrada

- [x] 4.1 Rodar `cd web && pnpm test` e `cd web && pnpm lint`. Verificar: suíte e lint passam
- [x] 4.2 Rodar `make mobile-check` (ou `./gradlew :shared:jvmTest` + build Android se o SDK exigir o alvo completo). Verificar: testes JVM e build Android de debug passam
- [x] 4.3 Conferir que nenhum log de cliente imprime valores detalhados de posições/P&L em caminhos felizes dos testes. Verificar: revisão dos asserts/spies de log (ou ausência de logging financeiro) ok
