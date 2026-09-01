# Metas de acumulação e navegação financeira

## Problema

Como permitir que o titular guarde dinheiro para um objetivo específico — por
exemplo, uma compra — acompanhando um valor total acumulado ao longo dos
meses, sem confundir esse objetivo com a meta mensal de aportes já representada
por `GOAL`.

## Direção recomendada

Criar um novo propósito de verba, `SAVINGS_TARGET`, para uma meta de
acumulação. Ele terá um valor total obrigatório (`targetAmount`) e usará o
saldo disponível atual da verba como progresso. Por exemplo: saldo de
R$ 100,00 para alvo de R$ 1.000,00 significa 10% concluído e R$ 900,00
restantes.

`SAVINGS_TARGET` não reutiliza `GOAL`. `GOAL` continua significando uma meta
mensal de aportes, conforme ADR-014; usar o mesmo propósito para os dois casos
mudaria relatórios já aceitos e deixaria a regra ambígua.

O novo tipo segue as regras comuns de verbas: aportes e gastos alteram o
saldo, o saldo pode atravessar meses sem reiniciar e só o proprietário pode
alterar ou encerrar a verba. Para não receber alocação automática mensal nem
reduzir o dinheiro não alocado, seu `baseAmount` é sempre R$ 0,00.

## Regras de cada tipo de verba

Cada card deve apresentar sua regra em linguagem direta, além do nome do tipo:

| Tipo | Regra exibida ao usuário |
| --- | --- |
| `LIMIT` — Limite de gasto | “Você planeja usar até **R$ X por mês**. O saldo não utilizado continua disponível nos próximos meses.” |
| `FIXED` — Compromisso fixo | “Reserve **R$ X por mês** para esta despesa recorrente. Registre o pagamento quando ele acontecer.” |
| `GOAL` — Meta de aporte | “Faça aportes de pelo menos **R$ X por mês** para avançar nesta meta, como investimentos.” |
| `SAVINGS_TARGET` — Meta de acumulação | “Junte qualquer valor até alcançar **R$ Y**. Seu saldo acumulado não reinicia no próximo mês.” |

Para `SAVINGS_TARGET`, o card exibe também `saldo atual de targetAmount`,
percentual limitado ao intervalo de 0% a 100% e o valor restante. O cálculo de
dinheiro permanece no backend; o cliente apenas apresenta os valores decimais
recebidos.

## Comportamento da meta de acumulação

- `targetAmount` é BRL positivo, com duas casas e sem arredondamento implícito.
- O progresso usa o saldo disponível: aportes o aumentam e gastos o reduzem.
  Portanto, o sistema não apresenta como dinheiro guardado um valor já gasto.
- Como `baseAmount` é zero, a passagem de mês não cria nem remove valor da
  meta. O saldo é o acumulado dos lançamentos ativos desde a criação.
- A primeira contribuição que leva o saldo de abaixo do alvo para igual ou
  acima dele registra `targetReachedAt` e devolve o estado de conquista para o
  cliente mostrar uma única mensagem de parabéns.
- Se o saldo cair depois, não há uma segunda mensagem ao cruzar o alvo
  novamente. A mensagem é uma celebração única da verba, não um alerta
  recorrente.
- O proprietário pode editar o alvo. A edição atualiza o progresso exibido,
  mas nunca produz mensagem de parabéns; para uma nova celebração, cria-se uma
  nova meta.
- A meta continua ativa após ser alcançada. Somente o proprietário a encerra
  pelo arquivamento já existente; o histórico permanece consultável.

## Organização da experiência web

A navegação passa a representar os trabalhos que a pessoa quer executar:

| Página | Conteúdo |
| --- | --- |
| Visão geral | Resumo do mês, alertas e atalhos para as áreas principais. |
| Verbas | Orçamento mensal: limites de gasto e compromissos fixos. |
| Metas | Seções separadas para metas de aporte (`GOAL`) e metas de acumulação (`SAVINGS_TARGET`). |
| Gastos | Registro, edição e consulta de gastos; evolui a página atual de Histórico. |
| Relatórios | Exportações e análises por período. |

A página **Metas** não mistura as duas regras em uma única lista sem contexto:

- **Metas de aporte** mostram o valor esperado por mês e os aportes feitos.
- **Metas de acumulação** mostram “R$ 100,00 de R$ 1.000,00 · 10% concluído ·
  faltam R$ 900,00”, com ação para aportar, editar o alvo ou encerrar.

No MVP, a página de gastos pode reaproveitar o comportamento e os dados de
`/historico`; a rota antiga deve redirecionar para a nova para preservar links.
O mobile continua exibindo os valores no painel existente neste corte. Uma
navegação mobile própria para Metas e Gastos é uma entrega posterior, pois hoje
o aplicativo possui somente o painel financeiro.

## Impactos técnicos previstos

- Nova migração Flyway: acrescentar `SAVINGS_TARGET` à restrição de propósito
  e adicionar `target_amount NUMERIC(19,2)` e `target_reached_at` à verba.
  `target_amount` é obrigatório e maior que zero somente para esse propósito.
- Domínio: validar a combinação de propósito, valor-base e alvo; detectar a
  primeira travessia dentro da transação que registra o aporte.
- API/OpenAPI: expor `targetAmount`, `targetReachedAt`, saldo atual e estado de
  conquista nas respostas de verba e de lançamento. Todos os clientes recebem
  dinheiro como string decimal BRL.
- Autorização: conservar as regras atuais — owner pode aportar, editar o alvo
  e arquivar; participantes só podem ver e registrar gastos quando forem
  autorizados na verba compartilhada.
- Privacidade: atualizar o inventário de dados para classificar o alvo e a
  data de conquista como dados financeiros pessoais. Logs não registram valores
  ou descrições de lançamentos.
- Clientes: atualizar os unions de propósito, filtros, cores, rótulos,
  acessibilidade e a navegação responsiva para cinco destinos.

## Premissas a validar

- [ ] Pessoas entendem a diferença entre “aporte esperado por mês” e “valor
  total a juntar” apenas pela organização e pelas regras nos cards.
- [ ] O saldo atual, em vez do total histórico de aportes, é a medida de
  progresso que melhor corresponde à expectativa de “dinheiro guardado”.
- [ ] Uma celebração única por verba continua útil quando o usuário aumenta o
  alvo posteriormente.

## Escopo do MVP

1. Criar, editar e arquivar `SAVINGS_TARGET` com alvo total positivo.
2. Registrar aportes e gastos usando as regras existentes de autorização.
3. Exibir saldo, alvo, percentual, valor restante e mensagem única de
   conquista.
4. Criar a página web **Metas**, reorganizar **Verbas** e renomear
   **Histórico** para **Gastos**, mantendo redirecionamento.
5. Cobrir a regra por TDD: escala monetária, persistência entre meses,
   autorização, primeira travessia, edição do alvo e ausência de segunda
   celebração.

## Não faremos neste corte

- Encerramento automático, bloqueio de novos aportes ou gasto automático ao
  atingir o alvo; a decisão de encerrar é do proprietário.
- Uma segunda celebração após queda e novo cruzamento, ou após editar o alvo.
- Meta mensal adicional dentro de `SAVINGS_TARGET`; essa responsabilidade
  permanece em `GOAL`.
- Relatório, projeção de data de conclusão, notificações push, juros ou
  integração bancária específicos para metas de acumulação.
- Uma nova arquitetura ou módulo de backend; a mudança permanece nos módulos
  `envelopes`, `ledger` e nos clientes existentes.

## Dependência de decisão

Antes de implementar, criar e aceitar um ADR que registre a alteração de
persistência e contrato público, a semântica de `SAVINGS_TARGET` e sua relação
com o relatório de `GOAL` do ADR-014. O inventário de dados pessoais e o
OpenAPI devem evoluir na mesma alteração.
