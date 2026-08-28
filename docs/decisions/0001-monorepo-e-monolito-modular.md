# ADR-001: Monorepo com backend em monólito modular

## Status

Aceito

## Data

2026-08-27

## Contexto

O produto possui uma API, uma aplicação web e um aplicativo Kotlin Multiplatform. As regras de renda, verbas, saldos e compartilhamento exigem consistência transacional. O deploy ocorrerá em uma VPS KVM2 compartilhada com outros projetos e será mantido inicialmente por uma pessoa.

## Decisão

Manter todas as aplicações no mesmo repositório. Implementar a API como um único processo Spring Boot, organizado por capacidades de negócio e validado pelo Spring Modulith.

Os módulos se comunicam por APIs explícitas e eventos de aplicação. Eventos externos, brokers e separação física somente serão considerados quando métricas demonstrarem necessidade.

## Alternativas consideradas

### Microserviços Spring

- Vantagem: implantação e escala independentes.
- Desvantagens: maior consumo, observabilidade distribuída e transações mais difíceis.
- Rejeitado porque o produto e a equipe ainda não justificam o custo operacional.

### Backend TypeScript

- Vantagem: linguagem compartilhada com a web.
- Desvantagem: aproveita menos a experiência existente em Java/Spring e não elimina a necessidade de Kotlin no mobile.
- Rejeitado em favor da stack já dominada.

## Consequências

- Regras transacionais permanecem locais a um banco e processo.
- Limites entre módulos devem possuir testes de arquitetura.
- Uma futura extração de serviço exige métricas, ADR e contrato explícito.
- O repositório terá ferramentas Gradle e pnpm, com comandos unificados na raiz.
