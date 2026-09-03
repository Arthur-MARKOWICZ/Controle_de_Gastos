# ADR-018: Recuperação de senha por e-mail com Gmail SMTP

## Status

Aceito

## Data

2026-09-03

## Contexto

O cadastro por e-mail e senha já possui sessões revogáveis, mas uma pessoa sem
a senha não podia recuperar o acesso. A decisão anterior de adiar recuperação e
verificação de e-mail não se aplica mais a este corte. A funcionalidade altera
autenticação, dados pessoais e o contrato público, portanto exige uma decisão
explícita e controles contra enumeração de contas, reuso de token e sequestro de
sessões.

## Decisão

- Oferecer `POST /api/v1/auth/password-reset-requests` e
  `POST /api/v1/auth/password-resets` sem autenticação prévia.
- A solicitação sempre responde de forma genérica e sofre limitação por chave
  HMAC de e-mail e IP. Para uma conta ativa, gera token aleatório de 256 bits,
  válido por 15 minutos e de uso único.
- Persistir apenas SHA-256 do token. Uma nova solicitação invalida qualquer
  token ativo anterior; hashes consumidos ou expirados são removidos até 24
  horas após seu término.
- Enviar o link em `https://<DOMINIO>/redefinir-senha#token=<TOKEN>`. O
  fragmento não é enviado ao servidor durante a navegação; a tela o remove do
  histórico e usa `Referrer-Policy: no-referrer`.
- Aplicar a mesma política Argon2id de senha do cadastro. Ao concluir, marcar
  `email_verified_at`, revogar todas as sessões, apagar o refresh cookie e não
  criar sessão automática.
- Usar Gmail SMTP em `smtp.gmail.com:587`, STARTTLS e remetente igual ao
  usuário SMTP. As credenciais são a conta remetente exclusiva e uma app
  password, armazenadas apenas nos Secrets `GMAIL_SMTP_USERNAME` e
  `GMAIL_SMTP_APP_PASSWORD` do GitHub Environment `production`.

## Alternativas consideradas

### Link com token na query string

Rejeitado porque aumenta a chance de o segredo aparecer em logs, histórico,
referer ou sistemas intermediários.

### Persistir o token original

Rejeitado: uma exposição do banco permitiria a troca imediata de senhas. O
token possui alta entropia, então o hash é suficiente para validá-lo.

### Gmail OAuth ou provedor transacional

São escolhas mais adequadas para maior escala ou gestão centralizada. Foram
adiadas por custo e complexidade operacional no corte atual; o Gmail indica que
app passwords são opção menos recomendada e devem ser rotacionadas.

### Manter sessões após redefinição

Rejeitado porque uma recuperação pode indicar comprometimento da credencial e
deve encerrar acessos previamente emitidos.

## Consequências

- Google recebe e-mail destinatário e o link de recuperação, ambos dados
  pessoais; o inventário de privacidade registra esse operador e a pendência de
  formalizar contrato, região e transferência antes da abertura pública.
- A VPS faz somente saída TCP 587 para o Gmail. As portas SMTP não são abertas
  para entrada e 3000, 5432 e 8080 permanecem vinculadas ao loopback.
- Alterar a senha da conta Gmail revoga suas app passwords. A rotação exige
  gerar uma nova, atualizar o Secret, implantar, testar uma conta sintética e
  revogar a anterior.
- Falha no envio não altera a resposta genérica da solicitação. O operador deve
  observar a falha sem registrar destinatário, token, URL ou credenciais e
  seguir o runbook de produção.

## Fontes

- [Google — Sign in with app passwords](https://support.google.com/mail/answer/185833)
- [OWASP — Forgot Password Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html)
