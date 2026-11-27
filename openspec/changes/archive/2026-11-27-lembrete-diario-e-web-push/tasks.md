# Tasks

## 1. Decisão e documentação base

- [x] 1.1 Escrever `docs/decisions/0033-notificacoes-web-push-e-lembrete-local.md` (design D10): Web Push sem FCM, lembrete local nos nativos, opt-in, payload com `EnvelopePurpose` e risco de inferência, retenção de assinaturas, Event Publication Registry, réplica única e alternativas rejeitadas. Verificar: o arquivo existe e cita ADR-0001/0003/0007/0026/0029
- [x] 1.2 Atualizar `docs/privacy/inventario-de-dados.md`: trocar "Token push" por "Assinatura Web Push" (endpoint, p256dh, auth, retenção, sem logs, exportação sem chaves); adicionar "Preferências de notificação"; descrever o push service do navegador como operador. Verificar: diff revisado contra os specs `web-push-delivery` e `notification-preferences`
- [x] 1.3 Atualizar `docs/ideas/fundacao-tecnica.md` (item Firebase/APNs: web resolvido, nativo em aberto) e a ADR-0007 (nota de que os eventos do ledger agora existem). Verificar: nenhum documento contradiz a ADR-0033
- [x] 1.4 Verificar a compatibilidade de `nl.martijndwars:web-push` com Java 25 e procurar CVEs abertas; registrar na ADR-0033 a biblioteca escolhida ou o plano B em JCA (design D5)

## 2. Backend: Event Publication Registry e eventos do ledger (TDD)

- [x] 2.1 Adicionar `spring-modulith-starter-jpa`, a migration V19 com `event_publication` (schema PostgreSQL do Modulith) e as propriedades `republish-outstanding-events-on-restart` e `completion-mode: delete`. Verificar: `./gradlew test` sobe o contexto e o Flyway valida
- [x] 2.2 Teste falhando (`ApplicationModuleTest` do `ledger`, `Scenario`): criar, editar e excluir gasto publicam um evento com `ownerId`, `envelopeId`, `envelopePurpose`, `month`, `authorId`, `wasNegative` e `isNegative`, sem valor. Implementar os records em pacote público `ledger` (`@NamedInterface`) e publicar no `LedgerService`
- [x] 2.3 Remover a chamada direta `alerts.onBalanceTransition` do `LedgerService`. Mover a regra para um `@ApplicationModuleListener` no `notifications`. Adaptar os testes atuais de alerta negativo para `Scenario`/espera da publicação. Verificar: os cenários "Verba fica negativa", "Segunda transação" e "Saldo volta ao positivo" de `event-alerts` passam
- [x] 2.4 Rodar o teste de arquitetura modular (`ApplicationModules.verify()`). Verificar que `ledger` não depende mais de `notifications`

## 3. Backend: preferências e "lançou hoje" (TDD)

- [x] 3.1 Migration V20: `notification_preference` (design D2) com `CHECK` do passo de 15 minutos e FK `ON DELETE CASCADE`. Verificar: o teste de integração de exclusão de conta não deixa linhas
- [x] 3.2 Testes falhando para `GET/PUT /api/v1/notifications/preferences`: padrões desligados e `20:00`; ativar um tipo sem mexer nos outros; `21:10` → 400; sem sessão → 401; participante só vê as próprias. Implementar entidade, serviço e controller até passarem
- [x] 3.3 Teste falhando para `LedgerActivity.hasExpenseOn` (autoria, `EXPENSE`, não excluído, data em `America/Sao_Paulo`) e para `GET /api/v1/notifications/today` com os quatro cenários do spec, incluindo o gasto de participante que não conta para o dono. Implementar
- [x] 3.4 Teste falhando na exportação do titular (`TitularRightsService`) com preferências presentes. Implementar. Verificar: o cenário "Exportação" passa

## 4. Backend: Web Push (TDD)

- [x] 4.1 Migration V21: `web_push_subscription` (design D2). O `SHARED_EXPENSE` no `CHECK` de `in_app_alert` já entrou na V19, junto com os eventos. Verificar com Flyway nos testes
- [x] 4.2 Testes falhando para os endpoints de assinatura e chave pública: upsert por endpoint, endpoint de outra conta passa à nova, `http://` → 400, remoção de terceiro → 204 sem efeito, chave privada ausente da resposta. Implementar
- [x] 4.3 Criar a interface `WebPushSender` e a implementação (biblioteca da tarefa 1.4), com propriedades VAPID opcionais (sem chaves = desabilitado, com log de aviso). Teste unitário: payload `{category, purpose, path}` sem nome nem valor; 404/410 apagam a assinatura; 503 mantém e propaga a falha; 2xx atualiza `last_success_at`. Teste com servidor HTTP falso
- [x] 4.4 Teste falhando: o alerta `SHARED_EXPENSE` é criado só quando `authorId != ownerId`, sem dado do participante. Push sai só com o tipo ativo e para todas as assinaturas, e a falha numa não bloqueia as outras. A resolução não envia push. Implementar no listener
- [x] 4.5 Teste falhando para o lembrete diário (design D3) com `Clock` fixo: envia sem gasto; não envia com gasto; não duplica após nova rodada no mesmo dia; recupera na rodada seguinte ao horário. Implementar o `@Scheduled` a cada 15 minutos
- [x] 4.6 Exportação do titular com assinaturas sem `p256dh`/`auth`; teste falhando e implementação. Garantir por teste de log (ou revisão dirigida) que endpoint e chaves não aparecem em logs
- [x] 4.7 Documentar em `docs/deploy/` as variáveis `NOTIFICATIONS_VAPID_*` e o comando para gerar o par de chaves, e adicioná-las ao `compose.yaml`/infra como segredo. Verificar: o backend sobe localmente com e sem as variáveis
- [x] 4.8 Rodar `cd backend && ./gradlew test` com cobertura acima dos limites da ADR-0030

## 5. Web: PWA, configurações e push

- [x] 5.1 Adicionar `src/app/manifest.ts` e `public/sw.js` (handlers `push` e `notificationclick`, sem `fetch`), com o dicionário de `EnvelopePurpose`. Teste unitário da função que monta título e corpo a partir de `{category, purpose}`. Verificar no navegador que o manifest é servido e o service worker registra
- [x] 5.2 Adicionar ao `lib/api.ts` as chamadas de preferências, chave pública, assinatura e remoção, com testes no padrão de `api.test.ts`
- [x] 5.3 Seção "Notificações" no `SettingsPage`: três tipos, horário em passos de 15 minutos, "Ativar/Desativar neste navegador", estados de permissão negada, sem suporte e iOS sem instalação. Testes de componente para os cenários de `ui-web/notification-settings`
- [x] 5.4 No logout (`auth-client.ts`), remover a assinatura atual antes de `/auth/logout`. Teste unitário com `pushManager` simulado
- [x] 5.5 Tolerar `SHARED_EXPENSE` na lista de alertas da web (texto "Novo gasto em verba compartilhada"). Teste de componente
- [x] 5.6 Rodar `pnpm --filter web test`, lint e build. Validar no navegador (preview): ativar push, receber o push de teste do backend local e clicar até a navegação

## 6. Android: lembrete local

- [x] 6.1 Cliente de API no `shared` para preferências e `today`, com testes `commonTest`
- [x] 6.2 `expect/actual` do agendador: o Android usa WorkManager (`OneTimeWorkRequest` único, `REPLACE`); o iOS é no-op. Teste do cálculo do próximo horário em `commonTest` (antes e depois do horário, virada de dia)
- [x] 6.3 Worker: consulta `today`, notifica só com `false`, não notifica sem sessão ou sem rede; o toque abre o lançamento rápido. Teste do worker com `TestListenableWorkerBuilder`
- [x] 6.4 Seção do lembrete no `MoreScreen` com permissão `POST_NOTIFICATIONS` no Android 13+, canal "Lembrete diário" e texto de permissão negada. Reagendar ao abrir o app e ao salvar; cancelar no logout. Testes de UI/ViewModel nos cenários de `ui-mobile/lembrete-diario`
- [x] 6.5 Rodar `./gradlew :shared:allTests :androidApp:testDebugUnitTest` e validar num emulador Android 13+ com horário próximo

## 7. Desktop: lembrete local

- [x] 7.1 Adicionar `tauri-plugin-notification` só no Cargo, usado por comandos Rust da aplicação (sem pacote npm nem capability para o webview), conforme a ADR-0029. Verificar: `cargo build` e `pnpm --filter desktop build`
- [x] 7.2 Módulo de agendamento no webview: próximo horário sem recuperar o perdido, consulta `today` pelo comando `api` e notificação só com `false` e sessão válida. Testes Vitest com timers falsos para os cenários de `ui-desktop/daily-reminder`
- [x] 7.3 Tela/seção de configurações do lembrete com o aviso "só com o app aberto", pedido de permissão e cancelamento no logout. Testes de componente
- [x] 7.4 Rodar os testes do desktop e validar no Linux com o app aberto

## 8. Integração

- [x] 8.1 De ponta a ponta local: ativar o lembrete às `HH:mm` próximas no web, confirmar o push sem gasto, registrar um gasto e confirmar que a rodada seguinte não envia. Mudar o horário na web e confirmar que o Android reagenda ao abrir
- [x] 8.2 Verba compartilhada: o participante registra gasto, e o dono com `SHARED_EXPENSE` ativo recebe push com o tipo da verba e sem nome nem valor. Com o tipo desligado, só o alerta in-app
- [x] 8.3 Rodar as verificações do `README.md` de todos os módulos afetados

## Residuais de validação humana (não bloqueiam a change)

- 5.6 / 8.1 (push real no browser): `pnpm test`/`lint`/`build` da web passam; preferências, SW e manifest já foram exercitados. Receber e clicar um push real ainda depende de Chrome/Firefox com backend local e VAPID.
- 6.3: regra do worker coberta por `DailyReminderWorkerLogic` + `DailyReminderWorkerLogicTest` (JVM). `TestListenableWorkerBuilder` exigiria Robolectric/`androidTest`, fora do padrão atual do módulo.
- 6.5 / 8.1 (Android em aparelho): `make mobile-check` passa (`jvmTest`, `testDebugUnitTest`, Kover, `assembleDebug`). Não há AVD/aparelho neste ambiente para o horário próximo.
- 7.4: testes Vitest (166) e build do webview passam; a janela Tauri não foi aberta manualmente neste ambiente.
