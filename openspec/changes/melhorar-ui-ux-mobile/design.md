# Design

## Context

Ver `proposal.md` para o motivo. O app autenticado é um `Scaffold` Material 3 com pilha própria (`Navigation.kt`), FAB `ExtendedFloatingActionButton` e formulários em `AlertDialog` (`Forms.kt`, `Components.kt`). O card (`EnvelopeCard.kt`) coloca até cinco `TextButton` numa `Row` sem wrap. Telas anônimas (`AuthScreen`, `MfaLoginScreen`) não passam pelo `Scaffold` e o Android já usa `enableEdgeToEdge`. A web já tem hint de centavos e prévia de saldo; a API de `ledger` não muda. Canais fora: API, web, MCP. Alvo verificável: Android (ADR-004).

## Goals / Non-Goals

**Goals:**

- Uma superfície de lançamento (folha inferior Material 3) no lugar da cadeia de diálogos.
- Lógica de data, prévia de saldo e composição das ações do card extraída em funções puras, testáveis em `:shared:jvmTest` (hoje não há teste de Compose UI).
- Reuso de `AmountInput`, `TouchTarget` e `describeFailure`; sem módulo, fila, cache ou cliente HTTP novo.
- `AmountInput.sanitize/display` e `Money` (value class sobre centavos) já são puros: a máscara (`display("50") == "R$ 0,50"`) e a prévia de saldo entram em `jvmTest` sem teste de Compose.

**Non-Goals:**

- Biblioteca de navegação (Navigation Compose / Voyager).
- Bottom sheet para criar/editar verba ou renda neste change — só o lançamento sai do `AlertDialog`.
- Testes instrumentados no emulador como porta de entrada; a verificação documentada permanece `jvmTest`.

## Decisions

### 1. `ModalBottomSheet` para lançar, não tela cheia nem dois diálogos

O ADR-007 já escolheu bottom sheet para o hábito diário. Material 3 1.12 no `commonMain` oferece `ModalBottomSheet` + `rememberModalBottomSheetState`. Uma folha com seletor de verba (quando o atalho global não traz uma) e os campos atuais de `EntryFormDialog`. Cancelar = dismiss da folha.

Alternativa rejeitada: tela `Route.NewExpense` — aumenta a pilha e o número de voltas, o oposto de lançamento rápido. Manter picker + form em dois `AlertDialog` também foi rejeitado pelas specs.

### 2. Menu "Mais" no card, não `FlowRow` de cinco ações

`FlowRow` caberia, mas manteria Encerrar ao lado de Registrar (toque acidental). Ação primária (`Button` ou `FilledTonalButton`, 48 dp) + `IconButton` "Mais" com `DropdownMenu`. Participante: só primária + Lançamentos no menu, sem editar/encerrar/participantes.

### 3. Prévia de saldo só no cliente

`Money` em centavos já permite `available.cents - amount.cents` para gasto. Não chamar a API até confirmar. Formatar com `toBrl()`. Aporte não calcula consumo.

### 4. Data inicial derivada do mês exibido

Função pura, por exemplo `defaultOccurredAt(viewed: YearMonth, today: String)`:

- mês corrente → `today`
- mês anterior → último dia daquele mês (ainda ≤ hoje)
- mês futuro → não abrir a folha; ocultar o FAB (não há `occurredAt` válido nesse mês)

A folha recebe o mês do `FinanceDashboardController` / `HistoryController` conforme a aba, e **mostra esse mês no título** (requisito, não só mitigação de risco — ver `specs/ui-mobile/lancamento-rapido`).

### 5. FAB também na aba Gastos; mês do histórico

`QUICK_EXPENSE_ROUTES` passa a incluir `Route.History`. A folha nessa aba usa o mês do `HistoryController` (pode divergir do painel — é o mês que a pessoa está vendo na lista). Após sucesso, recarregar histórico e painel.

### 6. Telas anônimas com insets, sem segundo `Scaffold` autenticado

Envolver login/MFA em `Scaffold` vazio ou `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)`. Evita barra superior duplicada. MFA ganha ação "Usar outra conta" / voltar que devolve `AuthState.Anonymous` sem chamar logout remoto se ainda não havia sessão plena.

### 7. Autofill no common se a API existir; senão só o que o campo já permite

Não bloquear colar (já não bloqueia). Toggle de visibilidade com `PasswordVisualTransformation` / `VisualTransformation.None`. `ContentType` de e-mail/senha só se Compose Multiplatform 1.12 expor no `commonMain`; não adicionar dependência Android-only só por isso.

### 8. Cromado

- Subtítulo da `TopAppBar` deixa de ser o e-mail; Mais continua mostrando.
- Rótulo `Route.Overview.label`: "Início" (cabe em 360 dp; o título da tela pode permanecer "Visão geral").
- `formatIsoDateBr` na lista de lançamentos e no histórico.
- `ConfirmDialog` destrutivo: `ButtonDefaults.buttonColors` com `error`.
- Estado negativo/meta: ícones `Icons.Outlined` + texto; remover `⚠` / `✓` como único sinal.

## Risks / Trade-offs

- [Folha longa + teclado] → `verticalScroll` + `imePadding` na folha; campos na ordem valor → data → descrição.
- [Dois meses (painel vs histórico)] → a folha usa o mês da tela visível; risco de a pessoa lançar no mês do histórico sem perceber. Mitigação: a folha mostra o mês no título, com cenário próprio na spec.
- [MFA cancelar] → não invalidar challenge no servidor neste change (sem endpoint novo); o challenge expira sozinho.
- [jvmTest não pega overflow real] → testes cobrem composição das ações, `defaultOccurredAt`, a máscara e a prévia de saldo; overflow, dica de centavos, teclado na folha e área segura ficam como critério de aceite manual em 360 dp, listados ao fechar a change.
- [iOS não validado] → código em `commonMain`; não declarar suporte iOS (ADR-004).

## Migration Plan

Entrega no mesmo APK Android. Sem migração de dados nem flag. Rollback = reverter o change no cliente. Nenhuma etapa de backend.

## Open Questions

Nenhuma que altere specs ou o recorte de tarefas. A disponibilidade exata de `ContentType` no CMP 1.12 se resolve na implementação: usar se compilar no `commonMain`, senão omitir sem mudar o restante.
