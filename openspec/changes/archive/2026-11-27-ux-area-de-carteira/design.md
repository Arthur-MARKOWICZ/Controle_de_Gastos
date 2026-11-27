# Design

## Context

Ver `proposal.md` (Why) e as specs desta change. Nesta branch a API de
investimentos (#76–#79) já expõe:

- `GET/POST/DELETE /api/v1/investments/stocks`
- `GET/POST/DELETE /api/v1/investments/cdbs`
- `GET /api/v1/investments/summary`

Web (`AppShell` + páginas Next) e Android (`Route` / `MoreScreen` /
`VerbasGateways`) não têm cliente nem tela de carteira. Dinheiro na UI
continua como string decimal de duas casas (ADR-0006). Sem cache.

## Goals / Non-Goals

**Goals:**

- Um hub por canal, alimentado só por `/summary` para leitura.
- Cadastro e exclusão pelos endpoints de stocks/cdbs; formulários no padrão
  Dialog (web) / sheet ou dialog Compose (Android).
- Isolar navegação e copy de orçamento; rótulo **Carteira**.

**Non-Goals:**

- Mudar backend, contratos ou ADRs.
- MCP / desktop.
- Aba própria na bottom bar.
- Edição de posição, gráficos, fundos.

## Decisions

### D1. Hub único + `/summary` como fonte de leitura

A tela Carteira chama apenas `GET /summary` para montar totais, ações e
CDBs. Não reaggregate no client a partir de `GET /stocks` + `GET /cdbs`.
Após POST/DELETE bem-sucedidos, o hub refresca o summary.

- *Alternativa: listagens soltas + cálculo local.* Rejeitada: duplicaria
  regras de preço médio, `complete` e P&L já na API.

### D2. Navegação assimétrica por canal

| Canal | Entrada |
|---|---|
| Web | Destino de 1º nível no `AppShell` → `/carteira`, rótulo "Carteira" |
| Android | Linha em `MoreScreen` → `Route.Carteira` empilhada; bottom bar inalterada (5 itens) |

Motivo: a spec mobile exige rótulos sem ellipsis em 360 dp; 6 abas
quebrariam. Web já tolera mais um item na sidebar.

- *Alternativa: aba Carteira no Android.* Rejeitada no dia 1 (densidade).
- *Alternativa: Carteira só em Configurações na web.* Rejeitada: o intent
  trata a área como produto, não como ajuste.

### D3. Rótulo "Carteira" (não "Investimentos")

Evita colisão mental com verbas `GOAL` e com o glossário de aportes. O
módulo backend e o ADR continuam "investimentos"; o chrome do usuário diz
Carteira. Formulários: "Cadastrar ação" / "Cadastrar CDB".

### D4. Web: página + Dialog existentes

- Rota `web/src/app/carteira/` com `ProtectedPage` + `AppShell current="carteira"`.
- Cliente em `web/src/lib/api.ts` (métodos `getPortfolioSummary`,
  `registerStockLot`, `deleteStockLot`, `registerCdb`, `deleteCdb`).
- Formulários com `Dialog` / `ConfirmDialog` já usados no projeto.
- Header compartilhado (`PageHeader`): um `h1` "Carteira".
- Índices da sidebar: renumerar após inserir Carteira (posição sugerida
  após Visão geral ou antes de Configurações — preferência: após
  Relatórios / antes de Configurações, para não empurrar o fluxo mensal).

### D5. Android: Route + gateway + tela Compose

- `Route.Carteira` (não `TopLevel`); push a partir de Mais.
- Novo gateway (ex. `InvestmentsGateway`) em `VerbasGateways` com summary /
  register / delete; implementação HTTP no `androidApp` espelhando os
  outros gateways.
- Tela Compose no `shared` (testável em `jvmTest`), no mesmo espírito de
  `ReportsScreen` / `IncomeHistoryScreen`.
- Confirmação destrutiva com ênfase de risco (padrão de
  `ui-mobile/estados-e-acesso`).
- iOS: compilar se o shared exigir; sem declarar teste sem macOS/Xcode.

### D6. Rótulos parcial e estimado

| Condição | Copy |
|---|---|
| `complete == false` | Indicar resumo **parcial** (cotação ausente em alguma ação) |
| Valor de CDB | Prefixo/rótulo **estimado**; nunca como líquido de IR/IOF |
| `currentValue` / `pnl` null em ação | Não inventar número; omitir ou mostrar estado sem cotação |

### D7. Autorização na UI

Cada sessão autentica o dono da **própria** carteira. Participante de
verba alheia gerencia a carteira dele, não a do dono das verbas. Nav
"Carteira" visível para qualquer autenticado (mesmo rótulo). Terceiro sem
sessão: `ProtectedPage` / fluxo de login.

### D8. Sem mudança de API / ADR

Nenhum endpoint novo. Nenhum ADR novo. MCP e desktop ficam fora
(ADR-0034).

## Risks / Trade-offs

- [Carteira “escondida” no Mais no mobile] → Aceito no dia 1; pode subir
  para aba depois se o uso diário justificar.
- [Sidebar web fica longa] → Aceitável; destino único e rótulo curto.
- [Taxa CDB em decimal `0.1250` confunde] → Formulário deve deixar claro
  "taxa anual decimal (ex.: 0.1250 = 12,50% a.a.)"; validação continua na API.
- [Summary incompleto confunde totais] → Mitigado pelo rótulo "parcial".
- [Duplicar DTOs web/Android] → Aceito: cada canal já mapeia contratos
  HTTP localmente.

## Migration Plan

Só clientes. Deploy independente do backend desde que #76–#79 estejam no
mesmo release. Rollback: remover rota/entrada de nav; API intacta.

## Open Questions

Nenhuma. Decisões de canal, nav, nome, hub e rótulos fechadas na exploração
da issue #80.
