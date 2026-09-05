# ADR-019: MFA por TOTP

## Status

Proposto

## Data

2026-09-05

## Contexto

O login por e-mail e senha não tem segundo fator. `docs/plans/plano_mfa.md`
especifica MFA opcional por TOTP, compatível com aplicativos autenticadores
(incluindo Microsoft Authenticator), com o requisito central de que nenhuma
sessão, access token ou refresh cookie seja emitido antes da confirmação do
segundo fator. A funcionalidade altera autenticação, persistência e o
contrato público, exigindo decisão explícita antes da implementação.

## Decisão

- Usar `dev.samstevens.totp:totp:1.7.1` para geração/verificação de código, com
  `com.google.zxing:core:3.5.4` e `com.google.zxing:javase:3.5.4` (dependência
  da própria biblioteca) para renderizar o QR Code em PNG no backend. Nenhum
  cliente (web ou mobile) ganha dependência de geração de QR: eles só exibem a
  imagem retornada pela API.
- TOTP com 6 dígitos, período de 30 segundos, algoritmo SHA-1 (padrão RFC 6238,
  compatibilidade com os autenticadores mais usados) e tolerância de uma janela
  anterior e posterior (`allowedTimePeriodDiscrepancy = 1`).
- O identificador no `otpauth://` é o UUID interno do usuário, nunca o e-mail,
  com issuer "Controle de Gastos" — mantém o rótulo livre de dado pessoal.
- `TotpCredential` é uma entidade própria (tabela `totp_credential`, chave
  primária `user_id`), não um `@Embeddable` em `UserAccount` como
  `PasswordCredential` (ADR-008). A diferença: `PasswordCredential` é
  obrigatória e sempre presente; `TotpCredential` tem máquina de estados
  (`DISABLED`, `PENDING`, `ENABLED`) e um segredo que expira antes de
  confirmado, aproximando-se mais do padrão de `PasswordResetToken`.
- O segredo TOTP é cifrado com AES-256-GCM antes de persistir: nonce aleatório
  de 12 bytes por segredo, tag de 128 bits, coluna `key_version` para permitir
  troca futura da chave de cifragem. A chave de runtime vem de
  `AUTH_TOTP_ENCRYPTION_KEY` (32 bytes, `openssl rand -base64 32`), com o mesmo
  tratamento de segredo de produção que `AUTH_JWT_SECRET`. Este corte não
  implementa rotação de chave: `key_version` existe como "seam" para uma
  decisão futura, mas só há uma chave ativa para decifragem por vez.
- "Substituir" um TOTP já ativo em uma sessão normal é modelado como
  desabilitar e reiniciar a configuração, não uma transição direta
  `ENABLED → PENDING`: `startEnrollment` lança exceção se o estado já for
  `ENABLED`. A única exceção é a sessão restrita de recuperação (ver abaixo):
  como ela existe justamente para substituir um TOTP cujo autenticador foi
  perdido, `/mfa/enroll` chamado com esse token desabilita e reinicia a
  configuração automaticamente em vez de exigir a etapa normal de
  desabilitar antes.
- Recovery codes seguem o mesmo padrão de `PasswordResetToken`: nunca
  persistidos em texto claro, apenas o hash SHA-256, uso único.
- O desafio de login MFA (`mfa_login_challenge`) é o mesmo artefato consumido
  tanto pela verificação de código TOTP quanto pelo consumo de um recovery
  code — a escolha entre os dois ocorre depois da senha já validada.
- O login responde `200 OK` com `{ mfaRequired: true, challengeId, expiresIn }`
  e sem `Set-Cookie` quando o segundo fator é necessário, em vez de `401`: a
  senha estava correta, e um `401` obrigaria todo cliente a inspecionar o corpo
  para distinguir falha real de segundo fator pendente.
- A sessão restrita de recuperação (habilitada apenas para cadastrar e
  confirmar um novo TOTP) é um JWT stateless com claim `mfa_scope =
  RECOVERY_SETUP`, sem claim `sid`, validade de 10 minutos, nunca persistido.
  `SessionValidationFilter` passa a permitir, para esse token, somente
  `POST /api/v1/mfa/enroll` e `POST /api/v1/mfa/enroll/confirm`; qualquer outro
  endpoint responde `403`. Reaproveita o mesmo segredo HS256 já usado para
  access tokens — não há uma segunda chave de assinatura.
- Tempos de vida: desafio de login MFA 5 minutos; configuração pendente 10
  minutos (exigido pelo plano); sessão restrita de recuperação 10 minutos.
- Ativar, trocar ou desabilitar MFA revoga todas as sessões do usuário, como já
  ocorre em reset de senha (ADR-018). Gerar novos recovery codes isoladamente,
  com MFA já ativo, não revoga sessões.
- Rate limiting reaproveita `AuthAttemptService` (chave HMAC por escopo), com
  novos escopos para verificação de TOTP, recuperação e configuração — sem
  nova biblioteca ou infraestrutura de limitação.
- Erros de senha, TOTP, desafio inválido ou expirado mantêm resposta genérica
  e idêntica entre si, sem distinguir a causa para quem chama a API.

### Contrato de endpoints

| Endpoint | Autenticação | Sucesso |
|---|---|---|
| `POST /auth/login` | nenhuma | `200 Token` ou `200 { mfaRequired, challengeId, expiresIn }` |
| `POST /auth/mfa/verify` | nenhuma (challengeId é a credencial) | `200 Token` + `Set-Cookie` |
| `POST /auth/mfa/recovery` | nenhuma | `200 { restrictedToken, tokenType, expiresIn }`, sem cookie |
| `POST /mfa/enroll` | bearer normal | `200 { otpauthUri, qrImageDataUri, manualEntryKey, pendingExpiresAt }`, `Cache-Control: no-store` |
| `POST /mfa/enroll/confirm` | bearer normal ou restrito | `200 { recoveryCodes: string[10] }` |
| `POST /mfa/disable` | bearer normal | `204`, revoga todas as sessões |
| `POST /mfa/recovery-codes` | bearer normal | `200 { recoveryCodes: string[10] }` |
| `GET /mfa/status` | bearer normal | `200 { status, pendingExpiresAt }` |

## Alternativas consideradas

### Embutir o TOTP em `UserAccount` como `@Embeddable`

Rejeitada: ao contrário da senha, o TOTP tem estado pendente com expiração e
segredo que pode nunca ser confirmado. Uma entidade própria evita carregar
esse ciclo de vida transitório no agregado principal do usuário.

### Responder `401` no login quando o segundo fator é necessário

Rejeitada: a senha já foi validada corretamente: misturar essa resposta com
falha de autenticação obrigaria todo cliente a inspecionar o corpo de todo
`401` só para saber se deve mostrar a etapa de MFA.

### Nova linha em `auth_session` para a sessão restrita de recuperação

Consideraria revogação explícita antes da expiração, mas exigiria estender o
modelo de sessão só para um caso de uso de 10 minutos. Um JWT stateless com
claim própria e expiração curta cobre o requisito ("só permite cadastrar e
confirmar um novo TOTP") com o menor diff possível; a alternativa fica
registrada caso um caso de uso futuro precise de revogação antecipada.

### KMS, Vault ou HSM para a chave de cifragem

Adiado por custo e complexidade operacional neste corte, na mesma linha do
ADR-018 para o Gmail SMTP. `AUTH_TOTP_ENCRYPTION_KEY` fica como variável de
runtime protegida, com o mesmo tratamento de `AUTH_JWT_SECRET`.

## Consequências

- `AUTH_TOTP_ENCRYPTION_KEY` é um novo segredo de produção: nunca entra no
  Git, logs ou imagens; perdê-lo torna irrecuperável a decifragem de todos os
  segredos TOTP ativos, exigindo suporte para desabilitar MFA diretamente no
  banco para os usuários afetados (sem caminho de recuperação automático).
- Sem rotação de chave implementada: uma futura rotação de
  `AUTH_TOTP_ENCRYPTION_KEY` exige novo ADR e uma estratégia de decifragem
  multi-chave usando a coluna `key_version` já reservada.
- O segredo TOTP, o código informado, os recovery codes e a URI `otpauth://`
  nunca podem entrar em logs, métricas ou respostas após a etapa de
  configuração — mesma disciplina já aplicada a hash de senha e tokens de
  recuperação.
- Uma sessão restrita de recuperação que tentar qualquer endpoint fora do
  fluxo de configuração de MFA recebe `403`, o que exige checar o novo claim
  `mfa_scope` em todo caminho autenticado (via `SessionValidationFilter`).

## Fontes

- [RFC 6238 — TOTP: Time-Based One-Time Password Algorithm](https://datatracker.ietf.org/doc/html/rfc6238)
- [dev.samstevens.totp — GitHub](https://github.com/samdjstevens/java-totp)
- [OWASP — Multifactor Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html)
