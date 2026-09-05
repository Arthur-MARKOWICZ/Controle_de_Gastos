# ADR-020: Login social com Google e GitHub

## Status

Aceito

## Data

2026-09-05

## Contexto

O login hoje é só por e-mail e senha ([ADR-005](0005-autenticacao-propria-por-email-e-senha.md)),
com a senha embutida e obrigatória em `UserAccount`
([ADR-008](0008-credencial-de-senha-no-agregado-de-usuario.md)). A ideia de
adicionar login via Google e via GitHub, permitindo múltiplos métodos por
conta, exige revisitar as duas decisões: o ADR-005 já previa que uma adoção
futura de um provedor externo exigiria "novo ADR, vínculo de identidades e
redefinição explícita de senha"; o ADR-008 previa que "uma futura segunda
credencial por usuário exige novo ADR e um modelo próprio". Este documento é
essa decisão explícita, cobrindo autenticação, persistência e contrato
público, conforme exigido pelo `AGENTS.md`.

## Decisão

### Modelo de dados

- Nova entidade `IdentityProviderLink`, em relacionamento um-para-muitos com
  `UserAccount` (um usuário pode ter vários vínculos; um vínculo pertence a
  exatamente um usuário): `id`, `user_id`, `provider` (`GOOGLE` | `GITHUB`),
  `provider_user_id`, `email_at_link_time` (apenas informativo, nunca usado
  como chave de busca), `linked_at`.
- Constraints `UNIQUE (provider, provider_user_id)` e
  `UNIQUE (user_id, provider)`, como no plano original.
- `PasswordCredential` deixa de ser sempre presente. Isso supera parcialmente
  o ADR-008: os campos passam a ser opcionais em `UserAccount` (ou isolados em
  um componente que pode estar ausente). Uma conta é válida se tiver ao menos
  uma credencial — uma senha **ou** ao menos um `IdentityProviderLink`.
- Invariante enforced no domínio (não só no banco): `UserAccount` nunca pode
  ficar sem nenhum método de login. Toda operação que remove um método
  (desvincular provider) verifica antes se sobra pelo menos outro.

### Endpoints

- **Um único endpoint de callback por provider**
  (`POST /api/v1/auth/oauth/{provider}/callback`), cobrindo cadastro e login
  juntos:
  1. Busca `IdentityProviderLink` por `(provider, provider_user_id)`.
     Se existir, é login: segue para a etapa de sessão abaixo.
  2. Se não existir vínculo, verifica se já existe `UserAccount` com o e-mail
     retornado pelo provider. Se existir, **não vincula automaticamente** —
     responde com o mesmo erro genérico de falha de login do item abaixo,
     exatamente como pedido no plano original.
  3. Se não existir vínculo nem conta com esse e-mail, cria um `UserAccount`
     novo sem senha e já com o vínculo do provider.
  4. Resposta genérica para os dois casos de falha (e-mail ausente do
     provider, conta existente não vinculada), sem detalhar qual dos dois
     ocorreu — mesmo padrão de não-enumeração usado no login por senha.
- `POST /api/v1/auth/oauth/{provider}/link` (autenticado): vincula um provider
  adicional à conta já logada. Reaproveita a checagem de unicidade e devolve
  conflito amigável (não erro de banco cru) se o `provider_user_id` já
  pertence a outra conta.
- `DELETE /api/v1/auth/oauth/{provider}` (autenticado): remove um vínculo.
  Recusa com erro explícito se for o único método de login restante da conta.
- `POST /api/v1/auth/password` (autenticado): define uma senha para quem
  ainda não tem. Aplica a mesma política de senha usada em `register`
  (ADR-008), incluindo o mesmo custo Argon2id do ADR-005.

### MFA

- Login via Google/GitHub passa pelo mesmo desafio de MFA do login por senha
  quando a conta tem TOTP `ENABLED` (ADR-019): nenhuma sessão, access token ou
  refresh cookie antes da confirmação do segundo fator, reaproveitando
  `MfaLoginChallenge` também para o fluxo OAuth. *(decisão confirmada
  explicitamente ao revisar este plano.)*

### E-mail ausente do GitHub

- Se o GitHub não retornar e-mail no perfil, a conexão falha com a mesma
  resposta genérica de erro. Este ADR **não** implementa o fallback de
  consultar `GET /user/emails` com o escopo `user:email` — avaliado durante a
  revisão e descartado deliberadamente para este corte. Consequência aceita:
  contas do GitHub com e-mail privado não conseguem se cadastrar por este
  fluxo.

### CSRF e integridade do fluxo (default proposto, não perguntado explicitamente)

- O backend gera e assina o parâmetro `state` antes de redirecionar ao
  provider, e o valida no callback antes de qualquer busca por
  `provider_user_id`. Sem isso o fluxo fica aberto a CSRF de login
  (RFC 6749 §10.12). Ajustar esta seção se a intenção for outra.

### Retry de chamadas ao provider

- O retry (até 3x) só se aplica a chamadas seguramente reexecutáveis: falha
  de rede antes de qualquer resposta do provider, e chamadas de leitura
  (userinfo) com o access token já obtido. A troca do `authorization code`
  por token **não** é retentada cegamente, porque o código é de uso único —
  uma falha de rede após o provider já ter processado o código tornaria o
  retry um novo erro (código já consumido), mascarando o problema real.

### Sessão emitida

- O login por provider emite sessão pelo mesmo mecanismo de `AuthSession` /
  refresh opaco rotativo já existente (ADR-005), sem um segundo mecanismo de
  token.

### Fluxo OAuth para clientes nativos (Android/iOS)

O app nunca fala diretamente com Google/GitHub nem guarda `client_secret`
(são clientes públicos). Ele reaproveita o navegador do sistema e um código
de handoff de uso único, no mesmo estilo dos artefatos já existentes no
projeto (`PasswordResetToken`, `mfa_login_challenge`): hash persistido, valor
bruto exposto uma única vez, TTL curto.

1. O app abre o navegador do sistema (Custom Tabs no Android /
   `ASWebAuthenticationSession` no iOS — nunca WebView embutida, conforme
   RFC 8252) apontando para `GET /api/v1/auth/oauth/{provider}/start?client=mobile`
   no próprio backend, não direto no provider.
2. O backend gera o `state`, monta a URL de autorização do provider com o seu
   próprio `redirect_uri` https (o mesmo endpoint de callback do fluxo web) e
   redireciona o navegador do sistema.
3. O provider redireciona de volta ao callback https do backend, que roda a
   mesma lógica de upsert já descrita (login/cadastro/erro) e o mesmo desafio
   de MFA quando aplicável.
4. Em vez de `Set-Cookie`, quando `client=mobile`, o backend gera um código de
   handoff opaco (256 bits, TTL de 60 segundos, uso único, só o hash é
   persistido) e redireciona o navegador do sistema para um **Android App
   Link / iOS Universal Link** verificado pelo SO (não um custom URI scheme
   registrável por qualquer app, que poderia ser sequestrado por um app
   malicioso) contendo esse código.
5. O SO entrega o link ao app, que imediatamente chama
   `POST /api/v1/auth/oauth/mobile-handoff` com o código. O backend valida
   (uso único, TTL, hash), invalida o código e devolve o access token e o
   refresh token no corpo da resposta — não em cookie — para o app persistir
   com a mesma proteção via Keystore já prevista no ADR-005 para o refresh no
   Android.

Isso evita adicionar PKCE, um client mobile confidencial ou SDKs nativos por
provider: o app nunca lida com `client_secret` nem com o código de
autorização do provider, só com o handoff de curta duração emitido pelo
próprio backend.

## Fora de escopo deste ADR

- Reautenticação (step-up) antes de desvincular um provider ou adicionar
  senha: não especificada; a sessão autenticada normal é suficiente por ora.

## Alternativas consideradas

### Endpoints separados de cadastro e login por provider (proposta original do plano)

Rejeitada porque o provider entrega tudo em um único redirect de callback; o
backend já precisa decidir ali se é conta nova, login ou erro de vínculo. Dois
endpoints obrigariam o front a adivinhar o estado da conta antes de saber a
resposta.

### Provider como segundo fator obrigatório substituindo o TOTP

Rejeitada: manter os dois fatores exigidos independentemente evita que a
posse da conta Google/GitHub vire bypass do MFA configurado localmente.

### Buscar e-mail via `user:email` quando o GitHub não retorna e-mail público

Levantada durante a revisão como melhoria de UX, mas descartada
deliberadamente para este corte — ver seção "E-mail ausente do GitHub".

### Revogar todas as sessões ao desvincular um provider

Diferente de troca de senha/MFA (ADR-018, ADR-019), que sempre indicam
possível comprometimento de credencial, desvincular um provider quando ainda
sobra outro método de login não implica suspeita equivalente. Decisão:
**não revogar** sessões existentes nessa operação.

### Custom URI scheme em vez de App Link/Universal Link verificado

Mais simples de registrar, mas qualquer app pode declarar o mesmo esquema no
Android e interceptar o código de handoff. Rejeitado por abrir sequestro do
código de handoff; App Links/Universal Links são verificados pelo SO contra o
domínio do backend.

### SDK nativo por provider (Google Sign-In, etc.) no app

Evitaria o salto pelo navegador do sistema, mas exige manter uma dependência
e um caminho de código por plataforma e por provider (GitHub não tem SDK
mobile oficial), quebrando a paridade Android/iOS do Compose Multiplatform.
Rejeitado por complexidade desnecessária frente ao handoff via backend.

## Consequências

- `UserAccount` perde a garantia estática de "sempre tem senha"; toda
  operação que hoje assume `PasswordCredential` presente precisa revisão.
- Novo mecanismo de invariante de domínio ("sempre ao menos um método de
  login") passa a ser validado em toda remoção de vínculo, não só no banco.
- Google e GitHub recebem o e-mail do usuário como parte do fluxo de
  autorização. `docs/privacy/inventario-de-dados.md` deve ser atualizado com
  as novas colunas pessoais (`provider_user_id`, e-mail do provider, código de
  handoff) e com os dois provedores como novos operadores de dados pessoais
  (mesmo tratamento dado ao Gmail SMTP no ADR-018) **na mesma entrega desta
  implementação**, não depois.
- Contas GitHub com e-mail privado ficam bloqueadas deste fluxo até uma
  decisão futura.
- O app mobile precisa registrar um App Link (Android) / Universal Link
  (iOS) verificado apontando para o domínio do backend, além do fluxo web já
  existente.
- Desvincular o único provider restante sem senha configurada continua
  bloqueado pela invariante "sempre ao menos um método de login"; desvincular
  quando ainda sobra outro método não revoga sessões ativas.

## Fontes

- [RFC 6749 — The OAuth 2.0 Authorization Framework, §10.12 (CSRF)](https://datatracker.ietf.org/doc/html/rfc6749#section-10.12)
- [GitHub — Authorizing OAuth Apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps)
- [Google Identity — OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect)
