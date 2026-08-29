# Plano — histórico de gastos e relatórios

## Objetivo

Disponibilizar, na web, uma única página de histórico que permita consultar os
gastos em um intervalo de datas, entender sua distribuição e manter os
lançamentos autorizados. A API continua sendo a fonte de verdade dos cálculos,
das autorizações e dos dados; o frontend somente apresenta os dados e desenha
os gráficos.

## Decisões confirmadas

- O filtro é sempre por intervalo de datas. A renda considerada é a renda
  mensal integral de cada mês que tenha pelo menos um dia dentro do intervalo.
- "Categoria" neste histórico é o tipo da verba: `LIMIT`, `GOAL` ou `FIXED`.
  Não será criada uma entidade de categoria separada nesta entrega.
- O histórico inclui as verbas que o usuário pode visualizar, inclusive as
  compartilhadas das quais participa.
- A lista é paginada por lançamentos individuais, com dez registros por página
  como padrão, e é agrupada visualmente pelo tipo da verba. A paginação não é
  aplicada aos grupos.
- Apenas o proprietário da verba pode editar ou excluir um gasto. Participantes
  podem visualizar os dados das verbas compartilhadas, mas não executam essas
  ações.
- A edição pode mudar valor, verba e descrição; a data de ocorrência não pode
  ser alterada.
- A exclusão é lógica: o lançamento deixa de compor os cálculos e a consulta
  padrão, mas permanece persistido. O proprietário pode optar por incluir
  lançamentos excluídos na lista.
- A tela não precisa exibir valores ou a verba anteriores depois de uma edição.
  Em consequência, relatórios de datas passadas são recalculados de acordo com
  o estado atual do lançamento.

## Escopo da página

A página terá os seguintes elementos, todos atualizados ao mudar o intervalo:

1. Gráfico de barras com o total de gastos por mês.
2. Gráfico de pizza com o total de gastos por tipo de verba.
3. Lista paginada de lançamentos, organizada visualmente por tipo de verba.
4. Quatro cards pequenos:
   - renda do período;
   - gastos do período;
   - saldo líquido do período (`renda - gastos`);
   - saldo acumulado das verbas na data final do intervalo.
5. Filtros de data e, para o proprietário, a opção de incluir lançamentos
   excluídos.
6. Ações de editar e excluir nos lançamentos em que o usuário autenticado seja
   proprietário da verba.

Verbas arquivadas que tenham movimentações relevantes até a data final do
filtro devem continuar sendo consideradas no saldo acumulado e no histórico.

## Regras de cálculo

| Métrica | Regra |
| --- | --- |
| Renda do período | Soma a renda mensal efetiva de cada mês que intersecta o intervalo, mesmo quando somente parte do mês foi filtrada. |
| Gastos do período | Soma os gastos ocorridos no intervalo, em verbas que o usuário pode visualizar, excetuando lançamentos excluídos. |
| Saldo líquido | Renda do período menos gastos do período. |
| Saldo acumulado das verbas | Fotografia do saldo disponível das verbas na data final do filtro, incluindo carry, aportes, gastos e verbas arquivadas relevantes. |

Valores monetários usam `Money`/`BigDecimal`, escala fixa de duas casas,
`NUMERIC(19,2)` na persistência e strings decimais em JSON. Não haverá
arredondamento implícito.

## Backend

O backend organiza a consulta no módulo `reporting` e mantém as mutações no
`ledger`; não deve introduzir serviço separado, broker ou cache distribuído.

### Consultas

A API precisa disponibilizar dados autorizados para:

- lançamentos paginados, filtrados por intervalo e agrupáveis pelo tipo da
  verba;
- totais mensais de gastos para o gráfico de barras;
- totais de gastos por tipo de verba para o gráfico de pizza;
- resumo com renda, gastos, saldo líquido e saldo acumulado.

Os agregados devem ser calculados no backend sobre todo o intervalo, nunca a
partir apenas da página atual da lista. O contrato deve aceitar paginação com
`size` padrão igual a 10 e expor os metadados necessários para navegação.

### Mutações

- A edição deve rejeitar tentativa de alterar `occurredAt` e validar valor,
  verba de destino e descrição conforme as regras do domínio.
- A exclusão deve ser lógica e fazer o lançamento deixar de participar das
  consultas e agregações padrão.
- Alterar ou excluir um lançamento antigo deve recalcular os saldos afetados a
  partir daquele lançamento, inclusive nos meses seguintes.

Em toda consulta e mutação, o identificador do usuário vem da sessão
autenticada. Nunca deve ser recebido do cliente nem servir como único filtro:
a verba precisa estar autorizada para o usuário como proprietário ou
participante. A autorização de editar e excluir exige, adicionalmente, que ele
seja o proprietário.

## Frontend

- Criar uma única página de histórico, em vez de páginas separadas para os
  gráficos e para o saldo.
- Manter o intervalo de datas no estado da URL para que a consulta seja
  compartilhável e recarregável.
- Renderizar os gráficos a partir dos agregados retornados pela API; o frontend
  não recalcula renda, saldo ou totais de todas as páginas.
- Atualizar cards, gráficos e lista após editar ou excluir, tratando estados de
  carregamento, vazio e erro de forma acessível.
- Exibir ações de edição/exclusão apenas como conveniência visual. A permissão
  efetiva permanece obrigatoriamente no backend.

## Privacidade e segurança

- Renda, valores, descrições e histórico de gastos são dados financeiros
  pessoais. Consultas devem aplicar autorização por recurso no backend.
- Logs não podem registrar valor monetário, descrição, token, cabeçalho de
  autorização, e-mail ou payload completo.
- A API deve retornar apenas os campos necessários para a tela e usar dados
  sintéticos em testes e capturas de tela.
- A inclusão de lançamentos excluídos é limitada ao proprietário da verba.

## Limitação aceita de auditoria

A auditoria detalhada foi adiada por decisão de escopo, formalizada no
[ADR-009](../decisions/0009-adiamento-da-auditoria-detalhada-de-gastos.md).
Esta entrega preserva autoria e instante de criação, além da exclusão lógica,
mas não retém valores, descrição ou verba anteriores a uma edição. Relatórios
de períodos passados podem, portanto, mudar após edições ou exclusões.

Também permanece necessário definir como os cards de renda e saldo líquido
devem se comportar para um participante que visualiza gastos de uma verba cujo
proprietário é outra pessoa: a renda exibida é sempre a renda própria do
participante, mas a inclusão desses gastos no saldo líquido precisa ser uma
regra explícita do produto.

## Fora do escopo

- Nova taxonomia de categorias além dos tipos de verba existentes.
- Alteração da data de ocorrência de um lançamento.
- Exclusão física de lançamentos.
- Histórico detalhado de versões de edição nesta entrega.
- Novas dependências de cache, mensageria, microserviços ou biblioteca de
  gráficos sem necessidade comprovada.

## Critérios de aceite

- [ ] O histórico respeita o intervalo de datas e a autorização de proprietário
      ou participante.
- [ ] Os quatro cards seguem as regras de cálculo deste documento e usam BRL
      exato.
- [ ] Os dois gráficos refletem todo o intervalo, não apenas a página atual.
- [ ] A lista usa dez registros por página por padrão, agrupa visualmente por
      tipo e permite navegar pelas páginas.
- [ ] Um participante não consegue editar, excluir ou incluir excluídos de uma
      verba compartilhada.
- [ ] Editar não permite mudar a data; excluir remove o lançamento das
      agregações padrão e preserva-o logicamente.
- [ ] Testes cobrem isolamento entre usuários, permissões de compartilhamento,
      intervalo que cruza meses, renda integral por mês intersectado,
      lançamentos excluídos e recálculo após edição/exclusão.
