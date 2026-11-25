# Design

## Context

- `notifications` tem só `InAppAlert` (V14, tipo único `NEGATIVE_BALANCE`) e
  `AlertService`. O `LedgerService` chama `alerts.onBalanceTransition(...)`
  **diretamente** dentro da transação do gasto. Não há evento, apesar de a
  ADR-0007 dizer que "cada mutação publica `ApplicationEvent`".
- O backend usa `spring-modulith-starter-core`. O Event Publication Registry
  **não** está ativo: falta `spring-modulith-starter-jpa` e a tabela
  `event_publication`.
- `@EnableScheduling` já existe, com crons em `America/Sao_Paulo`
  (`RecurringOccurrenceGenerator`). O backend roda com uma réplica e sem
  ShedLock.
- A web (Next.js 16, `output: standalone`) não tem manifest nem service worker,
  embora a issue #90 (PWA) esteja fechada.
- O desktop expõe um comando Rust genérico `api` que faz requisições
  autenticadas sem passar o token para o webview (ADR-0029).
- O Android não tem WorkManager. O `MoreScreen` já agrupa preferências
  ("Aparência").
- A linha "Token push" já existe no inventário como reserva.

## Goals / Non-Goals

**Goals:**
- Um único modelo de preferências na API para os três canais.
- Um aviso perdido por falha de envio não pode desfazer o gasto, e o envio é
  retomado após reinício.
- O payload de push não carrega nada além de categoria, tipo da verba e caminho.

**Non-Goals:**
- Garantia de entrega no minuto exato.
- Escalar o agendamento para várias réplicas.
- Biblioteca de PWA (`@serwist/next` e afins). O service worker é escrito à
  mão e não faz cache.

## Decisions

### D1. Eventos do ledger com Event Publication Registry
O `ledger` publica dois eventos, sem valores:
`EnvelopeBalanceTransitioned` (`ownerId`, `envelopeId`, `envelopeName`, `envelopePurpose`,
`month`, `wasNegative`, `isNegative`), só quando o sinal do saldo muda ao criar, editar
ou excluir gasto; e `ExpenseRecorded` (`entryId`, `envelopeId`, `envelopeName`,
`envelopePurpose`, `month`, `ownerId`, `authorId`), a cada gasto registrado. O
`notifications` consome com `@ApplicationModuleListener` (assíncrono, após o commit,
transação própria; `@EnableAsync` na aplicação). O `spring-modulith-starter-jpa` grava
a publicação na mesma transação do gasto e a marca como concluída quando o listener termina. Na inicialização,
`spring.modulith.events.republish-outstanding-events-on-restart=true` reenvia as
pendentes.

- O cálculo de `wasNegative`/`isNegative` continua no `ledger`, que já faz isso.
  O `notifications` não lê saldo.
- `envelopePurpose` vai no evento para o payload de push não precisar consultar
  `envelopes`.
- Os eventos ficam em pacote público do `ledger` (ex.: `ledger.events`,
  `@NamedInterface`). Assim o `notifications` deixa de ser dependência do
  `ledger`, e a dependência se inverte.
- **Alternativa rejeitada:** manter a chamada direta e fazer o push dentro da
  transação do gasto. Uma requisição HTTP externa ficaria dentro da transação, e
  uma falha do push service derrubaria o gasto.
- **Alternativa rejeitada:** fila, broker ou Redis. Exigiriam ADR e
  infraestrutura, e o registro em tabela resolve no volume atual.

**Consequência:** o alerta in-app deixa de ser síncrono. Os testes que esperam o
alerta logo após o `POST` passam a usar `Scenario` do Modulith (ou aguardar a
publicação concluída).

### D2. Modelo de dados (V19)
- `notification_preference`: `user_id` (PK, FK `user_account` `ON DELETE
  CASCADE`), `daily_reminder`, `negative_balance`, `shared_expense` (boolean,
  default false), `reminder_time` (`TIME`, default `20:00`, `CHECK` minuto em 0,
  15, 30 ou 45), `last_reminder_date` (`DATE`, nulo) e `updated_at`. A linha é
  criada na primeira gravação; sem linha, valem os padrões. Como a exclusão de conta é lógica
  (`closeAsDeleted`), o `TitularRightsService` apaga explicitamente as linhas de
  notificação; o `ON DELETE CASCADE` cobre a remoção física posterior.
- `web_push_subscription`: `id`, `user_id` (FK `ON DELETE CASCADE`), `endpoint`
  (`TEXT UNIQUE`), `p256dh`, `auth`, `created_at`, `last_success_at`. O
  `UNIQUE(endpoint)` faz o "mesmo endpoint de outra conta" passar a pertencer à
  nova conta (upsert).
- (V19, junto com `event_publication`) `in_app_alert.alert_type` ganha `SHARED_EXPENSE` no `CHECK`. O índice único de
  ativo continua só para `NEGATIVE_BALANCE`; `SHARED_EXPENSE` gera um alerta por
  evento.
- `event_publication`: o schema padrão do Spring Modulith para PostgreSQL vai em
  migration Flyway, com `spring.modulith.events.jdbc.schema-initialization` e
  JPA desligados para DDL.

`p256dh` e `auth` não são cifradas. Elas só permitem cifrar mensagens para
aquele navegador, não leem dados dele. Ainda assim, ficam fora de logs e da
exportação.

### D3. Lembrete diário do web no servidor
Um `@Scheduled(cron = "0 */15 * * * *", zone = "America/Sao_Paulo")` seleciona
quem tem `daily_reminder = true`, `reminder_time <= agora`,
`last_reminder_date` diferente de hoje e ao menos uma assinatura. Para cada
pessoa:

1. Grava `last_reminder_date = hoje` (`UPDATE ... WHERE last_reminder_date IS
   DISTINCT FROM hoje`, que é a guarda de idempotência).
2. Consulta "lançou hoje".
3. Envia o push se não houve gasto.

Por usar `<=`, quem ativou às 20:05 com horário 20:00 recebe na rodada de 20:15
do mesmo dia. Após uma queda, a rodada seguinte recupera o lembrete do dia.

- **Alternativa rejeitada:** um job por pessoa (Quartz/TaskScheduler dinâmico).
  É mais estado para pouco ganho.
- **Premissa:** uma réplica só. Com várias, a guarda no `UPDATE` ainda evita
  duplicata, mas o custo da varredura se repete. Isso fica registrado na ADR.

### D4. "Lançou hoje"
`GET /api/v1/notifications/today` → `{ "date": "2026-11-24", "hasExpense":
false }`. A consulta é `EXISTS` em `ledger_entry` com `author_id = :user`,
`kind = EXPENSE`, `occurred_at = hoje` e `deleted_at IS NULL`. Ela fica exposta
pelo `ledger` em uma interface nomeada (ex.: `LedgerActivity.hasExpenseOn`). O
`notifications` não acessa tabelas do `ledger`.

Conta a **autoria**, não a posse da verba. O hábito que se mede é o da pessoa.

### D5. Envio Web Push
- **Implementação:** a avaliação da tarefa 1.4 descartou `nl.martijndwars:web-push`
  5.1.2, que traz `bcprov-jdk15on` 1.70 e `jose4j` 0.7.9 com CVEs, mais dois
  clientes HTTP assíncronos. O `WebPushSender` usa só a JCA do JDK (ECDH P-256,
  HKDF, AES-128-GCM `aes128gcm`, ES256) e `java.net.http`, com teste de ida e volta
  da cifragem. Ver ADR-0033.
- **Chaves:** `NOTIFICATIONS_VAPID_PUBLIC_KEY`, `NOTIFICATIONS_VAPID_PRIVATE_KEY`
  e `NOTIFICATIONS_VAPID_SUBJECT` (`mailto:` do controlador) como segredos de
  runtime. Sem chaves, o envio fica desabilitado (log de aviso no início),
  e o resto funciona.
- **Payload JSON:** `{ "category": "NEGATIVE_BALANCE", "purpose": "LIMIT",
  "path": "/" }`, com `TTL` de 24 h e `Urgency: normal`. O texto exibido é montado
  no service worker, então não sai do servidor.
- **Respostas:** 404/410 apagam a assinatura; 2xx atualiza `last_success_at`;
  falha de rede ou 5xx é registrada e lança exceção, e o Registry retenta no
  reinício (alertas de evento). O lembrete diário não retenta além do dia.
- **Logs:** só `subscriptionId`, `userId`, categoria e status HTTP. Nunca
  endpoint nem chaves.

### D6. Web: manifest e service worker
- `src/app/manifest.ts` (rota de metadata do Next) com ícones já existentes em
  `app/`.
- `public/sw.js` escrito à mão: `push` → `showNotification` com texto do
  dicionário local (`EnvelopePurpose` → "Limite", "Meta", "Fixa", "Meta de
  poupança", "Gasto anual"); `notificationclick` → foca ou abre `path`. Sem
  handler `fetch`, o que garante que não há cache.
- O service worker é registrado em "Ativar neste navegador". O navegador mantém o
  registro e a assinatura entre visitas, então não há registro extra no layout.
- O lembrete diário aponta para `/` (visão geral): a web lança gasto a partir de uma
  verba, e não há formulário de lançamento sem verba.
- O endpoint da assinatura atual é lido via `pushManager.getSubscription()` no
  logout e enviado para remoção antes do `POST /auth/logout`.

### D7. Android: WorkManager
Um `OneTimeWorkRequest` é agendado para o próximo horário e reagendado ao
terminar, ao abrir o app e ao salvar preferências (`ExistingWorkPolicy.REPLACE`,
nome único). O worker chama `/notifications/today` com a sessão do Keystore e
notifica pelo canal "Lembrete diário".

- O agendamento fica no `androidApp`. A UI e a regra (`ReminderSettingsController`)
  ficam no `shared`; o agendador é uma interface `ReminderScheduler` injetada no
  `VerbasApp`, no mesmo padrão das portas `VerbasGateways` (em vez de `expect/actual`).
  No iOS vale `NoReminderScheduler` (ADR-0004).
- O worker e a tela usam o mesmo `AndroidApiClient` do processo (`SharedApiClient`):
  o refresh é rotativo e duas renovações paralelas acionariam a detecção de reuso.
- **Alternativa rejeitada:** `AlarmManager` exato. Exigiria
  `SCHEDULE_EXACT_ALARM`, e alguns minutos de atraso não atrapalham o lembrete.

### D8. Desktop
O timer fica no webview (React): calcula o próximo horário a partir das
preferências e, no disparo, chama o comando `api` existente para
`/notifications/today`. A notificação sai pelo `tauri-plugin-notification`, mas
pelo lado Rust, por dois comandos da aplicação (`reminder_permission` e
`reminder_notify`, texto fixo). O webview não recebe permissão do plugin nem
precisa de arquivo de capabilities (ADR-0029). Ele não recupera horário perdido:
se o app abrir depois do horário, apenas agenda o do dia seguinte. A configuração
fica num diálogo aberto pelo rodapé, porque o spec `ui-desktop/app-shell` fixa a
navegação em Visão geral e Verbas.

- **Alternativa rejeitada:** timer no processo Rust. Seria mais robusto com o
  webview suspenso, mas duplicaria o estado de preferências fora da UI. Como o
  desktop já exige o app aberto, o ganho é pequeno.

### D9. API
| Método | Caminho | Corpo/Resposta |
|---|---|---|
| GET/PUT | `/api/v1/notifications/preferences` | `{ dailyReminder, negativeBalance, sharedExpense, reminderTime: "20:00" }` |
| GET | `/api/v1/notifications/today` | `{ date, hasExpense }` |
| GET | `/api/v1/notifications/web-push/public-key` | `{ publicKey }` |
| PUT | `/api/v1/notifications/web-push/subscriptions` | `{ endpoint, keys: { p256dh, auth } }` → 204 |
| POST | `/api/v1/notifications/web-push/subscriptions/remove` | `{ endpoint }` → 204 |

A remoção usa `POST` com corpo para não pôr o endpoint na URL nem em logs de
acesso. O `/api/v1/alerts` passa a devolver `type` `SHARED_EXPENSE`, e os
clientes que só conhecem `NEGATIVE_BALANCE` precisam tolerar o tipo novo.

### D10. ADR-0033 e documentos
- A ADR-0033 registra: Web Push sem FCM; lembrete local nos nativos; opt-in;
  payload mínimo com `EnvelopePurpose` e o risco de inferência; retenção de
  assinaturas (até remoção, logout, 404/410 ou exclusão da conta); Event
  Publication Registry; e a premissa de réplica única.
- O inventário troca "Token push" por "Assinatura Web Push" e acrescenta
  "Preferências de notificação" e o push service do navegador (Google, Mozilla,
  Apple) como operador que recebe o payload cifrado.
- A fundação técnica atualiza o item "Projeto Firebase/APNs": continua aberto
  para push nativo, e o push web está resolvido.
- O deploy documenta as variáveis VAPID e o comando para gerá-las.

## Risks / Trade-offs

- **[Inferência pelo tipo da verba]** Com poucas verbas, "tipo Limite" pode
  apontar qual é. → Decisão do titular, registrada na ADR. O nome e o valor
  nunca saem.
- **[Alerta in-app passa a ser assíncrono]** A UI pode não ver o alerta logo
  após o `POST`. → Na prática o atraso é de milissegundos. As telas já buscam
  alertas ao carregar, e os testes usam `Scenario`.
- **[`event_publication` cresce]** → Configurar a remoção das publicações
  concluídas (`completion-mode: delete`).
- **[Biblioteca `webpush-java` pouco mantida]** → O envio fica atrás de uma
  interface (D5), com plano B em JCA.
- **[Push service de terceiros]** Google, Mozilla e Apple veem metadados (IP do
  servidor, horário, tamanho). → O payload é cifrado ponta a ponta pelo padrão.
  Isso fica registrado como operador no inventário.
- **[Android atrasa ou mata o WorkManager em modo econômico]** → É aceito. O
  spec já diz que o disparo pode atrasar.
- **[Desktop fechado não lembra]** → É aceito. A tela avisa (spec).
- **[Duas notificações no web e no Android]** → É aceito nesta versão.
  O opt-in por canal fica fora do escopo.

## Migration Plan

1. Deploy do backend com V19, `starter-jpa` e chaves VAPID. Sem chaves, o push
   fica desligado e o resto funciona.
2. Deploy da web (manifest e service worker), depois Android e desktop.
   A ordem entre clientes é livre, porque todas as preferências começam
   desligadas.
3. **Rollback:** reverter o backend deixa as tabelas novas sem uso; o
   `CHECK` com `SHARED_EXPENSE` é compatível. A web antiga ignora o service
   worker, que pode ser removido com um `sw.js` que se desregistra.

## Open Questions

- O ícone monocromático do aviso no Android (`smallIcon`) pode reusar o ícone
  atual ou precisar de um novo asset. Isso não muda specs nem tarefas.
