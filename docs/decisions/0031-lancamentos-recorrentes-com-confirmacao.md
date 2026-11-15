# ADR-0031: Lançamentos recorrentes com confirmação

## Status

Aceito

## Data

2026-11-13

## Contexto

Grande parte dos gastos do mês é previsível (aluguel, internet, assinaturas,
aporte mensal), mas todo lançamento é manual. A verba `FIXED` reinicia o
valor-base a cada mês (ADR-0022), mas o gasto em si ainda precisa ser
digitado, o que desestimula o hábito diário (issue #84 / NEG-002).

Lançar automaticamente um gasto que não aconteceu (assinatura cancelada)
distorceria o saldo e violaria o princípio de registrar só gastos reais
(ADR-0007). O saldo, o resumo, os relatórios e a exportação do titular somam
`ledger_entry` diretamente, filtrando apenas `deleted_at`.

## Decisão

1. **Modo pendente na v1.** Uma recorrência mensal gera, por competência, uma
   **ocorrência pendente**. Só a confirmação do usuário cria o lançamento.
   O valor da recorrência é uma sugestão: valor e data são ajustáveis na
   confirmação. O modo automático fica para depois da métrica.
2. **Tabelas separadas do ledger.** `recurring_entry` (a regra) e
   `recurring_occurrence` (a pendência). Pendências nunca entram em
   `ledger_entry`; nenhuma consulta de saldo ou relatório muda.
3. **Idempotência por competência.** `UNIQUE (recurring_entry_id, competence)`
   e inserção com `ON CONFLICT DO NOTHING`. Ocorrência descartada continua
   ocupando a chave e não é regenerada. Um job diário (`@Scheduled`, 00:10 em
   America/Sao_Paulo, e uma execução ao iniciar a aplicação) gera só a
   competência corrente: dias perdidos são recuperados dentro do mês, meses
   passados nunca são preenchidos. Com uma instância só, não há ShedLock.
4. **Dia inexistente vira o último dia do mês** (dia 31 em fevereiro → 28/29).
   Recorrência criada ou reativada depois do dia previsto começa no mês
   seguinte.
5. **Somente o proprietário da verba** cria, vê, confirma e descarta
   recorrências e pendências. Participantes (ADR-0026) não as veem; terceiros
   recebem 404. Pendências vencidas (competência anterior) continuam listadas
   até confirmação ou descarte.
6. **Confirmação pelo caminho do lançamento manual** (`LedgerService.register`):
   saldo, alertas, meta atingida e auditoria (ADR-0027) se comportam igual.
7. **Primeiro evento de aplicação.** Arquivar a verba publica
   `EnvelopeArchived`; o módulo `ledger` escuta de forma síncrona, na mesma
   transação, desativando recorrências e descartando pendências. Isso evita a
   dependência `envelopes → ledger`. Sem fila, sem entrega assíncrona.
8. **Exclusão lógica da recorrência** (`deleted_at`), preservando o histórico
   de ocorrências confirmadas para a métrica (% dos lançamentos vindos de
   recorrência). A exclusão do titular remove tudo em cascata com a verba
   (ADR-0023).

## Alternativas consideradas

### Status em `ledger_entry`

- Vantagem: uma tabela só.
- Desvantagem: toda soma de saldo, resumo, relatório e exportação precisaria
  filtrar `status = 'CONFIRMED'`; um filtro esquecido faria pendências
  afetarem o saldo. Rejeitada.

### Lançamento automático

- Vantagem: zero toques.
- Desvantagem: gasto cancelado entra no saldo sem o usuário perceber.
  Adiada até haver métrica de uso.

### Gerar sob demanda ao abrir a visão geral

- Vantagem: dispensa job.
- Desvantagem: mistura escrita com leitura e faz a geração depender de acesso.
  Rejeitada.

### Tratar o arquivamento só na leitura

- Desvantagem: recorrências "ativas" em verba arquivada e pendências órfãs.
  Rejeitada em favor do evento síncrono.

## Consequências

- Endpoints novos: `/api/v1/envelopes/{id}/recurring-entries` e
  `/api/v1/recurring-occurrences` (pendentes, confirmar, descartar).
- Migration `V17__create_recurring_entries.sql` (expand-only).
- Web, Android e desktop ganham gestão de recorrências e bloco de pendências;
  MCP fica fora deste corte.
- Logs da geração só com contagens e IDs (ADR-0003).

## Referências

- Issue: [#84](https://github.com/Arthur-MARKOWICZ/Controle_de_Gastos/issues/84)
- Change OpenSpec: `openspec/changes/lancamentos-recorrentes-com-confirmacao/`
- ADR-0006, ADR-0007, ADR-0022, ADR-0023, ADR-0026, ADR-0027
