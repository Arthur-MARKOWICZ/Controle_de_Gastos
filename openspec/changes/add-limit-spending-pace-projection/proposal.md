# Proposal

## Why

Os relatórios atuais de verbas `LIMIT` são retrospectivos (`limit-exceeded-months`): o titular só descobre o estouro depois do fechamento do mês. Falta um sinal preventivo de ritmo — projeção do gasto do mês, dia previsto de estouro e quanto ainda dá para gastar por dia — para agir enquanto ainda há tempo (issue #87 / NEG-005).

## What Changes

- Nova consulta de leitura pura no módulo `reporting` que, para o mês civil em `America/Sao_Paulo`, calcula a projeção de ritmo das verbas `LIMIT` visíveis ao solicitante:
  - gasto até hoje ÷ dias decorridos × dias do mês;
  - dia previsto de estouro (quando aplicável);
  - allowance diária restante até o fim do mês.
- Novo endpoint JSON autenticado (não substitui nem altera os exports CSV/XLSX existentes).
- Exibição desses indicadores na visão geral e nos cartões de verba `LIMIT` na web, no Android e no desktop.
- Valores monetários projetados como strings decimais com duas casas; sem tabela nova, sem cache e sem materialização de arquivos.

### Fora de escopo

- Alerta in-app / push / V14 quando a projeção passar de 100% (mencionado como opcional na issue; permanece com `NEGATIVE_BALANCE` reativo).
- Projeção de ritmo para `FIXED`, `GOAL`, `SAVINGS_TARGET` ou `ANNUAL_EXPENSE`.
- Alteração dos relatórios exportáveis existentes ou da fórmula de saldo mensal (ADR-0022).
- Persistência de projeções, filas ou qualquer mecanismo de cache.
- Canal MCP (não expõe overview de ritmo neste corte).

## Capabilities

### New Capabilities

- `reporting/spending-pace`: consulta de projeção de ritmo do mês corrente para verbas `LIMIT` (gasto projetado, dia de estouro, allowance diária), com autorização por visibilidade da verba.

### Modified Capabilities

- `ui-web/overview`: visão geral passa a destacar verbas `LIMIT` com ritmo que projeta estouro ou allowance diária útil.
- `ui-web/envelope-card`: cartão `LIMIT` exibe dia previsto de estouro e/ou quanto ainda dá para gastar por dia.
- `ui-mobile/cartao-de-verba`: mesmo sinal preventivo no card Android.
- `ui-desktop/overview`: visão geral desktop inclui o ritmo das verbas `LIMIT` visíveis.
- `ui-desktop/envelopes`: lista/cartão desktop de `LIMIT` mostra os mesmos indicadores.

## Impact

- **API**: novo `GET` JSON em `/api/v1/reports/...` no `ReportingController` / `ReportingService`; reuso das portas de leitura do ledger (`LedgerReportingQuery` ou equivalente); OpenAPI atualizado.
- **Clientes**: web (`Dashboard` / `EnvelopeCard`), Android (`MonthWorkspace` / card de verba) e desktop (`OverviewScreen` / envelopes) consomem o endpoint; `GET /api/v1/ledger/summary` permanece sem campos de ritmo.
- **ADRs**:
  - **ADR-0014**: restringe relatórios a consultas sem tabelas/cache; a projeção é leitura efêmera — alinhada. A alternativa rejeitada “Criar projeções ou arquivos persistidos” refere-se a materialização, não a este cálculo sob demanda; nenhum ADR novo obrigatório se o contrato JSON for só mais uma consulta do módulo `reporting`. Se o OpenAPI/ADR-0014 for interpretado como lista fechada de três endpoints, o design documentará a extensão mínima do contrato (e, se necessário na aplicação, um adendo curto ao ADR).
  - **ADR-0006 / ADR-0022 / ADR-0007**: dinheiro `BigDecimal` escala 2; gasto do mês sem carry; gasto além do limite continua permitido — o ritmo é sinal, não bloqueio.
  - **ADR-0033 / notifications**: alerta preventivo fica fora; sem mudança em event-alerts neste change.
- **Testes**: TDD nos cálculos (início do mês, verba zerada, meses 28/29/30/31) e nos canais de UI listados.
- **Issue**: [GitHub #87](https://github.com/Arthur-MARKOWICZ/Controle_de_Gastos/issues/87); branch de trabalho sugerida: `issue-87-limit-spending-pace`.
