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

## Rotação da chave AES-256-GCM de MFA

Gere a chave fora do repositório, com exatamente 32 bytes:

```bash
openssl rand -base64 32
```

Configure o resultado como `AUTH_TOTP_ENCRYPTION_KEY`. Este corte suporta
apenas uma versão de chave ativa para decifragem: `key_version` existe na
tabela `totp_credential` como "seam" para uma futura rotação multi-chave, mas
nenhuma lógica de decifragem por múltiplas chaves está implementada.

Consequência prática: **não há hoje uma rotação sem impacto**. Trocar
`AUTH_TOTP_ENCRYPTION_KEY` torna irrecuperáveis os segredos TOTP cifrados com
a chave anterior. Se a chave for perdida ou comprometida:

1. Trate como incidente e siga o procedimento de resposta.
2. Para cada conta com MFA `ENABLED` afetada, desabilite o MFA diretamente no
   banco (`totp_credential.status = 'DISABLED'`, limpando `secret_ciphertext`,
   `secret_nonce` e `key_version`) — não há caminho de recuperação automático.
3. Comunique as pessoas afetadas para que reconfigurem o MFA com um novo
   segredo assim que possível.

Uma estratégia de rotação sem downtime (decifragem multi-chave usando
`key_version`) exige novo ADR antes de ser implementada.

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
- O token de recuperação expira em 15 minutos, só pode ser usado uma vez e seu
  hash é descartado até 24 horas após o término. Suporte não altera hashes
  diretamente: deve orientar a pessoa a iniciar uma nova recuperação.
- A troca confirma o e-mail e revoga todas as sessões; a pessoa deve entrar de
  novo. O Gmail SMTP usa app password exclusiva e sua rotação segue o runbook
  de produção.
- O desafio de login MFA expira em 5 minutos e seu hash é descartado até 24
  horas após expiração ou consumo.
- A configuração de MFA pendente (QR gerado, ainda não confirmado) expira em
  10 minutos; a rotina diária reverte automaticamente para `DISABLED` e limpa
  o segredo cifrado de qualquer configuração pendente abandonada.
- Recovery codes consumidos ou invalidados têm o hash descartado até 24 horas
  depois. Ativar, trocar ou desabilitar o MFA revoga todas as sessões da
  conta; gerar novos recovery codes com o MFA já ativo não revoga sessões.
- A sessão restrita de recuperação (emitida ao consumir um recovery code) é um
  JWT stateless de 10 minutos, sem persistência própria: expira sozinha e não
  precisa de descarte programado.
