# Design

## Context

- Ver proposal.md (Why). Hoje o desktop só tem atalho **de janela** `Ctrl+N`
  (`useExpenseCapture.tsx`); ADR-0029 exclui tray, atalho global e autostart.
- Android já abre lançamento rápido via `MainActivity.EXTRA_QUICK_EXPENSE`
  (lembrete diário). Não há widget Glance nem `TileService`.
- `ledger_entry` guarda `occurred_at` (`LocalDate`, data de negócio) e
  `created_at` (`Instant`). Não há hora na data de negócio.
- Registro de gasto: `POST /api/v1/envelopes/{id}/entries`. Resumo:
  `GET /api/v1/ledger/summary`. Não existe consulta de sugestão.
- Canais no change: API, web, Android, desktop. MCP fica fora (proposal).

## Goals / Non-Goals

**Goals:**
- Uma regra de sugestão na API, reutilizada por web, Android e desktop.
- Superfícies de SO (widget/tile e tray/atalho/autostart) só disparam o fluxo
  de captura já existente; não inventam regra financeira no cliente.
- Emenda explícita ao ADR-0029 no mesmo change.

**Non-Goals:**
- Materializar ranking em tabela ou job.
- Medir a métrica de ≤ 5 s no CI.
- Validar Windows/macOS tray/atalho/autostart.

## Decisions

### D1. Endpoint de leitura em `ledger`
`GET /api/v1/ledger/expense-suggestions` autenticado, sem query params no
primeiro corte (o "agora" é o relógio do servidor em `America/Sao_Paulo`).

Resposta (esboço):

```json
{
  "asOf": "2026-11-27T14:30:00-03:00",
  "items": [
    {
      "envelopeId": "...",
      "name": "Almoço",
      "suggestedAmount": { "amount": "32.50", "currency": "BRL" }
    }
  ]
}
```

`suggestedAmount` pode ser `null`. No máximo 3 itens. Só verbas visíveis em que
a pessoa pode registrar `EXPENSE` e não arquivadas.

- **Alternativa rejeitada:** calcular no cliente a partir do histórico paginado.
  Exigiria baixar muitos lançamentos, divergiria entre canais e vazaria lógica.
- **Alternativa rejeitada:** módulo novo. A agregação é sobre `ledger_entry`;
  contrato público novo no módulo existente, com emenda ao ADR-0029 (desktop) e
  sem ADR de módulo.

### D2. Janela e escore (sem ML, sem cache)
- Janela: últimos **90 dias** de `occurred_at` (gastos `EXPENSE`, `deleted_at`
  IS NULL, `author_id` = usuário autenticado).
- Mínimo para ranquear uma verba: **2** gastos na janela (abaixo disso a verba
  não entra; evita “Farmácia” com um único evento dominar).
- Mínimo para `suggestedAmount`: **2** ocorrências do mesmo valor na verba; senão
  `null` (alinha o cenário “histórico insuficiente” da spec).
- Dia da semana: `EXTRACT(DOW)` / `DayOfWeek` de `occurred_at`.
- Faixa horária: hora de `created_at` convertida para `America/Sao_Paulo`,
  bucket de 1 hora. Usamos `created_at` porque `occurred_at` não tem hora —
  aproxima “quando a pessoa costuma lançar”, não o instante do gasto no mundo.
- Escore por verba (maior vence; empate → mais recente `created_at` na verba):

  ```
  score = 3 * count(mesmo DOW e mesmo bucket de hora)
        + 2 * count(mesmo DOW, outro horário)
        + 1 * count(outros dias)
  ```

- Valor sugerido: moda dos `amount` na janela para aquela verba; empate → valor
  do gasto mais recente.

Consulta em SQL agregado no PostgreSQL (ou agregação em memória se o volume do
usuário for pequeno e o teste ficar mais claro). **Sem** índice novo no primeiro
corte; se `EXPLAIN` mostrar seq scan custoso em massa, ADR/índice depois.
**Sem cache.**

### D3. Android: Glance + tile reutilizando deep link
- Widget Glance em `androidApp`, atualização periódica / ao abrir o app /
  após login, chamando a API via gateway já autenticado.
- Ações PendingIntent → `MainActivity` com `EXTRA_QUICK_EXPENSE=true` e, se
  toque em verba, `EXTRA_ENVELOPE_ID` (novo extra UUID string).
- `TileService` com a mesma Intent genérica de lançamento rápido.
- Shared/KMP: a folha lê sugestões ao abrir; se `envelopeId` veio no intent,
  ele tem prioridade sobre o ranking (spec).

- **Alternativa rejeitada:** App Widget XML clássico. Glance é o caminho atual
  da issue e reduz XML boilerplate.
- iOS: sem implementação validável.

### D4. Desktop: plugins Tauri 2
Dependências novas no crate `desktop/src-tauri` (e permissões Capacidade):

| Necessidade | Plugin / API |
|-------------|--------------|
| Atalho global | `tauri-plugin-global-shortcut` |
| Bandeja | API tray do Tauri 2 (`tauri::tray`) — já há permissões `core:tray` no schema gerado |
| Autostart | `tauri-plugin-autostart` |

Fluxo: atalho/tray emitem evento para o webview → `useExpenseCapture` /
comando que mostra a janela e abre o diálogo. Autostart: preferência em arquivo
local de config do app (mesmo espírito do tema no ADR-0029), default off.

Emenda ao **ADR-0029**: remover tray/atalho global/autostart da lista "fora do
corte"; declarar Linux como alvo validável dessas três superfícies; manter
Windows/macOS sem declaração de teste.

O atalho in-window `Ctrl+N` permanece.

### D5. Clientes consomem a mesma API
Web (`QuickExpenseDialog`), desktop (`ExpenseDialog` / `useExpenseCapture`) e
mobile (folha de lançamento) fazem `GET` ao abrir o formulário sem verba forçada.
Falha de rede → formulário vazio/heurística antiga, sem toast agressivo.

MCP: fora (proposal).

### D6. Privacidade e logs
Mesmas regras do ledger: handlers não logam amount, description nem lista de
sugestões. Autorização = verbas que `EnvelopeService.listVisible` + regra de
gasto (dono/participante spendable), nunca vazar verba alheia.

### D7. Testes
- Backend: TDD no serviço de sugestão (cenários DOW/hora, moda, empate,
  arquivada, participante, terceiro) + teste de API de integração.
- Desktop: testes unitários da UI de prefill onde já há Vitest; atalho/tray
  validação manual Linux documentada na task.
- Android: testes de lógica do PendingIntent / extras; widget no emulador como
  aceite manual da issue.

## Risks / Trade-offs

- [Hora via `created_at`] → pode divergir de “hora do gasto real” se a pessoa
  lançar atrasado. Mitigação: peso maior no DOW; bucket só como bônus.
- [Atalho global conflita com outros apps] → Mitigação: padrão documentado;
  change futuro pode tornar o atalho configurável (fora deste corte).
- [Tray/autostart frágeis no Wayland/X11] → Mitigação: validar no ambiente
  Linux do mantenedor; falha degradada (app ainda abre manualmente).
- [Widget sem rede / token expirado] → Mitigação: estado “abra o app”; refresh
  de sessão só dentro do processo do app, não no processo do widget isolado
  sem credencial.
- [PR grande (API+Android+desktop)] → Aceito pela escolha de escopo A; tasks
  ordenadas para API primeiro desbloquear clientes.

## Migration Plan

1. Emendar ADR-0029.
2. Entregar API + testes (sem migration Flyway se não houver tabela).
3. Clientes web/desktop/mobile consomem sugestão.
4. Desktop plugins (shortcut/tray/autostart).
5. Android widget + tile.
6. Rollback: reverter deploy da API (clientes degradam para heurística antiga);
   widgets/tiles e plugins desktop são só cliente.

## Open Questions

Nenhuma que altere specs ou o breakdown: defaults de escore (pesos 3/2/1,
janela 90d, mínimos 2) ficam neste design; ajustar pesos depois é mudança de
comportamento fina coberta pelos mesmos requisitos se os cenários continuarem
válidos.
