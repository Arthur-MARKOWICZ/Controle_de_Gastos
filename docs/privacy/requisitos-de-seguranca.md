# Requisitos mínimos de segurança e LGPD

## Identidade e acesso

- Login próprio por e-mail e senha; Argon2id com parâmetros explícitos e
  benchmark por ambiente.
- Access JWT RS256 de 15 minutos, refresh opaco rotativo com 30 dias de
  inatividade e sessão máxima de 365 dias.
- Consultar a sessão persistida em toda requisição protegida para revogação
  imediata; reuso de refresh revoga a sessão.
- Refresh em cookie `HttpOnly`, `Secure` e `SameSite=Strict`; no Android, cópia
  cifrada com chave protegida pelo Keystore e excluída no logout.
- Respostas genéricas e rate limiting com identificadores HMAC, sem persistir
  e-mail ou IP em `auth_attempt`.
- Autorização no backend para cada recurso; ocultar botões não é controle de acesso.
- Privilégios administrativos separados das contas comuns.
- Revogar tokens push ao sair e ao excluir a conta.

## Dados

- TLS em todo tráfego externo.
- Criptografia dos volumes/backups no provedor ou por ferramenta de backup.
- Segredos somente por variáveis/arquivos protegidos, nunca no Git.
- Chave JWT privada somente por arquivo/secret mount com permissão mínima; a
  chave anterior é removida após a janela dos access tokens emitidos.
- Valores monetários em `BigDecimal` de BRL, com duas casas e sem arredondamento
  implícito; persistência `NUMERIC(19,2)` e JSON em string decimal.
- Nenhum dado real em fixtures ou screenshots de desenvolvimento.

## Logs e auditoria

- Logs estruturados com identificadores técnicos e `correlation_id`.
- Não registrar token, senha, renda, descrição de gasto ou payload completo.
- Não registrar DTOs de cadastro/login, hashes de senha/refresh, cookies,
  cabeçalhos `Authorization` ou e-mails associados a falhas de autenticação.
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
