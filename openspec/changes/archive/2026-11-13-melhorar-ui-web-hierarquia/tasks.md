# Tasks

## 1. Chrome e navegação

- [x] 1.1 Carregar Inter via `next/font` em `web/src/app/layout.tsx`, ligar `--font-sans` à variável da fonte e verificar no teste de tema ou no CSS computado que a família carregada inclui Inter
- [x] 1.2 Reduzir o `h1` de `PageHeader` para `clamp(1.75rem, 4vw, 2.25rem)` e verificar no teste de `PageHeader` que o heading único continua sendo `h1`
- [x] 1.3 Trocar o rótulo do destino `/gastos` para "Histórico" em `AppShell` e `PAGE_TITLES`, manter o href, e verificar no teste de `AppShell` que a nav mostra Histórico e Gastos anuais como itens distintos com `aria-current`
- [x] 1.4 Trocar a grade `repeat(5, 1fr)` da nav ≤64rem por flex wrap, esconder os índices `01–07` nesse breakpoint, e verificar no CSS/módulo que não resta `repeat(5`
- [x] 1.5 Passar Configurações e Segurança para `PageHeader`, converter os `h1` internos de Segurança em `h2`, e verificar nos testes dessas páginas um único `h1` e o heading de seção em nível 2
- [x] 1.6 Passar Relatórios para `PageHeader` + `Button` compartilhado e verificar no teste de `ReportsPage` o `h1` Relatórios e o botão de gerar relatório
- [x] 1.7 Remover os imports mortos de `../VerbasPage/Verbas.module.css` em `SettingsPage` e `SecuritySettings` (as classes `.pageHeader` e `.eyebrow` não existem nesse módulo) e verificar com busca que nenhum componente referencia classe ausente de `Verbas.module.css`

## 2. Card de verba

- [x] 2.1 Escrever o teste falhando de `EnvelopeCard` para LIMIT com base `"400.00"` e disponível `"200.00"`: progressbar em `50` (gasto) e texto com restantes `"200.00"`; implementar o cálculo e verificar que o teste passa
- [x] 2.1a Escrever o teste falhando de FIXED com base `"1200.00"` e disponível `"300.00"`: progressbar em `75` e texto "300,00 a pagar · 75% pago" (nunca `75%` na barra com `25%` no texto); implementar e verificar que o teste passa
- [x] 2.2 Ajustar testes existentes de LIMIT/FIXED (Dashboard, Verbas, `page.test`) ao percentual gasto e verificar `pnpm test` nesses arquivos
- [x] 2.3 Escrever o teste falhando: dono vê ação primária "Registrar gasto"/"Aportar" como botão não-texto e "Encerrar" dentro de "Mais"; participante não vê Encerrar nem Participantes; implementar `<details>` e o verbo Encerrar; verificar que o teste passa
- [x] 2.4 Verificar no teste de confirmação que o diálogo de encerrar usa o mesmo verbo Encerrar

## 3. Formulários

- [x] 3.1 Escrever o teste falhando de `ExpenseForm`: submit com valor inválido marca o campo de valor `aria-invalid`, associa a mensagem e foca o campo; implementar via `Field.error` e verificar que o teste passa
- [x] 3.2 Escrever o teste falhando de `EnvelopeForm` para nome vazio e para `allocation.exceeds_income` no campo de valor; implementar e verificar que `Notice` no topo fica só para erro inesperado
- [x] 3.3 Confirmar que `IncomeForm` já liga o erro de mínimo `"4000.00"` ao campo e, se faltar foco após submit, adicionar o mesmo padrão; verificar no teste de `IncomeForm`

## 4. Visão geral

- [x] 4.1 Escrever o teste falhando de `Dashboard`: um único valor de renda `"5000.00"` no resumo, stats de reservado/não alocado/uso, e um controle de configurar renda; fundir `IncomeCard`+`BalanceSummary` em `MonthSummary`; verificar que o teste passa
- [x] 4.1a Apagar o componente que ficou sem uso e seu teste, e verificar que nenhuma importação de `IncomeCard` sobrou e que só uma `section` usa a classe `.summary`
- [x] 4.2 Tirar Glossário do header (link na descrição), remover o segundo "Nova verba" do rodapé, limitar o preview a quatro verbas com link "Ver todas", e verificar no teste de `Dashboard`
- [x] 4.3 Estado vazio com CTA de criar primeira verba: escrever/ajustar teste e verificar o botão no empty state
- [x] 4.4 Montar `PersistentAlerts` na Visão geral e verificar no teste que um alerta de "Combustível" em `"2026-08"` aparece e pode ser dispensado
- [x] 4.5 Trocar `.purposeGrid` e `.distribution` por `auto-fit` em `Verbas.module.css` e verificar que o CSS não força três colunas vazias

## 5. Histórico

- [x] 5.1 Trocar o `h1` e a copy de `HistoryPage` para Histórico, usar `PageHeader` com "Registrar gasto", e verificar nos testes de `HistoryPage` e `accessibility.test` o heading Histórico (não "Gastos" como título da página)
- [x] 5.2 Escrever o teste falhando do fluxo: dono com verba gastável abre o diálogo, escolhe a verba e vê `ExpenseForm`; estado vazio oferece a mesma ação; participante não vê Editar/Excluir em item que não é OWNER; implementar e verificar
- [x] 5.3 Formatar `occurredAt` `"2026-08-10"` em data pt-BR na lista (não a string ISO) e verificar no teste de `HistoryPage`
- [x] 5.4 Dar barra proporcional a "Gastos por tipo" (LIMIT `"1200.00"`, GOAL `"300.00"`) além do texto e verificar no teste que cada tipo tem valor e encoding visual

## 6. Integração

- [x] 6.1 Rodar `cd web && pnpm test && pnpm lint` (scripts `vitest run` e `eslint`) e verificar saída zero
- [x] 6.2 Revisar a nav em 64rem e em 38rem (7 destinos) e verificar que nenhum rótulo quebra em duas linhas nem fica mais estreito que o alvo de toque

## Workflow follow-up

- Aplicar o change com `/openspec-apply-change` quando os artefatos forem aceitos.
- Arquivar o change depois que a implementação e a revisão do projeto estiverem satisfeitas.
- Não fazer commit, push ou PR como parte destas tarefas.
