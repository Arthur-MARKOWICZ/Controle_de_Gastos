# Operação segura da autenticação

## Segredos de produção

Gere o segredo HS256 fora do repositório, com no mínimo 32 bytes:

```bash
openssl rand -base64 48
```

Configure o resultado como `AUTH_JWT_SECRET` e use outro segredo aleatório de
ao menos 32 bytes em `AUTH_ATTEMPT_HMAC_SECRET`. Não copie esses valores para
tickets, logs, imagens ou backups sem a mesma proteção dos dados de produção.

## Rotação do JWT

1. Gere um novo `AUTH_JWT_SECRET` aleatório.
2. Implante-o simultaneamente em todas as instâncias da API.
3. Access tokens assinados pelo segredo anterior deixam de ser válidos de
   imediato; clientes usam o refresh token para receber um novo token.

Se o segredo for exposto, a rotação é emergencial: troque-o, revogue as sessões
afetadas e execute o procedimento de incidente. Não reutilize o segredo
comprometido.

## Benchmark de Argon2id

Os parâmetros iniciais são 19 MiB, duas iterações e paralelismo 1. Antes da
abertura pública, meça login válido, inválido e concorrente na mesma classe de
VPS da produção, com aquecimento da JVM. Eleve memória/iterações enquanto o p95
de uma validação permanecer próximo ou abaixo de um segundo e a concorrência
não provocar exaustão. Registre data, hardware, JVM, parâmetros e percentis; não
use senhas nem e-mails reais na medição.

## Retenção e resposta

- Tentativas HMAC expiram em 24 horas.
- Sessões encerradas são descartadas 30 dias após revogação ou expiração.
- Uma rotina diária aplica o descarte; o rate limiter também remove tentativas
  vencidas oportunisticamente.
- Reuso de refresh revoga a sessão inteira e deve produzir métrica sem token,
  cookie, e-mail ou IP bruto.
- Recuperação de senha e verificação de e-mail não existem neste corte; suporte
  não deve contornar isso alterando hashes diretamente.
