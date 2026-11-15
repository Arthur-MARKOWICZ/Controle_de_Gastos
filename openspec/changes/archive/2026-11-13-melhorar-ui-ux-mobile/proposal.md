# Proposal

## Why

O canal mobile existe para o lançamento diário ser rápido, mas a auditoria estática da UI/UX mostrou fricção no caminho principal: ações do card estouram a largura, o gasto abre em dois `AlertDialog`, a máscara de centavos não explica o valor, alertas persistentes somem da home e o login trata erro como mensagem de marca. Sem isso, a premissa ainda aberta de “lançar com pouco esforço vira hábito” fica bloqueada pela própria superfície.

## What Changes

- Substituir o fluxo FAB → picker → `AlertDialog` por uma folha inferior única de lançamento (verba, valor, data, descrição), também alcançável na aba Gastos.
- Reorganizar o card de verba: uma ação primária visível e as demais num menu, para caber em telefone estreito sem toque acidental em Encerrar.
- Explicar a máscara de centavos e mostrar a prévia do saldo após o gasto, no mesmo modelo da web.
- Vincular a data do lançamento ao mês exibido, mostrar esse mês no título da folha e não oferecer lançamento em mês futuro.
- Mostrar alertas persistentes de saldo negativo na Visão geral, não só em Verbas.
- Corrigir feedback de autenticação: erro em cor de erro, mostrar/ocultar senha, autofill, saída da tela de MFA e área segura nas telas não autenticadas.
- Ajustes de clareza no cromado: rótulos da barra, e-mail só em Mais, datas em pt-BR, ícones no lugar de emoji de estado, confirmação destrutiva com ênfase de risco.

Fora de escopo:

- Redesign de paleta, tipografia ou identidade Verbas (teal/coral já alinhados à web).
- Gráficos no mobile; relatórios continuam exportáveis.
- Expor alocação de patrimônio no card (paridade de produto, change separado).
- Alterar API, regras de saldo, autorização ou contratos JSON.
- Cache, filas, microserviços.
- Validar ou publicar iOS (código compartilhado permanece; alvo verificável é Android).
- Alterar a web neste change, salvo reutilizar o padrão já existente de hint e prévia de saldo.

ADRs: ADR-007 (lançamento rápido em bottom sheet; status ainda proposto — este change realiza a parte mobile sem mudar o modelo de `ledger`); ADR-004 (Android primeiro); ADR-013 (tema local; não enviar preferência à API); ADR-022 (textos de limite/compromisso que reiniciam). Nenhum ADR novo: a folha usa Material 3 já presente.

## Capabilities

### New Capabilities

- `ui-mobile/lancamento-rapido`: registrar gasto ou aporte no Android com poucos toques, valor compreensível e data coerente com o mês visto.
- `ui-mobile/cartao-de-verba`: ações do card cabem na largura, com primária óbvia e destrutiva afastada.
- `ui-mobile/estados-e-acesso`: alertas na home, feedback de erro/sucesso, sessão (login/MFA) e cromado previsível.

### Modified Capabilities

- (nenhuma — `openspec/specs/` ainda não tem capacidades duráveis)

## Impact

- Somente `mobile/shared` (Compose Multiplatform) e testes em `mobile/shared/src/commonTest`. `androidApp` só se insets/autofill exigirem gancho de plataforma.
- Sem mudança de backend, OpenAPI, web ou MCP.
- Dependências: `ModalBottomSheet` / componentes Material 3 já no módulo; sem biblioteca nova de navegação ou cache.
- Verificação: testes de navegação/controladores existentes + testes novos de composição das ações, da data, da máscara e da prévia de saldo em `./gradlew :shared:jvmTest` (conforme o README). O projeto não tem teste de Compose UI: overflow em 360 dp, dica de centavos, teclado na folha e área segura das telas anônimas ficam como aceite manual declarado no fechamento da change.
