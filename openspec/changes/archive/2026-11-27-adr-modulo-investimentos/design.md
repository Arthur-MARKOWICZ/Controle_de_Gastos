# Design

## Context

Ver `proposal.md` (Why). O backend é um único processo Spring Boot, organizado
em pacotes `br.com.controlegastos.<módulo>` anotados com
`@ApplicationModule` (por exemplo, `envelopes` = "Verbas"). Já existem padrões
reaproveitáveis para o que o ADR-0034 vai prever:

- `RestClient` com retentativa em `identity/infrastructure/GoogleOAuthClient`
  para integração HTTP externa;
- `@Scheduled` com `zone = "America/Sao_Paulo"` em
  `notifications/application/DailyReminderService` e
  `ledger/application/RecurringOccurrenceGenerator`;
- `shared/money` para valores em BRL com `BigDecimal` e duas casas.

Os ADRs seguem `docs/decisions/NNNN-titulo.md` com as seções Status, Data,
Contexto, Decisão, Alternativas consideradas e Consequências. O último é o
0033. Existem dois 0023, mas não vamos renumerá-los aqui.

## Goals / Non-Goals

**Goals:**

- Dar às issues #76 a #80 uma decisão aceita que fixe a fronteira do módulo, a
  autorização, a persistência da cotação e a fórmula do CDB, para que cada uma
  vire uma change própria sem reabrir arquitetura.
- Manter o ADR-0028 intacto e explicitamente ortogonal.

**Non-Goals:**

- Especificar endpoints, payloads ou tabelas. Isso fica nas specs das changes
  #76 a #79.
- Escolher o provedor de cotação.

## Decisions

### D1. Módulo `investments` no monólito, sem dependência do orçamento

Pacote `br.com.controlegastos.investments`
(`@ApplicationModule(displayName = "Investimentos")`). Ele pode depender só de
`shared` e de `identity` (titular autenticado). Nenhum módulo do orçamento
(`envelopes`, `ledger`, `reporting`, `income`, `sharing`) depende dele nem é
dependência dele. A verificação do Spring Modulith garante isso.

- *Alternativa: submódulo de `envelopes`.* Rejeitada porque contradiz o intent
  ("100% isolado das verbas") e o ADR-0028.
- *Alternativa: serviço separado.* Rejeitada pelo ADR-0001 e pelo intent.

### D2. Autorização só do titular

Toda posição pertence a um `owner_id`. Terceiros recebem 404, como já acontece
com recursos de outro titular. Não existe participante, porque a regra
"proprietário e participantes" vale para verbas, não para carteira.

- *Alternativa: compartilhar carteira como verba (ADR-0026).* Rejeitada porque
  é uso pessoal e não há pedido para isso.

### D3. Cotação atrás de uma porta, com a última cotação válida persistida

Porta `QuoteProvider` no módulo e adaptador HTTP em `infrastructure`. A última
cotação válida de cada ticker é gravada no PostgreSQL (preço `NUMERIC(19,2)` e
`quoted_at`). O valor atual da posição usa essa linha. Quando a fonte falha, a
linha anterior é mantida e o cliente vê "cotado em <data/hora>". O custo de
aquisição nunca é alterado pela cotação. O log não registra ticker com valor
nem a resposta bruta do provedor.

A atualização roda por `@Scheduled` em horário de pregão (fuso
`America/Sao_Paulo`). Os horários exatos e o limite de requisições ficam no
ADR filho, junto com o provedor.

- *Alternativa: buscar a cotação a cada GET.* Rejeitada porque acopla a
  disponibilidade da carteira à do provedor e estoura limites gratuitos.
- *Alternativa: cache em memória (Caffeine/Spring Cache).* Rejeitada porque o
  projeto proíbe cache sem ADR e porque o valor some no restart. A linha
  persistida é um dado do domínio, não um cache.
- *Provedor agora × depois:* fica para um ADR filho na issue #77, depois de um
  spike. Os planos gratuitos dos provedores para B3 (brapi, Yahoo, Alpha
  Vantage) mudam com frequência e não bloqueiam a fronteira.

### D4. CDB prefixado, base de 365 dias corridos, valor bruto

```
dias      = min(hoje, vencimento) - data_início   (dias corridos)
fator     = (1 + taxa_aa) ^ (dias / 365)
estimado  = arredonda_2_casas(principal × fator)   HALF_EVEN só no resultado
```

A taxa é guardada como decimal anual (por exemplo, `0.1250` para 12,50% a.a.).
O vencimento é obrigatório no cadastro e o valor deixa de crescer nele. O
resultado é o valor bruto estimado, sem IR/IOF, e os clientes o rotulam como
"estimado".

- *Alternativa: base de 252 dias úteis.* É a convenção do mercado, mas exige
  calendário de feriados. Rejeitada para o dia 1 por decisão do titular. Pode
  ser trocada depois sem mudar o contrato.
- *Alternativa: % do CDI.* Exige a série do BCB e uma segunda integração.
  Fica fora do dia 1.

### D5. Expoente fracionário com `big-math`, sem `double`

`BigDecimal.pow` não aceita expoente fracionário. Adotamos
`ch.obermuhlner:big-math` (`BigDecimalMath.pow` com `MathContext.DECIMAL128`).

- *Alternativa: `Math.pow` em `double` só para o fator.* Rejeitada porque abre
  uma exceção à regra de dinheiro sem `double` (ADR-0006), mesmo que o fator
  em si não seja dinheiro.
- *Alternativa: juros compostos dia a dia.* Não resolve, porque o fator diário
  também é uma raiz fracionária.

### D6. P&L simples

`P&L = valor atual − custo de aquisição`, por posição e no total. Para ações, o
custo é `quantidade × preço de compra`. Para o CDB, é o principal. Nada de
rentabilidade anualizada nem benchmark.

### D7. Canais

- API: dona das regras (issues #76 a #79).
- Web e Android: área própria de investimentos (issue #80), fora das telas de
  verbas.
- MCP e desktop: ficam fora do dia 1. O ADR diz isso explicitamente.

## Risks / Trade-offs

- [O provedor gratuito some ou muda os limites] → A porta `QuoteProvider` e a
  última cotação persistida isolam o impacto. Trocar de provedor é um novo
  adaptador mais um ADR filho.
- [A estimativa com 365 dias corridos diverge do extrato do banco] → O valor é
  rotulado como "bruto estimado" e a troca para 252 úteis não muda o contrato.
- [Nova dependência `big-math`] → Biblioteca pequena e estável, usada só no
  cálculo do CDB. A dependência só entra na issue #78.
- [Dois ADRs com o número 0023 confundem a sequência] → Seguimos com 0034 e
  não renumeramos nada.

## Migration Plan

Não há. Sem código, migração ou deploy. O rollback é reverter o commit de
documentação.
