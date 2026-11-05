# ADR-0027: Auditoria detalhada de edições no ledger

## Status

Aceito — substitui o adiamento do [ADR-009](0009-adiamento-da-auditoria-detalhada-de-gastos.md)

## Data

2026-11-05

## Contexto

O ADR-009 adiou a trilha de versões de `ledger_entry` para permitir o corte
de histórico com edição e exclusão lógica. Com compartilhamento em piloto
(ADR-0026) e a dívida OPP-008 / issue #36, falta preservar estado anterior,
ator e instante de cada mutação, com regras de acesso, retenção, exportação e
exclusão alinhadas ao inventário.

## Decisão

1. **Tabela append-only `ledger_entry_revision` no módulo `ledger`**, no mesmo
   padrão de `income_revision` (sem eventos Modulith neste corte). O módulo
   `audit` permanece reservado a eventos transversais futuros.
2. **Escrita:** antes de aplicar edição ou exclusão lógica de um `EXPENSE`,
   gravar uma revisão com o estado **anterior** (`envelope_id`, `amount`,
   `description`), `action` (`EDIT` | `DELETE`), `actor_user_id` e `changed_at`.
   Exclusão já aplicada não gera revisão duplicada.
3. **Acesso de leitura:** somente o **proprietário** da verba (`owner_id` do
   lançamento) lista revisões. Participante e estranho recebem 403/404 como nas
   demais consultas sensíveis. Autor participante continua podendo editar/excluir
   (ADR-009), mas não consulta a trilha.
4. **Retenção:** acompanham o lançamento; `ON DELETE CASCADE` a partir de
   `ledger_entry`. `actor_user_id` usa `ON DELETE RESTRICT` (UUID preservado na
   exclusão lógica da conta, ADR-0023).
5. **Exportação do titular:** incluir revisões em que o usuário é `owner_id` ou
   `actor_user_id`. **Exclusão da conta:** remove revisões das verbas próprias
   via cascade dos lançamentos; revisões em verbas de terceiros em que o titular
   foi só ator permanecem com o UUID do ator.
6. **Logs:** apenas identificadores (`revisionId`, `entryId`, `actorId`); sem
   valores monetários nem descrição.

## Alternativas consideradas

### Módulo `audit` como consumidor de `ApplicationEvent`

- Vantagem: desacopla escrita do ledger.
- Adiada: não há infraestrutura de eventos; `income_revision` já prova o padrão
  síncrono no mesmo módulo.

### Participante lê a trilha

- Rejeitada neste corte: disputas e exportação ficam com o proprietário; reduz
  superfície LGPD para co-usuários.

## Consequências

- Totais históricos ainda refletem o estado atual do lançamento; a trilha
  explica *como* chegou lá.
- Inventário e OpenAPI passam a documentar `ledger_entry_revision`.
- Clientes expõem consulta mínima da trilha ao owner.

## Referências

- Issue: [#36](https://github.com/Arthur-MARKOWICZ/Controle_de_Gastos/issues/36)
- ADR-009, ADR-0023, ADR-0026
