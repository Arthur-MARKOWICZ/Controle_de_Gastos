# ADR-0033: Notificações por Web Push e lembrete diário local

## Status

Aceito

## Data

2026-11-24

## Contexto

A issue #85 (NEG-003) pede um gatilho para o hábito de lançar gastos todo dia.
Hoje o módulo `notifications` só grava alertas in-app de saldo negativo (V14), e
o `LedgerService` chama `AlertService` diretamente dentro da transação do gasto.
A ADR-0007 (A8) já rejeitou push a cada gasto, mas aceitou notificar saldo
negativo e verba compartilhada, desde que com consentimento. A fundação técnica
deixou "Projeto Firebase/APNs" como questão aberta.

O titular decidiu não depender do Firebase nesta etapa.

## Decisão

1. **Push só na web, por Web Push padrão (RFC 8030/8291/8292, VAPID).** Não há
   Firebase Cloud Messaging nem APNs. A API guarda as assinaturas dos navegadores
   (`web_push_subscription`) e as chaves VAPID ficam como segredo de runtime
   (`NOTIFICATIONS_VAPID_*`). Sem chaves, o envio fica desligado e o resto
   funciona.
2. **Envio implementado com a JCA do JDK.** Usa ECDH P-256, HKDF-SHA256,
   AES-128-GCM com `aes128gcm` e JWT ES256, além do `java.net.http`. Não usa
   `nl.martijndwars:web-push`: a versão 5.1.2 depende de `bcprov-jdk15on` 1.70 e
   `jose4j` 0.7.9, ambos com CVEs conhecidas, e de dois clientes HTTP
   assíncronos. O envio fica atrás de uma interface, o que permite trocar de
   implementação.
3. **Android e desktop recebem lembrete diário local, sem push remoto.** O
   horário vem da API. No horário, o cliente consulta
   `GET /api/v1/notifications/today` e notifica só se não houve gasto. No
   Android, isso usa WorkManager, com algum atraso possível. No desktop, usa
   `tauri-plugin-notification` e só funciona com o aplicativo aberto. No iOS
   (KMP), o agendamento é no-op (ADR-0004).
4. **Consentimento por tipo, desligado por padrão.** Os tipos são
   `DAILY_REMINDER`, `NEGATIVE_BALANCE` e `SHARED_EXPENSE`. As preferências e o
   horário (`HH:mm`, passos de 15 minutos, `America/Sao_Paulo`) ficam na API e
   valem para todos os canais.
5. **Payload mínimo.** O payload leva a categoria, o `EnvelopePurpose` da verba
   quando houver e um caminho do app. Nunca leva nome da verba, valor,
   descrição, e-mail ou identificador de outra pessoa. O texto exibido é montado
   no service worker.
6. **Eventos do ledger pelo Event Publication Registry do Spring Modulith.** O
   `ledger` publica eventos de gasto criado, editado e excluído (sem valores), e
   o `notifications` os consome após o commit (`@ApplicationModuleListener`). As
   publicações ficam na tabela `event_publication` do mesmo PostgreSQL, sem fila
   nem broker. Envios pendentes são republicados no reinício, e as publicações
   concluídas são apagadas. Com isso, a dependência `ledger → notifications` se
   inverte.
7. **Lembrete web pelo servidor.** Um agendamento a cada 15 minutos, no mesmo
   padrão de `RecurringOccurrenceGenerator`, envia o lembrete a quem tem horário
   vencido no dia. `last_reminder_date` impede o envio duplicado. A premissa é
   **uma réplica do backend**; com mais réplicas, essa guarda evita duplicatas,
   mas a varredura se repete.
8. **Retenção.** Uma assinatura dura até a remoção pelo titular, o logout no
   navegador, uma resposta 404/410 do push service ou a exclusão da conta
   (`ON DELETE CASCADE`). O endpoint e as chaves nunca entram em logs. A
   exportação do titular traz o endpoint e as datas, sem `p256dh` e `auth`.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| FCM no Android (Firebase Admin SDK) | Dependência do Google e projeto Firebase. O titular adiou essa decisão |
| `nl.martijndwars:web-push` | Dependências transitivas com CVEs e pouco mantidas |
| Push dentro da transação do gasto | Uma chamada HTTP externa dentro da transação; falha do push derrubaria o gasto |
| Fila, broker ou Redis | Exige infraestrutura e ADR próprios; a tabela de publicações basta no volume atual |
| Agendamento por pessoa (Quartz ou `TaskScheduler` dinâmico) | Mais estado para pouco ganho em relação a uma varredura a cada 15 minutos |
| `AlarmManager` exato no Android | Exige `SCHEDULE_EXACT_ALARM`; minutos de atraso não importam para um lembrete |
| Nome da verba no push | Expõe dado financeiro na tela bloqueada e no push service |

## Consequências

- O push service do navegador (Google, Mozilla, Apple) recebe o payload cifrado
  e metadados de envio. Ele passa a constar como operador no inventário.
- Com poucas verbas, o tipo (`Limite`, `Meta` etc.) pode indicar de qual verba
  se trata. O titular aceitou esse risco em troca de avisos úteis.
- O alerta in-app de saldo negativo passa a ser criado de forma assíncrona,
  logo após o commit do gasto.
- Quem ativa o lembrete no web e no Android pode receber dois avisos. O opt-in
  por canal fica para depois.
- No iOS, o Web Push exige a web instalada como app (iOS 16.4+), o que pede
  manifest e service worker. O service worker não guarda nada em cache.

## Quando revisar

- Push nativo no Android ou no iOS (o FCM/APNs reabre a questão da fundação).
- Mais de uma réplica do backend.
- Tabela `event_publication` crescendo sem ser apagada, ou atraso perceptível
  dos alertas.

Relacionados: ADR-0001, ADR-0003, ADR-0004, ADR-0007, ADR-0026, ADR-0029.
