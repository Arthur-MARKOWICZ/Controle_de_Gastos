# ADR-015: Metas de acumulação e organização financeira por objetivo

## Status

Aceito

## Data

2026-09-01

## Contexto

O produto já possui verbas `LIMIT`, `GOAL` e `FIXED`, saldo acumulável e
lançamentos explícitos de aporte ou gasto. `GOAL` possui uma semântica aceita:
o `baseAmount` representa a expectativa mensal de aportes para o relatório de
metas abaixo do esperado (ADR-014). O titular também precisa guardar dinheiro
para uma compra ou objetivo específico e acompanhar um valor total acumulado,
sem que ele seja reiniciado na virada do mês ou encerrado automaticamente ao
ser atingido.

Uma única página de verbas também mistura dois trabalhos distintos: organizar
o orçamento mensal e acompanhar objetivos de longo prazo. O histórico atual é
composto por gastos e deve ser descoberto como tal.

## Decisão

- Adicionar o propósito `SAVINGS_TARGET` às verbas. Ele requer
  `targetAmount` positivo em BRL, usa sempre `baseAmount = 0,00` e não entra
  no total de alocação mensal nem na restrição de renda.
- Medir o progresso pelo saldo disponível da verba, não pela soma histórica de
  aportes. Aportes aumentam o progresso e gastos o reduzem; o saldo continua
  acumulado entre meses pelas regras centrais do ledger.
- Registrar `targetReachedAt` uma única vez quando uma contribuição leva o
  saldo de abaixo do alvo para igual ou acima dele. A resposta da mutação
  informa a primeira conquista para que o cliente mostre uma mensagem de
  parabéns. Alterar o alvo ou cruzá-lo novamente não gera nova celebração.
- Manter a meta ativa após a conquista. Somente o proprietário pode alterá-la
  ou arquivá-la. As permissões de owner e participante permanecem as das
  verbas existentes.
- Organizar a web em Visão geral, Verbas, Metas, Gastos e Relatórios. Verbas
  concentra `LIMIT` e `FIXED`; Metas apresenta, em seções distintas, `GOAL` e
  `SAVINGS_TARGET`; Gastos substitui o nome da página Histórico e preserva uma
  rota de redirecionamento para links anteriores.
- Todo tipo de verba expõe uma regra curta em linguagem do usuário, além do
  nome técnico. Dinheiro continua como string decimal BRL no contrato e
  `BigDecimal`/`NUMERIC(19,2)` no backend.

## Alternativas consideradas

### Reutilizar `GOAL`

Rejeitada porque conflita com o relatório mensal definido no ADR-014 e faria o
mesmo campo representar simultaneamente uma expectativa mensal e um alvo total.

### Mostrar total histórico de aportes como progresso

Rejeitada porque apresentaria como guardado um dinheiro que já foi gasto. O
saldo disponível é a representação financeira atual da reserva.

### Encerrar automaticamente ao atingir o alvo

Rejeitada porque o titular pode continuar aportando ou decidir quando usar a
reserva. A conquista é uma informação, não uma transição automática de ciclo.

### Criar um módulo ou serviço separado de metas

Rejeitada porque a nova regra continua sendo uma capacidade de `envelopes` e
`ledger`; um módulo físico aumentaria a complexidade sem evidência de benefício.

## Consequências

- A migração, o agregado `Envelope`, o OpenAPI, os clientes e os testes devem
  reconhecer o novo propósito e seus campos condicionais.
- `targetAmount` e `targetReachedAt` são dados financeiros pessoais e entram
  no inventário de dados, nos controles de autorização, retenção e exclusão.
- Relatórios de `GOAL` preservam exatamente a semântica do ADR-014;
  `SAVINGS_TARGET` não entra em metas mensais abaixo do esperado neste corte.
- A interface deve ser acessível e responsiva com cinco destinos de navegação.
  O mobile continua usando o painel atual neste corte; não se declara uma nova
  navegação móvel como validada sem sua implementação e testes.

## Referências

- [Fundação técnica](../ideas/fundacao-tecnica.md)
- [Plano de metas de acumulação](../ideas/metas-de-acumulacao.md)
- [ADR-006: Valores monetários decimais](0006-valores-monetarios-decimais-e-renda-mensal.md)
- [ADR-014: Relatórios financeiros](0014-relatorios-financeiros-exportaveis.md)
- [Inventário de dados pessoais](../privacy/inventario-de-dados.md)
