# Fundação técnica do controle de gastos e verbas

## Problem Statement

Como construir um sistema pessoal de verbas reservadas, acessível pela web e pelo celular, que incentive o registro diário de gastos e possa crescer para múltiplos usuários sem tornar uma VPS compartilhada cara ou difícil de operar?

## Resultado confirmado

- Registrar gastos manualmente e com pouco esforço.
- Distribuir uma renda mensal fixa e alterável entre verbas cuja soma-base nunca exceda a renda.
- Acumular para o mês seguinte o saldo não utilizado.
- Manter dinheiro não alocado separado das verbas.
- Permitir saldo negativo com alerta, sem impedir o registro de um gasto real.
- Avaliar verbas como limite de gasto, meta de aporte ou compromisso fixo.
- Permitir verbas compartilhadas: participantes visualizam e registram gastos; somente o criador abastece, convida, remove e encerra.
- Manter histórico das movimentações e autoria de seu lançamento.

## Experiência por canal

- **Mobile:** lançamento rápido, saldos, alertas, notificações e histórico.
- **Web:** configuração, histórico completo e relatórios.
- **Backend:** fonte única das regras, autorizações e notificações.

## Direção recomendada e confirmada

Monorepo com aplicações separadas e um backend em monólito modular:

- Java 25 LTS, Spring Boot 4.1 e Spring Modulith 2.1 no backend.
- TypeScript, React 19 e Next.js 16 Active LTS na web.
- Kotlin 2.4 e Compose Multiplatform 1.12 no aplicativo Android/iOS.
- PostgreSQL 18 como banco relacional.
- Valores monetários em `BigDecimal` com duas casas, persistidos como
  `NUMERIC(19,2)` e expostos como strings decimais no JSON; tipos primitivos
  numéricos não representam dinheiro.
- Autenticação própria no módulo `identity`, por e-mail e senha, com access JWT
  curto e refresh opaco rotativo.
- REST e OpenAPI como contrato entre clientes e API.
- Flyway para migrações.
- Docker Compose para desenvolvimento e deploy inicial.
- Caddy como proxy reverso e terminação TLS na VPS.

O backend é um único processo implantável, separado internamente nos módulos `identity`, `income`, `envelopes`, `ledger`, `sharing`, `notifications`, `audit`, `privacy` e `reporting`.

## Por que esta direção

Ela aproveita a experiência existente em Spring, React e Kotlin, mantém os clientes adequados a cada plataforma e concentra as regras financeiras em uma API transacional. Spring Modulith fornece limites verificáveis entre módulos sem o custo de microserviços. Compose Multiplatform está estável para Android e iOS, mas o alvo iOS não poderá ser compilado neste ambiente por ausência de macOS/Xcode.

## Premissas a validar

- [ ] O lançamento manual é rápido o suficiente para virar hábito diário.
- [ ] O modelo de dinheiro reservado e acumulável é compreendido sem explicação extensa.
- [ ] O controle de uma verba compartilhada pelo criador atende ao uso real.
- [ ] A VPS mantém folga de memória e CPU com API, web e PostgreSQL junto dos demais projetos.
- [ ] Haverá acesso futuro a macOS/Xcode antes de declarar suporte de produção ao iOS.

## Escopo do primeiro corte vertical

1. Autenticar um usuário.
2. Configurar renda mensal.
3. Criar verbas e valores-base.
4. Validar que a soma-base não ultrapassa a renda.
5. Distribuir a renda e calcular o valor não alocado.
6. Registrar um gasto, inclusive acima do saldo.
7. Consultar saldo e alerta de verba negativa.

## Não faremos agora

- Microserviços, Kubernetes, Kafka ou Redis.
- Integração automática com bancos.
- Controle de contas bancárias, cartões, faturas ou dinheiro físico.
- Sincronização offline completa.
- Mais de uma moeda; o MVP usa BRL.
- Build ou publicação iOS sem macOS/Xcode.
- Plataforma pesada de observabilidade.
- Recuperação e verificação de e-mail neste corte.
- Keycloak no runtime atual; uma adoção futura exige novo ADR.

## Privacidade e LGPD

Privacidade é requisito transversal: minimização, autorização por recurso, trilha de auditoria, retenção explícita, exportação/correção/exclusão, canal do titular, backups protegidos e procedimento de incidentes. A implementação técnica não substitui revisão jurídica antes de uma abertura pública.

## Questões abertas que não bloqueiam o esqueleto

- Nome público do produto.
- Domínio e provedor de e-mail transacional.
- Projeto Firebase/APNs para notificações remotas.
- Acesso futuro a macOS e conta Apple Developer.
