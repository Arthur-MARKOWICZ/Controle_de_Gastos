# Contrato HTTP

O contrato executável inicial está em [`openapi.yaml`](openapi.yaml). Ele cobre
cadastro, login, renovação, logout, conta atual, configuração/consulta de renda,
histórico paginado e exportação de relatórios. Os downloads exigem `from`, `to`
e `format=csv|xlsx`; eles nunca incluem descrições de gastos, e-mails ou
lançamentos logicamente excluídos.

Regras de evolução:

- prefixar a API com `/api/v1`;
- autenticar por access JWT curto e refresh opaco em cookie protegido;
- autorizar por recurso, nunca apenas por papel global;
- representar dinheiro como string decimal com exatamente duas casas e moeda
  `BRL` (por exemplo, `{"amount":"5000.00","currency":"BRL"}`);
- não expor entidades JPA diretamente;
- usar `application/problem+json` (`ProblemDetail`) com `code` estável, sem
  dados pessoais, stack trace ou detalhes internos.
