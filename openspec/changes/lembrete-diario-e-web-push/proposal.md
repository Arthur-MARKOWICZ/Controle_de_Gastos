# Proposal

## Why

A premissa 1 (o lançamento diário vira hábito) não tem gatilho. Hoje o módulo
`notifications` só grava alertas in-app de saldo negativo (V14), e a pessoa só
os vê se abrir o app. A issue #85 (NEG-003) pede um lembrete diário opt-in e
avisos de eventos relevantes. Ela vem logo após recorrentes (NEG-002) no "ciclo
do hábito" de `reports/analise-negocio-2026-11-12.md`.

## What Changes

- **Preferências de notificação no backend:** opt-in por tipo (lembrete diário,
  verba negativa, gasto de participante em verba compartilhada) e horário do
  lembrete. Tudo começa desligado. O horário é a fonte de verdade para todos os
  canais.
- **Consulta "lançou hoje?":** o backend informa se a pessoa registrou ao menos
  um gasto na data de negócio atual (`America/Sao_Paulo`). Os clientes não
  calculam isso sozinhos.
- **Web Push padrão (VAPID), sem Firebase:** a API guarda assinaturas de push do
  navegador e envia três tipos de aviso: lembrete diário, verba negativa e gasto
  de participante. O payload traz só a categoria do aviso e o tipo da verba
  (`EnvelopePurpose`), nunca nome, valor ou autor.
- **Lembrete diário no servidor para o web:** um agendamento no módulo
  `notifications` dispara no horário escolhido quando não houve gasto no dia.
- **Alertas de evento assíncronos:** `ledger` deixa de chamar `AlertService`
  diretamente e passa a publicar eventos. O Event Publication Registry do
  Spring Modulith (`spring-modulith-starter-jpa`) garante a entrega sem fila nem
  broker. Surge o alerta in-app de gasto de participante, avisado ao dono da
  verba.
- **Web:** manifest e service worker próprios (pré-requisito do Web Push e da
  instalação como PWA no iOS 16.4+). Nas configurações, a pessoa ativa o push
  neste navegador e escolhe os tipos e o horário.
- **Android:** lembrete diário local (WorkManager). O horário vem da API; no
  horário, o app consulta "lançou hoje?" e notifica. Pede `POST_NOTIFICATIONS`
  no Android 13+ ao ativar.
- **Desktop:** lembrete diário local (`tauri-plugin-notification`) com o mesmo
  contrato. Só dispara com o aplicativo aberto.
- **Privacidade:** as assinaturas Web Push e as preferências entram no
  inventário de dados, na exportação do titular e na exclusão da conta.
  Assinaturas que o push service recusa (404/410) são apagadas.

### Fora do escopo

- Firebase Cloud Messaging, APNs e qualquer push remoto no Android, iOS nativo
  ou desktop. O "Projeto Firebase/APNs" da fundação técnica continua em aberto.
- Alertas de evento (verba negativa, gasto de participante) como notificação do
  sistema no Android e no desktop. Nesses canais eles seguem só in-app.
- Bandeja (tray) e início automático no desktop (NEG-004).
- Cache offline, sincronização offline ou qualquer cache de dados no service
  worker.
- Fuso horário por usuário. Tudo usa `America/Sao_Paulo`.
- Mais de uma réplica do backend (o agendamento parte de instância única).
- Opt-in por canal ou por dispositivo. As preferências valem para a conta toda.
- Push a cada gasto, já rejeitado pela ADR-0007 (A8).

### ADRs envolvidos

- **ADR-0007:** a alternativa A8 aceita notificar verba negativa e verba
  compartilhada com consentimento. Esta mudança cumpre essa condição. O texto
  "cada mutação publica `ApplicationEvent`" passa a ser verdade no código.
- **ADR-0001:** segue o monólito modular. O registro de eventos é tabela no
  mesmo PostgreSQL, sem broker.
- **ADR-0003:** privacidade desde a concepção. Opt-in, payload mínimo,
  retenção e exportação.
- **ADR-0026:** só o dono da verba recebe o aviso de gasto de participante.
- **ADR-0029:** o desktop passa a usar `tauri-plugin-notification`.
- **ADR-0004:** iOS preservado no KMP, sem lembrete validado.
- **Novo ADR-0033:** Web Push sem Firebase, lembrete local nos clientes
  nativos, consentimento, payload, retenção de assinaturas, Event Publication
  Registry e premissa de instância única.

## Capabilities

### New Capabilities
- `notifications/notification-preferences`: opt-in por tipo, horário do
  lembrete e consulta "lançou hoje?", válidos para todos os canais.
- `notifications/web-push-delivery`: registro e remoção de assinaturas Web Push,
  envio com payload mínimo, retenção e descarte de assinaturas inválidas.
- `notifications/event-alerts`: alertas de verba negativa e de gasto de
  participante gerados por eventos do ledger, in-app e por push.
- `ui-web/notification-settings`: ativação do push no navegador e edição das
  preferências, com manifest e service worker.
- `ui-mobile/lembrete-diario`: lembrete diário local no Android a partir das
  preferências da API.
- `ui-desktop/daily-reminder`: lembrete diário local no desktop a partir das
  preferências da API.

### Modified Capabilities
- Nenhuma. Não há spec de `notifications` hoje, e o alerta in-app de saldo
  negativo passa a ser descrito em `notifications/event-alerts`.

## Impact

- **Backend:** módulo `notifications` (preferências, assinaturas, agendamento,
  envio, listener de eventos), `ledger` (publica eventos em vez de chamar
  `AlertService`), `privacy` (exportação), migrations V19+, dependências
  `spring-modulith-starter-jpa` (o envio Web Push usa só a JCA do JDK), chaves VAPID como segredo de runtime.
- **API nova:** `/api/v1/notifications/preferences`,
  `/api/v1/notifications/web-push/subscriptions`,
  `/api/v1/notifications/web-push/public-key` e `/api/v1/notifications/today`.
  O contrato de `/api/v1/alerts` ganha o tipo `SHARED_EXPENSE`.
- **Web:** `manifest`, service worker em `public/`, página de configurações e
  cliente de API.
- **Android:** WorkManager, permissão `POST_NOTIFICATIONS`, tela "Mais".
- **Desktop:** `tauri-plugin-notification`, comando Rust para preferências e
  "lançou hoje?", tela de configurações.
- **Docs:** ADR-0033, inventário de dados (a linha "Token push" vira
  "Assinatura Web Push"), fundação técnica (questão Firebase/APNs) e deploy
  (variáveis VAPID).
- **MCP:** fora. Não expõe preferências de notificação.
