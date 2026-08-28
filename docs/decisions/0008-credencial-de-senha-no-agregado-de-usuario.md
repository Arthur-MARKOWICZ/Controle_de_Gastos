# ADR-008: Credencial de senha incorporada ao agregado de usuário

## Status

Aceito

## Data

2026-08-28

## Contexto

O modelo inicial mantinha `password_credential` em uma tabela e repositório
separados, embora exista exatamente uma credencial de senha por conta. O
cadastro cria conta e credencial na mesma transação, e o login sempre as lê
juntas. A separação acrescentava uma consulta e permitia persistir uma conta
sem a credencial obrigatória.

## Decisão

- `PasswordCredential` é um componente incorporado de `UserAccount` e seus
  campos são persistidos em `user_account`.
- A migração copia os hashes e instantes existentes para a conta antes de
  remover `password_credential`; nenhum hash é recalculado ou registrado.
- A política de senha permanece em `AuthenticationService` e é aplicada
  somente durante `register`, antes do hash e da persistência da conta.
- Login apenas compara a senha recebida com o hash da credencial do usuário;
  não reaplica a política de criação.

Esta decisão substitui apenas a separação física entre conta e credencial
descrita no ADR-005. Sessões continuam em tabela própria e ligadas pelo UUID
interno da conta.

## Alternativas consideradas

### Manter tabela `password_credential`

Preserva uma separação física sem benefício no fluxo atual de credencial única
e torna a criação da conta dependente de duas gravações coordenadas.

### Armazenar a senha no próprio serviço de autenticação

Rejeitada porque `AuthenticationService` coordena o caso de uso; ele não é o
agregado persistido nem deve manter estado de credenciais.

## Consequências

- A conta e sua credencial obrigatória são gravadas pelo mesmo repositório.
- O hash e o instante de alteração continuam classificados como dados pessoais
  e nunca podem entrar em logs, respostas da API ou fixtures reais.
- Uma futura segunda credencial por usuário exige novo ADR e um modelo próprio.
