# Requisitos mínimos de segurança e LGPD

## Identidade e acesso

- Login próprio por e-mail e senha; Argon2id com parâmetros explícitos e
  benchmark por ambiente.
- Access JWT HS256 de 15 minutos, refresh opaco rotativo com 30 dias de
  inatividade e sessão máxima de 365 dias.
- Consultar a sessão persistida em toda requisição protegida para revogação
  imediata; reuso de refresh revoga a sessão.
- Refresh em cookie `HttpOnly`, `Secure` e `SameSite=Strict`; no Android, cópia
  cifrada com chave protegida pelo Keystore e excluída no logout.
- Respostas genéricas e rate limiting com identificadores HMAC, sem persistir
  e-mail ou IP em `auth_attempt`.
- Recuperação de senha por token aleatório de 256 bits, com validade de 15
  minutos, uso único e somente hash persistido; a conclusão revoga todas as
  sessões do usuário e não cria login automático.
- Autorização no backend para cada recurso; ocultar botões não é controle de acesso.
- Privilégios administrativos separados das contas comuns.
- Revogar tokens push ao sair e ao excluir a conta.
- MFA por TOTP opcional e configurável pelo titular, com QR Code e chave
  manual gerados no backend; segredo cifrado com AES-256-GCM e nunca exposto
  em texto claro após a configuração.
- Login com MFA ativo não cria sessão, access token ou refresh cookie antes da
  confirmação do segundo fator; o desafio de login é opaco, de uso único e de
  curta duração.
- Dez recovery codes de uso único, persistidos apenas como hash; consumir um
  recovery code cria somente uma sessão restrita, que só acessa os endpoints
  de configuração de um novo TOTP — nenhum outro recurso, financeiro ou geral.
- Ativar, trocar ou desabilitar o MFA revoga todas as sessões da conta,
  inclusive a atual.

## Dados

- TLS em todo tráfego externo.
- Criptografia dos volumes/backups no provedor ou por ferramenta de backup.
- Segredos somente por variáveis/arquivos protegidos, nunca no Git.
- Segredo JWT somente por variável de runtime protegida, com pelo menos 32
  bytes; ele nunca entra no Git, logs ou imagens. Sua troca invalida os access
  tokens emitidos anteriormente.
- Chave de cifragem TOTP (`AUTH_TOTP_ENCRYPTION_KEY`) somente por variável de
  runtime protegida, com 32 bytes; nunca entra no Git, logs ou imagens. Sua
  perda impede a decifragem dos segredos TOTP ativos (sem rotação sem
  downtime neste corte).
- Valores monetários em `BigDecimal` de BRL, com duas casas e sem arredondamento
  implícito; persistência `NUMERIC(19,2)` e JSON em string decimal.
- Nenhum dado real em fixtures ou screenshots de desenvolvimento.

## Logs e auditoria

- Logs estruturados com identificadores técnicos e `correlation_id`.
- Não registrar token, senha, renda, descrição de gasto ou payload completo.
- Não registrar DTOs de cadastro/login, hashes de senha/refresh/recuperação, cookies,
  cabeçalhos `Authorization` ou e-mails associados a falhas de autenticação.
- Não registrar segredo TOTP (cifrado ou não), código informado, recovery code,
  URI `otpauth://` nem o QR Code gerado.
- Auditoria registra ator, ação, recurso, instante e resultado.
- Acesso à auditoria é restrito e também auditado.

## Direitos do titular

- Canal eletrônico de contato publicado.
- Exportação, correção e exclusão com autenticação reforçada.
- Registro do atendimento e dos prazos aplicáveis.
- Explicação acessível das finalidades e compartilhamentos.

## Operação

- Backups automáticos com teste periódico de restauração.
- Atualizações de segurança para imagens e dependências.
- Procedimento de triagem, contenção e comunicação de incidentes.
- Revisão jurídica antes de cadastro público de terceiros.
