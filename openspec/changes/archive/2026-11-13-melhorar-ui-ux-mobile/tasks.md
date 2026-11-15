# Tasks

## 1. Funções puras do lançamento e do card

- [x] 1.1 Escrever teste falhando de `defaultOccurredAt` para mês corrente, mês passado e mês futuro (futuro = sem data válida) e verificar que `MoneyTest`/`IsoDateTest` (ou arquivo novo) falha até a função existir
- [x] 1.2 Implementar `defaultOccurredAt` e `canRegisterIn(viewed, today)` e verificar que os testes de 1.1 passam
- [x] 1.3 Escrever teste falhando da prévia de saldo (`"10.00"` − `"15.00"` = `"-5.00"`) e verificar que falha
- [x] 1.4 Implementar a subtração em `Money` (ou helper ao lado) só para apresentação e verificar que o teste de 1.3 passa e que aporte não usa essa prévia
- [x] 1.5 Escrever teste falhando da composição de ações (primária Registrar/Aportar, menu do dono vs participante) e verificar que falha
- [x] 1.6 Implementar a composição das ações do card e verificar que o teste de 1.5 passa

## 2. Card de verba

- [x] 2.1 Trocar a `Row` de cinco `TextButton` por ação primária 48 dp + menu "Mais" em `EnvelopeCard.kt` e verificar visualmente em 360 dp que nada corta e Encerrar não fica ao lado de Registrar
- [x] 2.2 Substituir emoji de estado negativo/meta por texto + ícone vetorial e verificar que o teste de composição e o card não usam `⚠`/`✓`
- [x] 2.3 Usar ênfase de erro no confirmar de Encerrar/Excluir em `ConfirmDialog` (parâmetro destrutivo) e verificar no diálogo que o botão confirmar não usa a cor primária

## 3. Folha de lançamento

- [x] 3.1 Introduzir a folha inferior única (verba + valor + data + descrição) no lugar de `ExpenseEnvelopePicker` + `EntryFormDialog` em cadeia e verificar que um toque em "Novo gasto" abre uma única superfície
- [x] 3.1a Apagar `ExpenseEnvelopePicker` e o estado `MonthDialog.ChooseExpenseEnvelope` que ficam sem uso e verificar com busca que nenhuma referência sobrou em `Screens.kt` e `Forms.kt`
- [x] 3.2 Acrescentar dica "ex.: 120,00" em `AmountField` (placeholder/`supportingText`) e a prévia de saldo na folha de gasto; cobrir em `jvmTest` as funções já puras — `AmountInput.display("50") == "R$ 0,50"` e a prévia de `"15.00"` sobre `"10.00"` — e conferir a dica na tela em 360 dp
- [x] 3.3 Ligar a data inicial ao mês da tela visível, ocultar o FAB em mês futuro e incluir `Route.History` em `QUICK_EXPENSE_ROUTES` e verificar: mês passado lança naquele mês; aba Gastos abre a folha; mês futuro não mostra FAB
- [x] 3.3a Mostrar o mês de referência no título da folha e verificar que, com o painel no mês corrente e a aba Gastos em `"2026-08"`, a folha aberta pela aba Gastos identifica `"2026-08"`
- [x] 3.4 Aplicar `imePadding` e scroll na folha e verificar que o botão confirmar permanece alcançável com o teclado aberto num emulador Android

## 4. Alertas e cromado

- [x] 4.1 Renderizar `PersistentNegativeAlerts` na Visão geral (mesmo dismiss) e verificar que um alerta ativo aparece na home e some das duas telas ao dispensar
- [x] 4.2 Remover o e-mail da `TopAppBar`, manter em Mais, e mudar o rótulo da aba Overview para "Início" (título da tela pode continuar "Visão geral") e verificar `NavigationTest` atualizado e os cinco rótulos (Início, Verbas, Metas, Gastos, Mais) sem ellipsis em 360 dp
- [x] 4.3 Formatar `occurredAt` com `formatIsoDateBr` em histórico e lançamentos da verba e verificar que a lista não mostra `YYYY-MM-DD` cru

## 5. Acesso (login e MFA)

- [x] 5.1 Usar cor de erro nas falhas de `AuthScreen` e tom distinto no sucesso de cadastro, com região ao vivo, e verificar que a mensagem de senha recusada não usa `primary`
- [x] 5.2 Adicionar mostrar/ocultar senha e não bloquear colar; usar `ContentType` se compilar no `commonMain` e verificar o toggle na tela de entrar
- [x] 5.3 Permitir cancelar MFA voltando a `AuthState.Anonymous` e aplicar `WindowInsets.safeDrawing` nas telas anônimas e verificar: voltar sai do MFA; campos de login não ficam sob a status bar no Android edge-to-edge

## 6. Verificação integrada

- [x] 6.1 Rodar `./gradlew :shared:jvmTest` em `mobile/` e verificar que a suíte passa, inclusive os testes novos desta change
- [x] 6.2 Registrar no final da change o que ficou como aceite manual em 360 dp (ações do card, dica de centavos, teclado na folha, área segura das telas anônimas), já que não há teste de Compose UI no projeto

## Aceite manual pendente (não automatizável neste projeto)

O projeto não tem teste de Compose UI nem emulador neste ambiente, e o
`proposal.md` já previa estes pontos como aceite manual. `./gradlew
:shared:jvmTest` (84 testes, 0 falhas) e `./gradlew :androidApp:assembleDebug`
passam, mas não cobrem o que depende de pixels, teclado ou barras do sistema.
Conferir num Android de 360 dp antes de dar a change por aceita:

- Ações do card (2.1): em 360 dp, nenhum rótulo cortado; a ação primária e o
  botão "Mais" com pelo menos 8 dp entre si; Encerrar só dentro do menu.
- Ênfase destrutiva (2.3): o confirmar de Encerrar e de Excluir aparece na cor
  de erro, não na primária.
- Dica de centavos (3.2): "Digite em centavos · ex.: 120,00" visível sob o
  campo de valor, sem truncar.
- Teclado na folha (3.4): com o teclado aberto, a folha rola e o botão
  Registrar continua alcançável (`imePadding` + `verticalScroll` aplicados).
- Rótulos da barra inferior (4.2): Início, Verbas, Metas, Gastos e Mais sem
  ellipsis.
- Área segura (5.3): em edge-to-edge, título e campos de entrar/cadastro/MFA
  ficam abaixo da status bar e o botão principal acima da área de gesto.
- Senha visível (5.2): o olho mostra e oculta a senha, e colar continua
  funcionando com o gerenciador de senhas.
