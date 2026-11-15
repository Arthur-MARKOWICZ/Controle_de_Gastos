# Design

## Context

See `proposal.md` — Why. Canal afetado: somente web (Next.js 16, CSS Modules, componentes em `web/src/components`). API, Android e MCP não mudam.

Estado atual relevante:

- `AppShell` declara sete destinos; em `max-width: 64rem` a lista usa `repeat(5, 1fr)`.
- `Dashboard` empilha `IncomeCard` e `BalanceSummary` (ambos `.summary`) e um header com três CTAs.
- `EnvelopeCard` trata todas as ações como `Button variant="text"` e calcula progresso LIMIT como restante/base.
- `HistoryPage` e `ReportsPage` não usam `PageHeader`/`Button`; Configurações/Segurança importam `.pageHeader` de `Verbas.module.css` (classes inexistentes).
- `--font-sans: Inter` em `globals.css` sem `next/font` nem `@font-face`.
- Renda é sempre da sessão autenticada (`useIncome`); participante não edita renda de outro usuário.

Restrições: ADR-007 (dialog de lançamento, WCAG, anti-AI-aesthetic), ADR-0022 (reinício por natureza), Money em string decimal, sem cache, TDD nas regras de apresentação cobertas por teste.

## Goals / Non-Goals

**Goals:**

- Decidir o recorte técnico que satisfaz as specs `ui-web/*` reusando `PageHeader`, `Button`, `Field`, `Dialog` e `PersistentAlerts`.
- Corrigir layout e copy sem novo design system, nova paleta ou nova lib de ícones.

**Non-Goals:**

- Novo padrão arquitetural, módulo Spring ou contrato REST.
- Paridade visual Android neste change.
- Relatórios além do download CSV/XLSX já existente.

## Decisions

### 1. Rótulo de `/gastos` vira "Histórico"

A rota permanece `/gastos`. O item de nav e o `h1` passam a **Histórico**, para não colidir com **Gastos anuais**.

Alternativa considerada: "Lançamentos" — mais preciso, menos óbvio para quem já usa "Gastos". Histórico descreve a tarefa da página (consultar e corrigir) e convive com o CTA "Registrar gasto".

### 2. Nav responsiva com flex wrap, não grade de 5

Em viewports ≤64rem, `.navList` usa `display: flex; flex-wrap: wrap` (ou `auto-fit`) em vez de `repeat(5, 1fr)`. Índices `01–07` escondem-se já nesse breakpoint. Destinos e `aria-current` permanecem.

Alternativa: agrupar Metas e Gastos anuais sob Verbas — muda IA demais e foge do recorte.

### 3. Um resumo na Visão geral

Fundir `IncomeCard` + `BalanceSummary` num único bloco:

- Lead: valor da renda da sessão (ou "Renda não configurada") + vigência.
- Stats: já reservado, não alocado, uso.
- Ação "Editar"/"Configurar renda" só nesse bloco.

Glossário sai do header e vira link de texto na descrição. "Nova verba" permanece no header; some o botão duplicado do rodapé. Histórico de renda fica na coluna lateral.

Forma concreta: nasce um componente `MonthSummary` a partir de `BalanceSummary` (que já recebe renda, alocado, não alocado e `usagePct`) e `IncomeCard` é removido junto com seu teste; a ação de editar/configurar renda migra para o novo bloco. Não deixar os dois componentes no repositório com um deles sem uso — as duas `section.summary` concorrentes são exatamente o que a spec proíbe.

Participante continua vendo o controle de renda da **própria** sessão. Não esconder por papel de envelope compartilhado.

Alternativa: dois cards com copy diferente — ainda duplica o número da renda, o que a spec proíbe.

### 4. Card: botão primário + "Mais"

Ação primária: `Button` default ("Registrar gasto" ou "Aportar"). Ações secundárias (Participantes, Encerrar, Editar alvo, Patrimônio) entram em um `<details>` "Mais" nativo, sem lib de menu.

Verbo de encerrar: **Encerrar** no botão e no `ConfirmDialog` (hoje o botão diz "Arquivar").

Alternativa: kebab menu customizado — mais acessibilidade para implementar. `<details>` já tem teclado.

### 5. Progresso LIMIT/FIXED = percentual gasto

`ProgressBar` em LIMIT e FIXED recebe `% usado` (`(base - disponível) / base`). GOAL, SAVINGS_TARGET e ANNUAL_EXPENSE continuam com `% concluído`. Cor de propósito é complemento.

O texto de status não pode simplesmente "permanecer": hoje `EnvelopeCard` calcula um único `pct = disponível / base` e o reaproveita no texto. Em LIMIT o texto já é `"X restantes · N% usado"` (usa a variável `spent`), então só a barra muda. Em **FIXED** o texto é `"X reservados · N%"` com o `pct` de restante — se a barra passar a gasto e o texto mantiver o mesmo número, o card exibe duas leituras opostas do mesmo valor. Decisão: em FIXED o texto passa a `"X a pagar · N% pago"`, com `N` igual ao valor da barra, e "reservado" fica só onde descreve meta ou provisão (GOAL, SAVINGS_TARGET, ANNUAL_EXPENSE).

Alternativa: duas barras ("resto" vs "feito") — dobra complexidade sem ganho. A spec pede uma metáfora por família.

### 6. Histórico ganha CTA de lançamento

`HistoryPage` usa `PageHeader` com ação "Registrar gasto". O fluxo reusa `ExpenseForm` + `Dialog`: primeiro escolher verba em que `role` permite gastar, depois o formulário. Lista de verbas: `getEnvelopes` do mês de `to`. Estado vazio aponta para a mesma ação.

Datas visíveis: `toLocaleDateString("pt-BR", { timeZone: "America/Sao_Paulo" })`. Inputs `type="date"` permanecem ISO.

"Gastos por tipo" ganha a mesma barra proporcional já usada em "Gastos por mês", mais o valor em texto.

### 7. Inter via `next/font/local`

Em `app/layout.tsx`, carregar Inter (pesos 400–800, `display: swap`) com `next/font/local` a partir de `web/src/app/fonts/Inter-latin-wght-normal.woff2` e aplicar a variável no `html`. Tokens `--font-sans` apontam para essa variável. Sem pacote npm extra. A fonte fica versionada no repositório: o `pnpm build` da imagem Docker não consulta `fonts.googleapis.com` nem `fonts.gstatic.com` (o Turbopack do Next 16.2 quebra o build quando o Google devolve URL sem extensão). Nenhuma requisição a Google Fonts em tempo de execução, o que preserva a restrição de privacidade. O change `add-tauri-desktop-client` copia estes tokens; ao mexer aqui, a cópia do desktop fica defasada quanto à fonte (risco registrado lá).

`PageHeader h1` reduz para `clamp(1.75rem, 4vw, 2.25rem)` — ferramenta, não capa.

Configurações e Segurança passam a importar `PageHeader`. Seções internas de Segurança usam `h2`. Relatórios usa `PageHeader` + `Button`.

### 8. Erros de campo nos forms existentes

`EnvelopeForm` e `ExpenseForm` passam a enviar a mensagem de validação/API de campo para `Field.error` (como `IncomeForm` já faz). `Notice` no topo só para falha inesperada. Após submit inválido, foco no primeiro controle `aria-invalid`.

`PersistentAlerts` entra também no `Dashboard`, com o mesmo `client` já usado em Verbas.

### 9. Grade de Verbas/Metas

`.purposeGrid` e `.distribution` usam `repeat(auto-fit, minmax(16rem, 1fr))` para não deixar um buraco de terceira coluna quando há dois grupos.

## Risks / Trade-offs

- [Usuários habituados a "Gastos" na nav] → Mitigação: rota `/gastos` inalterada; `h1` e nav mudam juntos.
- [Barra LIMIT que hoje esvazia ao gastar passa a encher] → Mitigação: texto "restantes · % usado" permanece; testes do card atualizam o valor da barra.
- [Menu "Mais" esconde Participantes] → Mitigação: um clique extra em ação rara; lançamento fica visível.
- [Foco após erro em dialog] → Mitigação: reusar o seletor de focáveis já existente em `Dialog`; teste de formulário cobre o campo inválido.
- [Inter com `swap` pode trocar métricas no primeiro paint] → Mitigação: pesos 400–800 cobrem os `font-weight` atuais; fallback do sistema permanece.

## Migration Plan

Só frontend. Deploy = release da imagem web. Rollback = imagem anterior. Sem migração de dados, cache ou API.

Verificação: `cd web && pnpm test && pnpm lint` nas áreas tocadas; `make check` se o ambiente local estiver completo, conforme README.

## Open Questions

Nenhuma que altere specs ou o recorte acima.
