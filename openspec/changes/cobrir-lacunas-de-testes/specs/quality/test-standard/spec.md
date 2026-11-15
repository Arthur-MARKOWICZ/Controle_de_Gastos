# Spec Delta

## Purpose

Define o padrão mínimo de testes que toda unidade com comportamento do monorepo
(backend, web, mobile, desktop e MCP) MUST atender para ser considerada coberta.

## ADDED Requirements

### Requirement: Minimum test set per behavioral unit
Toda unidade com comportamento (service, entidade de domínio, endpoint, hook,
componente com lógica, controller de tela mobile, módulo do desktop ou do MCP)
SHALL ter no mínimo sete testes: ao menos um de caminho feliz, ao menos três de
situação indevida e ao menos três de erro esperado. Unidades sem lógica própria
(DTOs, records de resposta, configuração declarativa) ficam isentas.

#### Scenario: Unidade atende ao padrão
- **GIVEN** um service com um teste de caminho feliz, três de situação indevida e três de erro esperado
- **WHEN** a suíte do módulo é avaliada contra o padrão
- **THEN** a unidade é considerada coberta

#### Scenario: Unidade só com caminho feliz
- **GIVEN** uma unidade cujos testes cobrem apenas o fluxo principal
- **WHEN** a suíte é avaliada contra o padrão
- **THEN** a unidade é considerada abaixo do padrão até ganhar os cenários indevidos e de erro

#### Scenario: Record sem lógica
- **GIVEN** um record de resposta sem método com regra
- **WHEN** a suíte é avaliada contra o padrão
- **THEN** a unidade não exige testes próprios

### Requirement: Error tests assert the exact error
Um teste de erro esperado SHALL afirmar qual erro ocorreu: o tipo da exceção,
o status HTTP com o `type` ou código do `ProblemDetail`, ou a mensagem exibida
ao usuário. Afirmar apenas que "algo falhou" ou que nada quebrou MUST NOT contar
como teste de erro.

#### Scenario: Erro de domínio verificado pelo tipo
- **WHEN** um teste envia uma alocação acima da renda
- **THEN** ele afirma o tipo específico da exceção de alocação excedente

#### Scenario: Erro HTTP verificado pelo problem
- **WHEN** um teste de API envia um corpo inválido
- **THEN** ele afirma o status `400` e o código `INVALID_REQUEST` do problem

#### Scenario: Erro de tela verificado pela mensagem
- **WHEN** um teste de componente simula falha `422` da API
- **THEN** ele afirma a mensagem de erro renderizada para o usuário

#### Scenario: Asserção genérica não conta
- **GIVEN** um teste que só verifica que uma exceção qualquer foi lançada
- **WHEN** a unidade é avaliada contra o padrão
- **THEN** esse teste não é contado entre os três erros verificados

### Requirement: Endpoint tests cover authentication and resource authorization
Todo endpoint REST autenticado SHALL ter, entre suas situações indevidas, um
teste de acesso sem sessão que recebe `401` e um teste de acesso por usuário
que não é proprietário nem participante que recebe `403` ou `404`, sem expor
dados do recurso. Quando o recurso admite participante, SHALL haver também o
caso do participante tentando ação reservada ao proprietário.

#### Scenario: Acesso sem sessão
- **WHEN** a requisição chega sem token de acesso
- **THEN** a API responde `401`

#### Scenario: Acesso por terceiro
- **GIVEN** uma verba do titular A
- **WHEN** o titular B, sem vínculo com a verba, chama o endpoint
- **THEN** a API responde `403` ou `404`
- **AND** o corpo não contém nome, saldo nem participantes da verba

#### Scenario: Participante em ação do proprietário
- **GIVEN** o titular B é participante da verba do titular A
- **WHEN** B tenta uma ação reservada ao proprietário
- **THEN** a API responde `403`

### Requirement: Money edge case in money-handling units
Toda unidade que recebe, calcula ou exibe valor monetário SHALL ter ao menos
um teste de borda de `BigDecimal` ou de sua representação em string decimal:
escala diferente de duas casas, valor zero ou valor no limite permitido.

#### Scenario: Escala inválida rejeitada
- **WHEN** um teste envia o valor `"10.5"` ou `"10.555"` onde se exige duas casas
- **THEN** ele afirma a rejeição com o erro exato da unidade

#### Scenario: Valor no limite aceito
- **GIVEN** renda de `"1000.00"` e alocações somando `"900.00"`
- **WHEN** um teste aloca exatamente `"100.00"`
- **THEN** ele afirma que a alocação é aceita sem arredondamento

#### Scenario: Valor zero
- **WHEN** um teste envia `"0.00"` onde o valor deve ser positivo
- **THEN** ele afirma o erro de validação correspondente

### Requirement: Behavior-describing test names in Portuguese
Testes novos ou alterados SHALL ter nome em português que descreve o
comportamento verificado, no idioma de cada stack (`@DisplayName` ou nome do
método no Java/Kotlin, texto do `it`/`test` no Vitest).

#### Scenario: Nome descritivo
- **WHEN** um teste novo verifica a recusa de convite ao próprio proprietário
- **THEN** seu nome descreve esse comportamento em português, por exemplo "recusa convidar o proprietário da verba"
