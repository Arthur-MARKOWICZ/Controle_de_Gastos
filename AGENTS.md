# Instruções para agentes de IA

Antes de propor arquitetura, escrever código ou alterar dependências, leia integralmente:

1. [`docs/ideas/fundacao-tecnica.md`](docs/ideas/fundacao-tecnica.md) — intenção confirmada, escopo e stack obrigatória.
2. [`docs/decisions/`](docs/decisions/) — decisões arquiteturais aceitas e seus limites.
3. [`docs/privacy/`](docs/privacy/) — requisitos de privacidade e segurança desde a concepção.

## Regras do projeto

- Preserve a arquitetura de monólito modular no backend. Não introduza microserviços, filas ou caches distribuídos sem um novo ADR aceito.
- Organize o backend por capacidade de negócio, não por camada técnica global.
- Use TDD para toda regra de negócio: teste falhando, implementação mínima e refatoração.
- Foque-se em implementar o código de negócio, não em detalhes de implementação.
- Foque-se em manter o código limpo e legível.
- Foque-se em manter o código mais simples possivel sem perder qualidade.
- Trate valores monetários de BRL com `BigDecimal`, escala fixa de duas casas e
  arredondamento proibido nas fronteiras. Não use `long`, `int`, `double` ou
  `float` para representar dinheiro.
- Toda consulta e mutação deve respeitar o proprietário e os participantes da verba.
- Não registre tokens, senhas, dados financeiros detalhados ou outros dados pessoais em logs.
- Alterações de arquitetura, autenticação, persistência, contratos públicos ou tratamento de dados exigem ADR.
- Android é o alvo móvel validável neste ambiente. Preserve o alvo iOS, mas não declare que ele foi testado sem macOS/Xcode.
- Antes de concluir uma mudança, execute os testes e verificações documentados no `README.md`.

## Fonte de verdade

Se código, comentário ou tarefa contradizer os documentos acima, interrompa a implementação e proponha a atualização explícita do documento/ADR correspondente. Não altere silenciosamente a direção confirmada.
