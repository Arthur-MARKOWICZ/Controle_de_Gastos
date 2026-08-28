---
name: java-spring-enxuto
description: Desenvolve e refatora APIs Java com Spring Boot de forma enxuta e sustentável, no fluxo controller-service-entity. Use para CRUDs, regras de negócio e persistência; não use para aplicações Java sem Spring.
---

# Java e Spring Boot enxutos

Entregue a menor solução que preserve comportamento, legibilidade, validação e testes. Não confunda poucas linhas com atalhos arquiteturais.

## Antes de alterar

1. Leia `pom.xml` ou `build.gradle`, o wrapper, a versão do Java/Spring Boot, os testes e os arquivos vizinhos. Siga as convenções existentes quando forem seguras.
2. Consulte a documentação oficial da versão detectada antes de adotar ou trocar uma API. Não atualize dependências nem crie infraestrutura sem necessidade do requisito.
3. Defina o fluxo mínimo e as invariantes. Para correções ou mudanças de comportamento, primeiro escreva um teste focado que falhe pelo motivo esperado.

## Estrutura mínima

Organize por funcionalidade, sob o pacote raiz da aplicação:

```text
pedido/
├── PedidoController.java
├── PedidoService.java
├── Pedido.java
└── PedidoRepository.java   # somente quando houver persistência
```
Cada classe deve estar em seu pacote, com nome igual ao nome do tipo pacote. Por exemplo, uma classe do tipo service deve estar dentro de uma pasta chamada service.  
Mantenha o sentido das dependências:

```text
HTTP → Controller → Service → Entity
                         └→ Repository → banco
```

- **Controller:** converte HTTP em chamada de caso de uso; valida entrada; define status, headers e DTO de resposta. Não contém regra de negócio nem acessa repository.
- **Service:** concentra o caso de uso, coordena entities e repository e delimita a transação. Prefira classe concreta; crie interface apenas quando existir mais de uma implementação ou uma fronteira real.
- **Entity:** representa estado e protege invariantes. Não conhece HTTP, controller ou DTO.
- **Repository:** existe apenas para persistência e expõe somente as operações necessárias. Não crie implementação manual quando o Spring Data já a fornece.

## Regras de implementação

- Injete dependências pelo único construtor, guarde-as em campos `final` e omita `@Autowired` nesse construtor.
- Não exponha entity JPA diretamente na API. Use DTOs pequenos; prefira `record` para dados imutáveis quando a versão do Java e as bibliotecas do projeto suportarem. Faça o mapeamento trivial perto da fronteira, sem criar uma camada de mapper.
- Valide requests com Bean Validation e `@Valid`; mantenha invariantes de negócio também no domínio/service, sem depender apenas da validação HTTP.
- Em aplicações com persistência, coloque `@Transactional` em métodos públicos da classe concreta de service; use `readOnly = true` nas leituras quando apropriado. Não dependa de autochamada para ativar transações.
- Use `Optional` principalmente como retorno que representa ausência; converta a ausência em exceção de domínio no service. Não use `Optional` como campo de entity ou parâmetro.
- Para erros REST consistentes, centralize apenas os casos necessários em `@RestControllerAdvice` e retorne `ProblemDetail`; não revele stack trace ou detalhes internos.
- Prefira os atalhos HTTP específicos (`@GetMapping`, `@PostMapping` etc.) e respostas com semântica HTTP correta. Não adicione paginação, cache, eventos, herança, builders, classes base ou novas dependências sem demanda concreta.
- Evite duplicação real, mas não extraia abstrações antes de haver repetição ou variação. Nomes claros são preferíveis a comentários que repetem o código.

## Testes e conclusão

- Teste regras e invariantes no nível mais rápido possível. Use testes MVC/JPA focados somente para contratos, serialização, validação, mapeamentos e queries; reserve contexto completo para fluxos críticos.
- Execute primeiro o teste focado e depois a suíte do projeto pelo wrapper existente. Rode formatter/linter configurado e verifique que não há warnings novos relevantes.
- Na entrega, resuma o comportamento, os testes executados e decisões não triviais. Cite as páginas oficiais consultadas; sinalize qualquer padrão que não pôde ser verificado para a versão do projeto.

## Fontes oficiais

- [Estrutura de código no Spring Boot](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)
- [Injeção por construtor no Spring Framework](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
- [Mapeamento de requests no Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html)
- [Request body e validação](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html)
- [Respostas de erro com Problem Details](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- [Transações declarativas](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
- [Conceitos de repositories do Spring Data](https://docs.spring.io/spring-data/jpa/reference/repositories/core-concepts.html)
- [Testes no Spring Boot](https://docs.spring.io/spring-boot/reference/testing/)
- [Records no Java](https://docs.oracle.com/en/java/javase/25/language/records.html)
- [`Optional` no Java](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Optional.html)
