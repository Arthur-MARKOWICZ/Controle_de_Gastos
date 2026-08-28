# ADR-002: Identidade por OpenID Connect com Keycloak

## Status

Superado pelo [ADR-005](0005-autenticacao-propria-por-email-e-senha.md)

## Data

2026-08-27

## Contexto

Web e mobile precisam de cadastro, autenticação, recuperação de acesso e sessões seguras. Implementar um servidor de identidade próprio aumentaria o risco de segurança e desviaria esforço do domínio financeiro.

## Decisão

Usar Keycloak 26.7 como provedor OpenID Connect. A API Spring atua como Resource Server. Web e mobile usam Authorization Code com PKCE; clientes públicos não armazenam segredo.

O perfil financeiro local referencia o `subject` imutável emitido pelo provedor. Dados de domínio não dependem de e-mail como identificador.

## Alternativas consideradas

### Autenticação implementada no Spring

- Vantagem: um processo a menos.
- Desvantagens: responsabilidade por credenciais, recuperação, rotação de tokens e múltiplos clientes.
- Rejeitada porque segurança de identidade não é o diferencial do produto.

### Provedor SaaS

- Vantagem: menor operação local.
- Desvantagens: custo, dependência externa e avaliação adicional de operadores/transferência de dados.
- Adiado até existir necessidade comprovada.

## Consequências

- Keycloak consome memória relevante e deve receber limite explícito no contêiner.
- TLS é obrigatório em produção.
- Cada projeto compartilhando a instância deve usar realm e clientes isolados.
- Eventos e dados administrativos do Keycloak entram no plano de backup e retenção.

## Histórico

Esta decisão deixou de orientar o runtime em 2026-08-27. O documento é mantido
para explicar por que Keycloak foi considerado e quais custos deverão ser
reavaliados caso uma migração futura seja proposta.
