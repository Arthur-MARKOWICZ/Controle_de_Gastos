# Tasks

## 1. Decisão e documentação

- [x] 1.1 Escrever `docs/decisions/0031-lancamentos-recorrentes-com-confirmacao.md` (modo pendente × automático, idempotência por recorrência + competência, sem backfill, tabelas separadas do `ledger_entry`, só o dono opera, primeiro evento de aplicação síncrono `EnvelopeArchived`) e verificar que segue as seções dos ADRs existentes e cita ADR-0006, 0022, 0023, 0026 e 0027
- [x] 1.2 Atualizar `docs/privacy/inventario-de-dados.md` com `recurring_entry` e `recurring_occurrence` (finalidade, retenção, exclusão em cascata com a verba) e verificar que o inventário cita as duas tabelas

## 2. Regras de data (TDD, domínio puro)

- [x] 2.1 Escrever testes falhando de `RecurrenceSchedule.dueDate` (dia 5, dia 31 em fevereiro comum e bissexto, dia 31 em abril, dia 30 em fevereiro) e verificar que falham antes da implementação
- [x] 2.2 Implementar `dueDate` mínimo e verificar que os testes de 2.1 passam
- [x] 2.3 Escrever testes falhando de `firstCompetence` (criação antes, no dia e depois do dia previsto; dia 31 criado em 30/04) e de `dueCompetence` (competência atual vencida, ainda não vencida, anterior à primeira competência, competência passada nunca é devida) e implementar até passarem
- [x] 2.4 Refatorar mantendo os testes verdes e verificar que `./gradlew unitTest` passa

## 3. Persistência e domínio de recorrência (TDD)

- [x] 3.1 Criar `backend/src/main/resources/db/migration/V17__create_recurring_entries.sql` conforme o design (CHECKs, `UNIQUE (recurring_entry_id, competence)`, FKs `ON DELETE CASCADE` para verba) e verificar com teste de integração que a migration aplica e que a restrição única rejeita duplicata
- [x] 3.2 Escrever testes falhando das entidades `RecurringEntry` (valor positivo, dia 1..31, descrição até 140, desativar/reativar recalcula `firstCompetence`, exclusão lógica) e `RecurringOccurrence` (transições PENDING→CONFIRMED e PENDING→DISCARDED; confirmar/descartar fora de PENDING lança erro) e implementar até passarem
- [x] 3.3 Implementar repositórios com o insert `ON CONFLICT DO NOTHING` e verificar com teste de integração (Testcontainers) que duas inserções da mesma competência geram uma linha

## 4. Gerador diário (TDD)

- [x] 4.1 Escrever testes falhando do gerador com `Clock` fixo: gera no dia previsto; não duplica em reexecução; não regenera descartada; recupera dias perdidos no mês; não gera competência anterior; ignora inativa, excluída e de verba arquivada; dia 31 em fevereiro gera em 28/02
- [x] 4.2 Implementar `RecurringOccurrenceGenerator` com `@Scheduled(cron = "0 10 0 * * *", zone = "America/Sao_Paulo")` e execução no `ApplicationReadyEvent`, e verificar que os testes de 4.1 passam
- [x] 4.3 Verificar com teste que captura logs que a geração registra só contagens e IDs (sem valores, descrições ou e-mails)

## 5. Serviço e API de recorrências (TDD)

- [x] 5.1 Escrever testes falhando do CRUD com autorização: dono cria `EXPENSE` e `CONTRIBUTION`; participante recebe 403; terceiro recebe 404; verba arquivada recusa criação; dia/valor inválidos dão 400; e implementar `RecurringEntryService` até passarem
- [x] 5.2 Expor `GET/POST /api/v1/envelopes/{id}/recurring-entries` e `PUT/DELETE .../{recurringId}` com valores como string decimal e verificar com testes de controller (MockMvc) os cenários de 5.1
- [x] 5.3 Verificar com teste que editar o valor não altera a pendente já gerada e que excluir a recorrência mantém o lançamento confirmado e descarta as pendentes
- [x] 5.4 Atualizar `docs/api/openapi.yaml` com os endpoints de recorrência e verificar que o arquivo valida (lint/teste existente do contrato)

## 6. Pendências: listar, confirmar, descartar (TDD)

- [x] 6.1 Escrever testes falhando de listagem: só pendências de verbas do usuário dono; separação `current`/`overdue` pela competência em America/Sao_Paulo; participante não vê pendências de verba compartilhada; e implementar até passarem
- [x] 6.2 Escrever testes falhando de confirmação: sem ajuste cria lançamento com valor sugerido e data prevista; com "237.45" cria lançamento de "237.45"; vencida confirmada mantém data da competência original; data futura dá 400 e mantém pendente; segunda confirmação dá 409; confirmação de descartada dá 409; participante 403; terceiro 404; e implementar `confirm` chamando `LedgerService.register` até passarem
- [x] 6.3 Verificar com teste que a pendente não altera disponível, resumo mensal nem relatórios e que a confirmação dispara o mesmo alerta de saldo e a mesma revisão de auditoria do lançamento manual
- [x] 6.4 Escrever testes falhando de descarte (não cria lançamento; impede confirmação posterior; autorização) e implementar até passarem
- [x] 6.5 Expor `GET /api/v1/recurring-occurrences/pending`, `POST .../{occurrenceId}/confirm` e `POST .../{occurrenceId}/discard`, mapear conflito de versão para 409, documentar em `docs/api/openapi.yaml` e verificar com testes de controller

## 7. Arquivamento e direitos do titular (TDD)

- [x] 7.1 Escrever teste falhando: arquivar a verba desativa recorrências e descarta pendentes na mesma transação; publicar `EnvelopeArchived` em `EnvelopeService.archive` e tratar com `@EventListener` em `ledger` até passar; verificar que `ModularityTest` continua verde (sem dependência `envelopes → ledger`)
- [x] 7.2 Incluir recorrências e ocorrências na exportação de `TitularRightsService` e verificar com teste que o arquivo exportado contém "1800.00" da recorrência e suas ocorrências
- [x] 7.3 Verificar com teste de integração que a exclusão do titular remove recorrências e ocorrências das verbas dele e que `cd backend && ./gradlew check` passa com o limite JaCoCo

## 8. Web

- [x] 8.1 Adicionar tipos e chamadas de recorrências e pendências em `web/src/lib/api.ts` e verificar com testes de `api.test.ts` (valores string, erros 403/404/409)
- [x] 8.2 Ação "Recorrências" no card da verba, abrindo diálogo (criar, editar, ativar/desativar, excluir; aviso para dias 29–31; oculta para participante) e verificar com testes de componente desses cenários
- [x] 8.3 Bloco "Pendências" em `web/src/app/dashboard.tsx` com grupos do mês e "Vencidas" (rótulo textual), confirmar, ajustar e descartar com confirmação, recarga de saldos após sucesso e erro acessível em falha; oculto sem pendências; verificar com testes de componente e `cd web && pnpm test:coverage`

## 9. Android (KMP)

- [ ] 9.1 Adicionar DTOs e chamadas em `mobile/shared` e verificar com testes JVM do `shared` (serialização de valores decimais como string, mapeamento de 403/404/409)
- [x] 9.2 Ação "Recorrências" no menu do card da verba, abrindo diálogo (oculta para participante), e verificar com testes de ações do card e de UI Compose no `shared`
- [x] 9.3 Pendências e "Vencidas" na tela inicial com confirmar em um toque (alvos ≥ 48dp), ajustar e descartar; falha mantém o item com mensagem; verificar com testes do `androidApp` e `make mobile-check`

## 10. Desktop (Tauri)

- [x] 10.1 Adicionar tipos e chamadas em `desktop/src/lib/api.ts`/`types.ts` usando o comando IPC `api` existente e verificar com testes vitest
- [x] 10.2 Seção de recorrências em `EnvelopesScreen` (oculta para participante) e bloco de pendências em `OverviewScreen` com rótulo "Vencidas", ações acessíveis por teclado e recarga de saldos; verificar com testes de `EnvelopesScreen.test.tsx`/`OverviewScreen.test.tsx` e `make desktop-check`

## 11. Verificação integrada

- [x] 11.1 Rodar `make check` e verificar que backend, web, mobile e desktop passam com os limites de cobertura do ADR-0030
- [ ] 11.2 Exercitar o fluxo ponta a ponta em ambiente local (criar recorrência com dia de hoje, reiniciar o backend para disparar a geração, confirmar com ajuste na web e ver o saldo mudar; descartar outra no desktop) e registrar o resultado no PR
