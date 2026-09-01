# ADR-014: Relatórios financeiros exportáveis por período

## Status

Aceito

## Data

2026-08-31

## Contexto

O produto prevê relatórios na web e já possui verbas com os propósitos
`LIMIT`, `GOAL` e `FIXED`, lançamentos append-only com exclusão lógica e saldo
acumulável. O titular precisa exportar gastos por tipo, identificar meses em
que limites foram ultrapassados e identificar metas de aporte abaixo do
esperado. Participantes autorizados também precisam gerar os relatórios das
verbas compartilhadas que podem visualizar.

Os dados exportados são financeiros e pessoais. A fundação técnica exige
autorização por recurso, `BigDecimal`/`NUMERIC(19,2)` para BRL, REST/OpenAPI
como contrato e web como canal de relatórios. O repositório já reserva o módulo
`reporting`, mas ele ainda não contém comportamento.

## Decisão

- Implementar relatórios como consultas no módulo `reporting`, sem novas
  tabelas, cache, fila ou armazenamento de arquivo. O módulo depende apenas de
  interfaces de leitura explícitas dos módulos de domínio necessários.
- Publicar três endpoints autenticados, um por relatório, com `from`, `to` e
  `format=csv|xlsx` obrigatórios:

  ```text
  GET /api/v1/reports/expenses-by-purpose
  GET /api/v1/reports/limit-exceeded-months
  GET /api/v1/reports/goals-below-target
  ```

- Usar datas inclusivas em `America/Sao_Paulo`. Gastos por tipo aceita qualquer
  intervalo válido. Relatórios mensais aceitam somente meses completos: início
  no primeiro dia do mês e término no último dia do mês. A validação retorna
  `400 application/problem+json` para intervalos inválidos.
- Autorizar owner e participante pelas mesmas regras de visibilidade das
  verbas. A consulta retorna somente lançamentos ativos e recursos visíveis ao
  solicitante; e-mail, descrições de gastos e dados de recursos ocultos não
  entram em arquivos.
- Um limite foi extrapolado quando o saldo de fechamento mensal de uma verba
  `LIMIT` é negativo, considerando alocação-base, aportes, gastos e carry.
  O relatório não bloqueia nem altera o lançamento original.
- Uma meta ficou abaixo quando, em uma verba `GOAL`, a soma de `CONTRIBUTION`
  ativos no intervalo é menor que `baseAmount` multiplicado pelos meses
  completos selecionados. A alocação-base automática não é tratada como aporte
  explícito neste relatório.
- CSV é UTF-8 delimitado por `;`. Os dois formatos neutralizam conteúdo textual
  que possa ser interpretado como fórmula. As respostas usam download, MIME
  correto e `Cache-Control: no-store`.

## Alternativas consideradas

### Um endpoint genérico com o tipo como parâmetro

Reduziria rotas, mas cada relatório tem semântica, validação e colunas
próprias. Endpoints explícitos são mais fáceis de documentar e testar neste
corte pequeno.

### Relatórios com dias parciais para regras mensais

Aceitar qualquer data exigiria rateio ou uma regra surpreendente para meta e
saldo de fechamento. Meses completos mantêm valores financeiros exatos e uma
interpretação previsível.

### Contar alocação-base como aporte de meta

Faria toda meta parecer atendida pelo cálculo automático de saldo e apagaria a
diferença entre alocação planejada e aporte lançado. O relatório mede somente
`CONTRIBUTION` explícita.

### Criar projeções ou arquivos persistidos

Poderia acelerar consultas extensas, mas duplicaria dados, criaria política de
retenção adicional e aumentaria operação. Só será reavaliado com evidência de
desempenho insuficiente.

### Exportar descrições e autores dos gastos

Ofereceria mais detalhe, porém aumenta exposição de dados pessoais e não é
necessário para os objetivos definidos. Foi rejeitado por minimização.

## Consequências

- O OpenAPI, os testes de aceitação e o cliente web precisam evoluir juntos.
- Consultas devem preservar isolamento por proprietário/participação, inclusive
  nas agregações e nos relatórios vazios.
- As linhas de limite negativo dependem do cálculo de saldo já centralizado no
  backend; o frontend não recalcula carry ou dinheiro.
- A decisão torna `baseAmount` também a meta mensal esperada para `GOAL` no
  relatório. Caso o produto passe a exigir uma meta independente da alocação,
  será necessária modelagem própria e novo ADR.
- Não há mudança em autenticação, moeda, retenção ou direitos do titular; as
  regras existentes de logs, exportação e exclusão continuam aplicáveis.

## Referências

- [Fundação técnica](../ideas/fundacao-tecnica.md)
- [ADR-001: Monólito modular](0001-monorepo-e-monolito-modular.md)
- [ADR-003: Privacidade desde a concepção](0003-privacidade-desde-a-concepcao.md)
- [ADR-006: Valores monetários decimais](0006-valores-monetarios-decimais-e-renda-mensal.md)
- [Inventário de dados pessoais](../privacy/inventario-de-dados.md)
