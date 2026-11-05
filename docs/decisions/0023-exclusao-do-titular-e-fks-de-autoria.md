# ADR-0023: Exclusão do titular e FKs de autoria (`author_id` / `added_by`)

## Status

Aceito

## Data

2026-11-05

## Contexto

`ledger_entry.author_id` (V5) e `envelope_participant.added_by` (V4) referenciam
`user_account` sem `ON DELETE` explícito. O status `DELETED` já existe em
`user_account`, mas não há fluxo completo de exclusão do titular. Apagar a
linha de um usuário que é autor em verba de terceiros falha por FK; cascata
das verbas próprias não resolve autoria compartilhada (issues DB-005 / DB-008).

Backup/restauração (DB-004) permanece fora deste ADR e continua pendente.

## Decisão

1. **Preservar o UUID do titular** após a exclusão lógica: a conta passa a
   `UserStatus.DELETED`, e-mail e credenciais são anonimizados/removidos, e a
   linha em `user_account` permanece. Assim `author_id` e `added_by` continuam
   válidos sem apagar histórico alheio.
2. **Declarar `ON DELETE RESTRICT`** nas FKs `ledger_entry.author_id` e
   `envelope_participant.added_by` (migration expand). Isso torna explícito que
   a exclusão física da conta só ocorre depois que a aplicação anonimizou e,
   se necessário, reapontou referências — nunca via `CASCADE` em autoria.
3. **Não usar `ON DELETE CASCADE` nem `SET NULL` em `author_id`**: cascata
   apagaria ou alteraria histórico de verbas de terceiros; `SET NULL` enfraquece
   a trilha de auditoria já exigida pela fundação técnica.
4. **Participações do titular em verbas alheias** são removidas na exclusão
   (`envelope_participant.user_id` já tem `ON DELETE CASCADE` quando a conta
   for eventualmente removida; no fluxo lógico, a aplicação remove as linhas).
5. **Dados financeiros próprios** (renda, verbas, lançamentos onde é owner)
   são apagados ou anonimizados pelo fluxo de direitos do titular (DB-008),
   coordenado pelo módulo `privacy`. Propagação a backups fica vinculada a
   DB-004 e não bloqueia o desenho acima.

## Alternativas consideradas

### `ON DELETE SET NULL` em `author_id`

- Vantagem: permite apagar `user_account` sem tocar no histórico.
- Desvantagem: perde a ligação estável de autoria; exige coluna nullable e
  muda o contrato de auditoria.
- Rejeitada no corte atual.

### `ON DELETE CASCADE` em `author_id`

- Vantagem: limpeza automática.
- Desvantagem: apagaria lançamentos em verbas de outros usuários.
- Rejeitada.

### Remover a FK e manter UUID órfão

- Vantagem: exclusão física imediata.
- Desvantagem: perde integridade referencial e dificulta joins de auditoria.
- Rejeitada enquanto a exclusão lógica com UUID estável for suficiente.

## Consequências

- Migration `V13` declara `RESTRICT` nas FKs citadas.
- Implementação da API de exclusão/exportação (DB-008) deve anonimizar a conta
  in-place e nunca deletar fisicamente enquanto existirem FKs ativas — o que
  fica seguro com `RESTRICT`.
- DB-009 (owner_id do ledger sem FK) permanece independente e aceito pelo
  ADR-007 / ADR-0024.

## Referências

- Issues: [#27 DB-005](https://github.com/Arthur-MARKOWICZ/Controle_de_Gastos/issues/27),
  [#30 DB-008](https://github.com/Arthur-MARKOWICZ/Controle_de_Gastos/issues/30)
- [ADR-003](0003-privacidade-desde-a-concepcao.md), [ADR-007](0007-gastos-registro-saldo-e-ux.md)
