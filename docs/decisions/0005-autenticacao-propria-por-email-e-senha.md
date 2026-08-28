# ADR-005: Autenticação própria por e-mail e senha

## Status

Aceito

## Data

2026-08-27

## Contexto

O primeiro corte precisa de cadastro e login por e-mail e senha, mas operar
Keycloak agora contraria a direção confirmada e aumenta o runtime da VPS. A
decisão substitui o ADR-002 e assume explicitamente a responsabilidade de
armazenar credenciais, emitir tokens e revogar sessões no módulo `identity`.

## Decisão

- Manter conta, credencial e sessão separadas, ligadas pelo UUID interno. E-mail
  nunca identifica recursos financeiros.
- Normalizar e-mail e armazenar senha somente como Argon2id, com salt de 16
  bytes, hash de 32 bytes, 19 MiB, duas iterações e paralelismo 1. O custo será
  medido na VPS e elevado enquanto a validação permanecer próxima ou abaixo de
  um segundo.
- Emitir access JWT RS256 por 15 minutos com apenas `iss`, `aud`, `sub`, `sid`,
  `jti`, `iat`, `nbf` e `exp`. Chaves privadas ficam fora do Git; `kid` permite
  rotação e a chave pública anterior só permanece durante a expiração dos JWTs.
- Emitir refresh opaco com 256 bits de aleatoriedade, persistindo apenas
  SHA-256 do segredo. Rotacionar a cada uso, renovar 30 dias de inatividade e
  limitar a sessão a 365 dias. Reuso revoga a família inteira.
- Consultar `auth_session` em toda requisição autenticada para que logout,
  expiração e bloqueio tenham efeito imediato.
- Entregar refresh somente no cookie `__Secure-refresh_token`, `HttpOnly`,
  `Secure`, `SameSite=Strict` e path `/api/v1/auth`. O perfil HTTP local usa
  `refresh_token` sem `Secure`.
- Aceitar cadastro público sem verificação de e-mail, sempre com resposta
  genérica. A conta permanece não verificada e não pode recuperar senha,
  aceitar convite ou executar ação sensível baseada em e-mail.
- Não oferecer recuperação de senha neste corte.
- Derivar chaves de tentativas com HMAC de e-mail/IP e reter apenas contadores
  técnicos por 24 horas.

## Motivos

A separação preserva referências financeiras e deixa uma migração futura
possível. Argon2id é adaptativo e torna vazamentos mais caros de explorar.
JWT curto reduz o dano de cópia; assinatura assimétrica impede que serviços que
só validam tokens possam emiti-los. Refresh opaco e hasheado evita transformar
um dump do banco em sessões prontas. A rotação detecta reuso. A consulta ao
banco acrescenta uma leitura, mas é necessária para o requisito escolhido de
logout imediato. Cookies `HttpOnly` evitam expor o refresh ao JavaScript. UUIDs
e tokens sem e-mail minimizam dados pessoais replicados.

## Alternativas consideradas

### Keycloak/OIDC

Continua tecnicamente válido, mas foi adiado para reduzir operação e atender à
experiência atual de e-mail/senha. Uma adoção futura exige novo ADR, vínculo de
identidades e redefinição explícita de senha; hashes não serão exportados
silenciosamente.

### Refresh JWT sem estado

Rejeitado porque dificulta rotação, detecção de reuso e revogação imediata.

### Access JWT sem consulta de sessão

Rejeitado porque logout deixaria o token utilizável por até 15 minutos.

## Consequências

- O time passa a responder por benchmark de Argon2, rotação de chaves,
  monitoramento de abuso e resposta a incidentes de identidade.
- Perder a senha não tem autosserviço até a próxima entrega de identidade.
- Cadastro público só pode ser aberto após aviso de privacidade, canal do
  titular, exportação/exclusão, revisão jurídica e procedimento de incidentes.
- O ADR-002 fica superado e nenhuma configuração Keycloak permanece ativa.

## Fontes

- [Spring Security — armazenamento de senhas](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [OAuth 2.0 Security BCP — refresh token rotation](https://datatracker.ietf.org/doc/html/rfc9700#section-4.14.2)
- [OWASP — respostas de autenticação](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html#authentication-responses)
