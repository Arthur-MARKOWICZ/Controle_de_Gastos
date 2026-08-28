# Operação segura da autenticação

## Segredos de produção

Gere o par RSA fora do repositório e mantenha a chave privada legível somente
pelo usuário do processo da API:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out jwt-private.pem
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem
chmod 600 jwt-private.pem
chmod 644 jwt-public.pem
chown 10001:10001 jwt-private.pem jwt-public.pem
```

Configure `AUTH_JWT_PRIVATE_KEY_PATH_HOST`, `AUTH_JWT_PUBLIC_KEY_PATH_HOST`, um
`AUTH_JWT_KEY_ID` único e `AUTH_ATTEMPT_HMAC_SECRET` com ao menos 32 bytes
aleatórios. O overlay `compose.production.yaml` monta as chaves somente para
leitura. Não copie esses valores para tickets, logs, imagens ou backups sem a
mesma proteção dos dados de produção.

## Rotação do JWT

1. Gere um novo par e um `kid` nunca usado.
2. Implante o novo par como chave atual e monte a chave pública anterior,
   configurando `AUTH_JWT_PREVIOUS_PUBLIC_KEY_PATH` e
   `AUTH_JWT_PREVIOUS_KEY_ID`.
3. Aguarde no mínimo a vida do access token (15 minutos) mais a tolerância de
   relógio definida na operação.
4. Remova a chave pública anterior e suas variáveis. Descarte a chave privada
   antiga conforme o procedimento de segredos.

Se uma chave privada for exposta, a rotação é emergencial: troque o par, revogue
as sessões afetadas e execute o procedimento de incidente. Não mantenha a chave
comprometida como anterior.

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
