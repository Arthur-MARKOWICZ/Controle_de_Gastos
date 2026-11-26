# ADR-034: Módulo de investimentos (carteira de ações B3 e CDB prefixado)

## Status

Aceito

## Data

2026-11-26

## Contexto

O titular quer acompanhar o patrimônio no mesmo app, mas sem misturá-lo ao
orçamento. A intenção confirmada
([`docs/intent/rastreamento-investimentos.md`](../intent/rastreamento-investimentos.md))
pede posições cadastradas à mão, ações atualizadas por cotação automática, CDB
crescendo pela taxa contratada e uma visão com total da carteira, valor por
ativo e P&L simples desde a compra. O dia 1 cobre ações da B3 e CDB. Fundos
ficam para depois.

O [ADR-0028](0028-classificacao-de-alocacao-de-patrimonio.md) classifica a
**verba** de aporte no orçamento, para que o resumo separe "consumi" de
"apliquei". Naquele corte, ele rejeitou uma área de investimentos e deixou
registrado que cotação, rentabilidade e carteira por ativo exigiriam um novo
ADR. Este é esse ADR.

Restrições que valem aqui: o monólito modular
([ADR-0001](0001-monorepo-e-monolito-modular.md)), valores monetários em
`BigDecimal` com duas casas
([ADR-0006](0006-valores-monetarios-decimais-e-renda-mensal.md)), privacidade
desde a concepção ([ADR-0003](0003-privacidade-desde-a-concepcao.md)) e a
proibição de cache sem ADR.

## Decisão

### Módulo `investments`, isolado do orçamento

- Criamos o pacote `br.com.controlegastos.investments`, com
  `@ApplicationModule(displayName = "Investimentos")`, dentro do mesmo
  processo Spring Boot.
- O módulo pode depender apenas de `shared` (dinheiro e utilitários) e de
  `identity` (titular autenticado).
- Nenhum módulo do orçamento (`income`, `envelopes`, `ledger`, `sharing`,
  `reporting`) depende de `investments`, e `investments` também não depende
  deles. A verificação do Spring Modulith garante essa fronteira.
- Registrar uma posição nunca gera lançamento em verba, e lançamentos em
  verbas nunca alteram posições.

### Acesso só do titular

- Toda posição pertence a um `owner_id`.
- Não existem participantes nem compartilhamento. Um recurso de outro titular
  responde 404, seguindo o padrão do projeto.

### Ações da B3: posição manual e cotação externa

- A posição guarda ticker, quantidade, preço unitário de compra e data da
  compra. O custo de aquisição é `quantidade × preço de compra`.
- A cotação chega por uma porta `QuoteProvider` do módulo. O adaptador HTTP
  fica em `investments/infrastructure` e segue o padrão `RestClient` já usado
  em `identity`.
- A **última cotação válida** de cada ticker fica no PostgreSQL (preço
  `NUMERIC(19,2)` e `quoted_at`). É um dado do domínio que sobrevive a
  restart, e não um cache. O valor atual da posição é
  `quantidade × última cotação`.
- A atualização roda por `@Scheduled` no fuso `America/Sao_Paulo`, em horário
  de pregão.
- Se a fonte falhar, a última cotação válida continua valendo e os clientes
  exibem "cotado em <data/hora>". Uma falha nunca altera o custo de aquisição.
  Os logs não registram tickers com valores nem a resposta bruta do provedor.
- O provedor, a frequência exata e o limite de requisições serão decididos em
  um ADR filho, na issue #77, depois de um spike.

### CDB prefixado: valor bruto estimado

- O CDB guarda o principal, a taxa anual prefixada (decimal: `0.1250` equivale
  a 12,50% a.a.), a data de início e a data de vencimento (obrigatória).
- O valor estimado é calculado assim:

  ```
  dias     = dias corridos entre data_início e min(hoje, vencimento)
  fator    = (1 + taxa_aa) ^ (dias / 365)
  estimado = principal × fator, arredondado a 2 casas (HALF_EVEN) só no resultado
  ```

- No vencimento, o valor deixa de crescer.
- O valor é **bruto**, sem IR nem IOF, e os clientes o rotulam como
  "estimado".
- `BigDecimal.pow` não aceita expoente fracionário, por isso o cálculo usa a
  biblioteca `ch.obermuhlner:big-math` (`BigDecimalMath.pow` com
  `MathContext.DECIMAL128`). Nenhuma etapa passa por `double`.

### Resumo da carteira e P&L simples

- O resumo traz o total da carteira (ações cotadas e CDBs estimados) e o
  detalhe por ativo e por posição.
- `P&L = valor atual − custo de aquisição`, por posição e no total. No CDB, o
  custo é o principal.
- Não há rentabilidade anualizada, benchmark nem série histórica.

### Canais

- **API**: dona de todas as regras do módulo.
- **Web e Android**: área própria de investimentos, separada das telas de
  verbas e do resumo de gastos.
- **MCP e desktop**: ficam fora do dia 1.

### Relação com o ADR-0028

As duas decisões são complementares e ortogonais:

| | ADR-0028 | ADR-0034 |
|---|---|---|
| Pergunta respondida | Quanto do meu orçamento eu apliquei? | Quanto vale o que eu tenho? |
| Unidade | Verba (`wealthAllocation`) | Posição por ativo |
| Módulo | `envelopes` e `reporting` | `investments` |
| Dinheiro | Saídas reais da conta | Custo, cotação e estimativa |

Um aporte pode aparecer nos dois lugares, como saída numa verba marcada e como
posição na carteira, mas nenhum dos dois lados lê ou escreve o outro.

## Alternativas consideradas

### Investimentos como submódulo ou propósito de verba

Rejeitada. A intenção exige isolamento total das verbas, e o ADR-0028 já
rejeitou o propósito `INVESTMENT` porque ele mudaria a semântica do saldo.

### Serviço separado para investimentos

Rejeitada pelo ADR-0001 e pela intenção. Ainda não há métricas que justifiquem
o custo operacional.

### Buscar a cotação a cada consulta

Rejeitada. A disponibilidade da carteira passaria a depender do provedor e os
limites gratuitos seriam consumidos rápido.

### Cache em memória para cotações (Caffeine ou Spring Cache)

Rejeitada. Cache exige ADR próprio, e a cotação se perderia a cada restart. A
última cotação persistida resolve o mesmo problema como dado do domínio.

### CDB pós-fixado (% do CDI) no dia 1

Rejeitada para o dia 1. Exigiria uma segunda integração externa (a série do
CDI no BCB) e um cálculo diário composto. O pós-fixado pode entrar depois.

### Base de 252 dias úteis

É a convenção de mercado, mas exige um calendário de feriados nacionais.
Rejeitada para o dia 1 por decisão do titular. A estimativa com 365 dias
corridos fica um pouco distante do extrato do banco. A troca para 252 dias
úteis não muda o contrato da API.

### Calcular o fator do CDB com `Math.pow` em `double`

Rejeitada. Abriria uma exceção à regra de não usar `double` em valores
monetários. A precisão de `big-math` evita essa discussão.

## Consequências

- A issue #77 abre um ADR filho para escolher o provedor de cotação, definir a
  frequência e o limite de requisições e decidir como guardar uma eventual
  credencial do provedor (variável de ambiente e nunca em log).
- A issue #78 adiciona a dependência `ch.obermuhlner:big-math` ao backend.
- As issues que criarem tabelas (#76, #77 e #78) atualizam o
  [inventário de dados](../privacy/inventario-de-dados.md) com finalidade,
  retenção (até a exclusão da conta) e inclusão na exportação do titular.
- A exclusão da conta precisa apagar as posições do titular, seguindo o
  [ADR-0023](0023-exclusao-do-titular-e-fks-de-autoria.md).
- O módulo nasce com testes de fronteira do Spring Modulith e com TDD das
  regras de custo, valor atual, estimativa do CDB e P&L.
- MCP e desktop não ganham investimentos no dia 1. Incluí-los depois exige uma
  change própria, mas não exige um novo ADR.
- Fundos, pós-fixado, IR/IOF, import de corretora e gráficos continuam fora do
  escopo.
