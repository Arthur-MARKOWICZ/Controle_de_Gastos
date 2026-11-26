# Proposal

## Why

O titular quer acompanhar o patrimônio (ações da B3 e CDB) no mesmo app, sem
misturar com o orçamento de verbas. O intent
`docs/intent/rastreamento-investimentos.md` foi confirmado em 2026-11-10, mas o
ADR-0028 rejeitou explicitamente um módulo de carteira naquele corte e afirma
que cotação, rentabilidade e carteira por ativo "exigiriam novo ADR". Sem esse
ADR, as issues #76 a #80 não têm fronteira arquitetural nem regras de cálculo
acordadas. Esta change corresponde à issue #75.

## What Changes

- Novo **ADR-0034** em `docs/decisions/0034-modulo-de-investimentos.md`, aceito,
  que registra:
  - o módulo de negócio `investments` dentro do monólito modular (ADR-0001),
    validado pelo Spring Modulith, sem dependência de `envelopes`, `ledger` ou
    `reporting`;
  - o acesso restrito ao titular, sem participantes e sem compartilhamento;
  - o escopo do dia 1: posições manuais de ações B3 e CDB prefixado;
  - a valorização das ações pela cotação externa, com a última cotação válida
    persistida no PostgreSQL (não é cache), atrás de uma porta
    `QuoteProvider`. A escolha do provedor fica para um ADR filho dentro da
    issue #77;
  - a estimativa do CDB prefixado: valor bruto =
    `principal × (1 + taxa a.a.)^(dias corridos / 365)`, congelado no
    vencimento, sem IR/IOF, calculado em `BigDecimal` com a biblioteca
    `big-math` (sem `double`);
  - o P&L simples (valor atual − custo de aquisição);
  - a complementaridade com o ADR-0028: o orçamento classifica a verba de
    aporte, e a carteira registra o ativo. Os dois não se cruzam.
- Atualização de `docs/intent/rastreamento-investimentos.md` para apontar para
  o ADR-0034.
- Atualização da consequência do ADR-0028 apenas com um link
  "ver ADR-0034". A decisão do ADR-0028 não muda.
- Atualização da regra `specs` em `openspec/config.yaml` para incluir
  `investments` entre os módulos de negócio aceitos para capacidades.

### Fora do escopo

- Qualquer código de domínio, API, migração ou cliente (issues #76 a #80).
- Escolha do provedor de cotação (ADR filho na #77).
- Fundos, CDB pós-fixado (% do CDI), IR/IOF, marcação a mercado de títulos,
  sync ou import de corretora, gráficos e séries históricas.
- Base de 252 dias úteis e calendário de feriados.
- Inventário de dados LGPD das novas tabelas (entra com cada issue que criar
  tabela).

## Capabilities

### New Capabilities

Nenhuma. A change é só de documentação e configuração de planejamento.
Os requisitos de comportamento do módulo `investments` nascem nas changes das
issues #76 a #79. Por isso, `.openspec.yaml` declara `skip_specs: true`.

### Modified Capabilities

Nenhuma.

## Impact

- Documentação: `docs/decisions/` (ADR-0034 novo, link no ADR-0028) e
  `docs/intent/rastreamento-investimentos.md`.
- Planejamento: `openspec/config.yaml` (regra de organização de specs).
- ADRs que restringem a change: ADR-0001 (monólito modular), ADR-0003
  (privacidade desde a concepção), ADR-0006 (valores monetários decimais),
  ADR-0028 (alocação de patrimônio, permanece válido e ortogonal).
- O ADR-0034 antecipa a dependência `big-math` e uma integração HTTP externa
  de cotação. Ambas só entram no código nas issues #78 e #77.
- Código, API e clientes não mudam nesta change.
