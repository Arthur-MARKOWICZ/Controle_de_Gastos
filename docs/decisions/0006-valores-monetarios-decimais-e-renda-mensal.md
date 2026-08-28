# ADR-006: Valores monetários decimais e renda mensal efetiva

## Status

Aceito

## Data

2026-08-28

## Contexto

O primeiro modelo de verbas representava BRL como centavos em `long`. A direção
confirmada passou a proibir tipos primitivos para dinheiro e exige uma renda
mensal única, alterável, cujo valor de cada mês passado permaneça consultável.
A API também será consumida por TypeScript e Kotlin, portanto números JSON não
podem depender da precisão numérica de cada cliente.

## Decisão

- Representar todo dinheiro no domínio por um tipo `Money` imutável, composto
  por `BigDecimal` normalizado para escala 2 e moeda fixa `BRL`.
- Aceitar somente construções por `String` ou `BigDecimal`. Valores com mais de
  duas casas são inválidos; o domínio não arredonda silenciosamente.
- Persistir valores como `NUMERIC(19,2)`. `long`, `int`, `double` e `float` não
  representam dinheiro, embora tipos integrais continuem válidos para versão,
  contagem e paginação.
- Expor dinheiro no JSON como string decimal com duas casas, acompanhada de
  `currency: BRL`.
- Persistir uma renda por usuário e mês de vigência. A consulta de um mês usa a
  alteração mais recente cuja vigência seja anterior ou igual ao mês pedido.
  Alterar a renda grava o mês corrente em `America/Sao_Paulo`, afeta esse mês e
  os seguintes e não reescreve meses anteriores.
- Manter histórico append-only de alterações reais. Repetir o mesmo valor
  normalizado é idempotente e não cria uma revisão.
- Derivar o proprietário e o ator exclusivamente da identidade autenticada.
  Uma redução será recusada quando restrições de verbas-base exigirem renda
  superior.

## Alternativas consideradas

### Centavos em `long`

Tem aritmética exata, mas foi rejeitado pela regra explícita do produto e
facilita misturar identificadores, contagens e dinheiro na mesma primitiva.

### `double` ou `float`

Rejeitados porque ponto flutuante binário não representa de forma exata todos
os valores decimais financeiros.

### Tipo `money` do PostgreSQL

Rejeitado por depender de configuração regional e por acoplar formatação à
persistência. `NUMERIC` preserva a representação decimal exata definida pelo
domínio.

### Sobrescrever uma única renda atual

Rejeitado porque impediria reconstruir a renda aplicável a meses passados e
apagaria a trilha de alteração.

## Consequências

- Verbas e renda compartilham um único núcleo monetário.
- Entradas como `10.999` retornam erro em vez de serem arredondadas.
- Migrações e DTOs devem declarar explicitamente precisão, escala e formato.
- O histórico contém dado financeiro pessoal e segue os mesmos controles de
  autorização, logs, retenção, exportação e exclusão da conta.
- O módulo de verbas poderá implementar o contrato de restrição da renda sem
  criar uma dependência do módulo `income` para a persistência de verbas.

## Fontes

- [Java `BigDecimal`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html)
- [Java `Currency`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Currency.html)
- [Jakarta Persistence `Column`](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/column)
- [Hibernate ORM — tipos básicos](https://docs.jboss.org/hibernate/orm/7.0/userguide/html_single/Hibernate_User_Guide.html)
- [PostgreSQL — tipos numéricos](https://www.postgresql.org/docs/current/datatype-numeric.html)
