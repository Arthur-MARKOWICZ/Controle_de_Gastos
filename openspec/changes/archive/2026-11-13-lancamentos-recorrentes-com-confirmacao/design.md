# Design

## Context

Motivação e escopo: ver `proposal.md`. Requisitos: ver `specs/ledger/recurring-entries/spec.md` e as specs `ui-*`.

Estado atual relevante:

- `@EnableScheduling` já está ativo em `ControleGastosApplication`; o único job é `IdentityRetentionService.discardExpiredTechnicalData` (cron em UTC). Há uma instância do backend.
- `LedgerService.register(envelopeId, kind, amount, occurredAt, description)` concentra as regras de lançamento: visibilidade da verba, só o dono aporta (ADR-0026), data no máximo amanhã em `BUSINESS_ZONE` (America/Sao_Paulo), alerta de transição de saldo e meta atingida. Ele lê o autor de `AuthenticationService.currentUserId()`, então **depende de uma requisição autenticada**.
- Saldo, resumo e relatórios somam `ledger_entry` diretamente (`sumAmountUpTo`, `sumAmountBetween`, consultas de reporting e exportação do titular), filtrando só `deleted_at IS NULL`.
- `ledger` já depende de `envelopes` (`EnvelopeService.getVisible`); `envelopes` não conhece `ledger`. O backend ainda não usa eventos de aplicação.
- Exclusão do titular (ADR-0023) remove as verbas do proprietário; as FKs de verba usam `ON DELETE CASCADE`.

## Goals / Non-Goals

**Goals:**
- Gerar pendências idempotentes sem tocar nas consultas de saldo existentes.
- Reaproveitar `LedgerService.register` na confirmação, para que saldo, alertas, meta e auditoria se comportem igual ao lançamento manual.
- Manter as regras de data (competência, dia 31, recuperação no mês) testáveis em domínio puro, com `Clock`.

**Non-Goals:**
- Ferramentas MCP (canal fora deste change).
- Execução em várias instâncias (sem ShedLock); a idempotência do banco basta para reexecuções.
- Cálculo da data prevista em SQL.

## Decisions

### 1. Tabelas separadas, não status em `ledger_entry`

`recurring_entry` (a regra) e `recurring_occurrence` (a pendência por competência). Só a confirmação cria uma linha em `ledger_entry`.

- **Por quê:** nenhuma soma de saldo, relatório ou exportação precisa de filtro novo; uma pendência nunca "vaza" para o saldo por esquecimento.
- **Alternativa rejeitada:** coluna `status` em `ledger_entry`. Exigiria `status = 'CONFIRMED'` em todas as consultas de soma e de reporting, e afrouxaria `CHECK (amount > 0)` e a semântica de "lançamento = gasto real".

### 2. Esquema (migration `V17__create_recurring_entries.sql`)

```
recurring_entry
  id UUID PK
  envelope_id UUID NOT NULL REFERENCES envelope(id) ON DELETE CASCADE
  kind VARCHAR(12) NOT NULL CHECK (kind IN ('EXPENSE','CONTRIBUTION'))
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0)
  day_of_month INTEGER NOT NULL CHECK (day_of_month BETWEEN 1 AND 31)
  description VARCHAR(140)
  active BOOLEAN NOT NULL
  first_competence DATE NOT NULL          -- primeiro dia do mês da 1a competência
  created_at TIMESTAMPTZ NOT NULL
  deleted_at TIMESTAMPTZ
  version BIGINT NOT NULL

recurring_occurrence
  id UUID PK
  recurring_entry_id UUID NOT NULL REFERENCES recurring_entry(id) ON DELETE CASCADE
  envelope_id UUID NOT NULL REFERENCES envelope(id) ON DELETE CASCADE
  competence DATE NOT NULL                -- primeiro dia do mês
  due_date DATE NOT NULL
  suggested_amount NUMERIC(19,2) NOT NULL CHECK (suggested_amount > 0)
  status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING','CONFIRMED','DISCARDED'))
  ledger_entry_id UUID REFERENCES ledger_entry(id) ON DELETE CASCADE
  resolved_at TIMESTAMPTZ
  created_at TIMESTAMPTZ NOT NULL
  version BIGINT NOT NULL
  UNIQUE (recurring_entry_id, competence)
  INDEX (envelope_id, status, due_date)
```

- `day_of_month` é `INTEGER`, não `SMALLINT`: a validação de esquema do Hibernate exige o tipo do campo `int` da entidade.
- `ledger_entry_id` usa `ON DELETE CASCADE`: lançamentos só são apagados fisicamente junto com a verba (exclusão do titular), e a ocorrência acompanha; em uso normal o lançamento é excluído logicamente e a ocorrência fica.
- O proprietário não é duplicado: vem de `envelope.owner_id`. Como só o dono opera, o autor do lançamento confirmado é o dono (via `register`).
- `envelope_id` em `recurring_occurrence` é redundante, mas permite listar pendências por verba do dono sem join na recorrência e acompanha o `CASCADE` da exclusão do titular.
- `kind`/descrição da ocorrência são lidos da recorrência no momento da confirmação; o valor é o da ocorrência (`suggested_amount`, congelado na geração).
- Excluir recorrência = **exclusão lógica** (`deleted_at`) + descarte das pendentes. Mantém o histórico das confirmadas para a métrica da issue (ocorrências `CONFIRMED` ÷ lançamentos do mês).

### 3. Regras de data em domínio puro

Um objeto de valor `RecurrenceSchedule(dayOfMonth)` com:
- `dueDate(YearMonth)` → `month.atDay(min(day, month.lengthOfMonth()))`.
- `firstCompetence(LocalDate today)` → mês corrente se `dueDate(atual) >= today`, senão o próximo. Usado na criação e na reativação (reativar depois do dia previsto não gera a competência atual).
- `dueCompetence(YearMonth firstCompetence, LocalDate today)` → a competência corrente quando `YearMonth.from(today) >= firstCompetence && dueDate(corrente) <= today`; vazio caso contrário.

Só a competência corrente é gerada; um servidor fora do ar por dias recupera dentro do mês, e meses passados nunca são preenchidos. Tudo testado com datas fixas, sem Spring.

### 4. Job e idempotência

- `RecurringOccurrenceGenerator.generateDue()` com `@Scheduled(cron = "0 10 0 * * *", zone = "America/Sao_Paulo")`, logo após a virada do dia para que a pendência apareça de manhã. Também roda uma vez no `ApplicationReadyEvent` (`generateOnStartup`), cobrindo reinício após janela perdida; nessa execução uma falha é só registrada no log (classe da exceção, sem dados) e não impede a aplicação de subir — o job diário tenta de novo.
- Carrega recorrências ativas, não excluídas, de verbas não arquivadas e com `first_competence <= competência atual`; para as que têm `dueCompetence`, insere com `INSERT ... ON CONFLICT (recurring_entry_id, competence) DO NOTHING`. Uma ocorrência descartada continua ocupando a chave, então não é regenerada.
- Não roda em contexto autenticado e não chama `LedgerService`.
- Logs só com contagens (geradas/ignoradas) e IDs; nunca valor, descrição ou e-mail.
- **Alternativa rejeitada:** gerar sob demanda ao abrir a visão geral. Mistura escrita com leitura e faz a métrica depender de acesso; o job é simples e já há precedente.

### 5. Confirmação reaproveita `LedgerService.register`

`RecurringOccurrenceService.confirm(occurrenceId, amount?, occurredAt?)`, numa transação:
1. Carrega a ocorrência e a verba via `EnvelopeService.getVisible` (terceiro → 404); se o usuário não é o dono → 403.
2. Se `status != PENDING` → 409.
3. Chama `ledger.register(envelopeId, kind, amount ?: suggested, occurredAt ?: due_date, description)`.
4. Marca `CONFIRMED`, grava `ledger_entry_id` e `resolved_at`. `@Version` faz a segunda confirmação concorrente falhar com 409, tratada pelo `RecurringExceptionHandler` junto com a pendência já resolvida.

Descartar segue os passos 1–2 e marca `DISCARDED`, sem tocar no ledger.

- **Alternativa rejeitada:** criar o `LedgerEntry` direto no serviço de recorrência. Duplicaria alertas, meta e validação de data, e divergiria da auditoria (ADR-0027).

### 6. Autorização exclusiva do dono

Todos os endpoints resolvem a verba com `getVisible` (404 para terceiros) e exigem `envelope.ownerId == currentUser` (403 para participantes). A listagem de pendências filtra por verbas cujo `owner_id` é o usuário, então participantes nunca veem pendências de verbas compartilhadas.

### 7. Arquivamento da verba sem ciclo de dependência

`envelopes` não pode chamar `ledger` (ciclo). `EnvelopeService.archive` publica um `EnvelopeArchived(envelopeId)` (Spring `ApplicationEventPublisher`); um `@EventListener` síncrono em `ledger` desativa recorrências e descarta pendentes **na mesma transação**. O gerador também filtra verbas arquivadas, como defesa.

- **Alternativa rejeitada:** tratar arquivamento só de forma preguiçosa na leitura. Deixaria recorrências "ativas" numa verba arquivada e pendências órfãs.
- Primeiro evento de aplicação do backend: o ADR-0031 registra o padrão (evento síncrono intra-monólito, sem fila).

### 8. Contrato HTTP

```
GET    /api/v1/envelopes/{id}/recurring-entries
POST   /api/v1/envelopes/{id}/recurring-entries
PUT    /api/v1/envelopes/{id}/recurring-entries/{recurringId}   (inclui "active")
DELETE /api/v1/envelopes/{id}/recurring-entries/{recurringId}

GET    /api/v1/recurring-occurrences/pending
       -> { "current": [...], "overdue": [...] }
POST   /api/v1/recurring-occurrences/{occurrenceId}/confirm
       body opcional { "amount": "237.45", "occurredAt": "2026-12-05" }
POST   /api/v1/recurring-occurrences/{occurrenceId}/discard
```

Valores como string decimal com duas casas (ADR-0006). Cada item pendente traz `envelopeId`, `envelopeName`, `kind`, `description`, `suggestedAmount`, `dueDate`, `competence`. "Vencida" = `competence < mês corrente` em `BUSINESS_ZONE`, calculado no servidor.

### 9. Clientes

- **Web:** ação "Recorrências" no card da verba (só dono), que abre um diálogo no Dashboard, em Verbas e em Metas; bloco "Recorrências para confirmar" na visão geral (`Dashboard.tsx`), abaixo dos alertas persistentes; usa `web/src/lib/api.ts` e `money.ts`.
- **Android (KMP):** contrato, controlador e UI Compose em `mobile/shared` (`RecurringContract.kt`, `RecurringScreens.kt`), com a ação "Recorrências" no menu do card e confirmação em um toque na home; gateway HTTP em `androidApp` (`AndroidApiGateway.kt`). iOS preservado no `shared`, não validado.
- **Desktop:** chamadas via IPC `api` existente (sem comando Rust novo), seção em `EnvelopesScreen` e bloco em `OverviewScreen`. O comando `api` aceitava só GET/POST/PATCH; com aprovação do titular, a lista fechada de métodos passou a incluir PUT e DELETE (`Method::parse` no crate `session`, testado), necessários para editar, desativar e excluir recorrências. Métodos fora da lista continuam recusados.
- **MCP:** fora do escopo.

## Risks / Trade-offs

- [Pendências esquecidas acumulam vencidas] → agrupamento "Vencidas" visível na visão geral; descarte com um clique. Expiração automática fica para depois da métrica.
- [Confirmar vencida altera saldo de mês passado e pode disparar alerta retroativo] → é o comportamento do lançamento manual com data passada; o usuário pode ajustar a data na confirmação.
- [Job perde a janela por indisponibilidade] → execução no `ApplicationReadyEvent` e recuperação dentro do mês; na virada do mês sem execução, a competência anterior é perdida (aceito, sem backfill).
- [Duas instâncias no futuro] → `UNIQUE` + `ON CONFLICT` evita duplicata; ShedLock só se escalar (fora do escopo).
- [Primeiro evento de aplicação] → listener síncrono e transacional, documentado no ADR-0031; sem dependência nova.
- [Usuário espera que "aporte mensal" seja automático] → texto na UI explica que nada entra no saldo antes de confirmar.

## Migration Plan

1. Migration `V17` só cria tabelas (expand-only); nenhum dado existente muda.
2. Deploy do backend antes dos clientes; clientes antigos ignoram os endpoints novos.
3. Rollback: desligar as telas e, se necessário, remover endpoints; as tabelas podem ficar vazias sem afetar o ledger.
