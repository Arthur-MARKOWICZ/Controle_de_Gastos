# Relatórios de verbas

## Problema

Como permitir que o titular, ou um participante autorizado, exporte uma visão
clara de gastos e de situações que exigem atenção sem duplicar saldos,
movimentações ou regras financeiras no cliente.

## Direção recomendada

O módulo `reporting` será somente de leitura. Ele consulta as interfaces
explícitas de `ledger`, `envelopes` e `income`, aplica autorização por recurso
no backend e gera o arquivo solicitado sem criar tabela, cache ou processo
assíncrono.

Há três downloads autenticados, todos com `from`, `to` e `format` obrigatórios:

```text
GET /api/v1/reports/expenses-by-purpose?from=YYYY-MM-DD&to=YYYY-MM-DD&format=csv|xlsx
GET /api/v1/reports/limit-exceeded-months?from=YYYY-MM-DD&to=YYYY-MM-DD&format=csv|xlsx
GET /api/v1/reports/goals-below-target?from=YYYY-MM-DD&to=YYYY-MM-DD&format=csv|xlsx
```

`from` e `to` são inclusivos e devem respeitar `from <= to`. O relatório de
gastos aceita qualquer intervalo de dias. Os dois relatórios mensais exigem
`from` no primeiro dia de um mês e `to` no último dia de um mês: não haverá
rateio de meta, aporte ou saldo de fechamento em períodos parciais.

Os arquivos usam `Content-Disposition: attachment`, MIME apropriado e
`Cache-Control: no-store`. CSV é UTF-8 com delimitador `;`. Conteúdos de texto
que começarem com `=`, `+`, `-` ou `@` são neutralizados antes de entrarem no
CSV ou XLSX, evitando injeção de fórmula em planilhas.

## Regras de cada relatório

### Gastos por tipo de verba

- Considera somente lançamentos ativos de tipo `EXPENSE` dentro do intervalo.
- Mostra totais por `LIMIT`, `GOAL` e `FIXED` e uma linha por verba visível,
  com período, tipo, total gasto e quantidade de lançamentos.
- Não inclui descrição do gasto, e-mail ou outros dados pessoais além do nome
  da verba necessário para a leitura financeira.

### Meses com limite extrapolado

- Considera somente verbas `LIMIT` visíveis ao solicitante.
- Mostra uma linha por verba e mês cujo saldo de fechamento seja negativo.
- O saldo considera alocação-base, aportes, gastos e carry anterior; despesas
  acima do saldo continuam válidas e são apenas sinalizadas.

### Metas de aporte abaixo do esperado

- Considera somente verbas `GOAL` visíveis ao solicitante.
- A meta esperada de cada mês é o `baseAmount` da verba. O esperado do
  intervalo é `baseAmount × número de meses completos`.
- O realizado é a soma de lançamentos ativos `CONTRIBUTION` no intervalo. Uma
  linha é emitida para cada verba cujo realizado seja menor que o esperado,
  mostrando esperado, realizado e diferença.
- A alocação automática usada no cálculo do saldo não conta como aporte
  explícito neste relatório.

## Autorização e privacidade

- Owner e participante podem gerar relatórios apenas das verbas que podem ver.
- Filtros e agregações ocorrem no backend; o cliente não recebe dados de
  recursos não autorizados para filtrá-los localmente.
- Lançamentos logicamente excluídos ficam fora de toda exportação.
- Os logs não registram conteúdo, valores monetários detalhados, descrições ou
  nomes de arquivos com dados pessoais.

## Experiência web

Uma página **Relatórios** oferece os três tipos, datas inicial e final, formato
CSV/XLSX e o botão **Gerar relatório**. O botão fica indisponível até a
validação passar; mensagens de erro são associadas aos campos e anunciadas de
forma acessível. O download usa resposta autenticada como arquivo, não o
tratamento JSON normal da API web.

## Validação antes de entregar

- [ ] Testes de intervalo obrigatório, formato inválido e intervalo parcial em
  relatórios mensais.
- [ ] Testes de isolamento entre contas e de acesso de participante.
- [ ] Testes de carry negativo, aportes abaixo da meta e exclusão lógica.
- [ ] Testes de cabeçalhos, MIME, conteúdo e neutralização de fórmulas em CSV
  e XLSX.
- [ ] OpenAPI, cliente web e testes de acessibilidade atualizados.

## Não faremos neste corte

- Aplicativo mobile, e-mail agendado, geração assíncrona ou armazenamento de
  arquivos.
- Relatórios com descrições de gastos, e-mail de autores ou histórico de
  versões de lançamentos.
- Tabelas de projeção, cache distribuído ou saldo materializado para relatórios.

## Dependência de decisão

O contrato público, a exportação de dados financeiros e a semântica de metas
estão registrados e aceitos no [ADR-014](../decisions/0014-relatorios-financeiros-exportaveis.md).
