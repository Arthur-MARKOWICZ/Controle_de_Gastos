# ADR-011: JWT HS256 no corte atual

## Status

Aceito

## Data

2026-08-30

## Contexto

O ADR-005 previa JWT RS256, arquivos de chaves e rotação por `kid`. A
implementação entregue usa HS256: um único segredo HMAC assina e valida os
access tokens, e nenhum cliente ou serviço externo valida tokens. As variáveis
de caminho de chaves e de rotação não eram lidas pelo runtime, criando uma
configuração enganosa.

## Decisão

- Manter HS256 no corte atual, com `AUTH_JWT_SECRET` de pelo menos 32 bytes.
- Remover `AUTH_JWT_KEY_ID`, `AUTH_JWT_PRIVATE_KEY_PATH`,
  `AUTH_JWT_PUBLIC_KEY_PATH`, `AUTH_JWT_PREVIOUS_PUBLIC_KEY_PATH` e
  `AUTH_JWT_PREVIOUS_KEY_ID` do runtime, Compose e exemplos.
- Aceitar que a troca do segredo invalida imediatamente os access tokens
  assinados pelo valor anterior. O refresh opaco permanece rotativo e a sessão
  continua consultada pela API.
- Guardar o segredo real fora do Git, logs e imagens. Os valores padrão do
  Compose existem exclusivamente para o teste de entrega de contêineres.

Esta decisão substitui somente a escolha RS256 e a rotação de chaves prevista
no ADR-005; as demais decisões de identidade permanecem aceitas.

## Alternativas consideradas

### Implementar RS256 agora

Preservaria a rotação por `kid` prevista originalmente, mas exige implementar
carregamento de chaves, emissão do cabeçalho `kid`, seleção da chave pública na
validação e testes de rotação. Isso amplia o corte de teste operacional.

### Manter as variáveis RSA sem implementação

Rejeitada porque sugere uma proteção inexistente e induz operação incorreta.

## Consequências

- O segredo HMAC precisa ser idêntico em todas as instâncias da API.
- Não se deve fornecer esse segredo a clientes ou futuros serviços que apenas
  validem tokens; uma migração para esse cenário exige novo ADR e implementação
  RS256 ou equivalente.
- Antes de abrir o sistema ao público, a estratégia de gestão e rotação de
  segredos deve ser revista junto da etapa de produção.
