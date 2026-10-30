---
name: analise-banco-dados
description: Audita o banco de dados e sua integração com o sistema, analisando modelagem, integridade, consultas, índices, transações, concorrência, migrações, segurança, backup e operação. Gera relatório PDF com achados priorizados e, somente após aprovação explícita por ID, cria issues aprovadas no GitHub usando gh CLI. Use para revisão de schema, performance SQL, integridade e evolução de banco de dados.
---

# Skill: Análise de Banco de Dados

## Objetivo

Analisar criticamente a estrutura, a utilização e a operação do banco de dados de um projeto de software. Identificar problemas comprovados e riscos plausíveis na modelagem, integridade, consultas, transações, segurança, evolução e recuperação de dados. Produzir um relatório PDF com evidências e ações recomendadas.

**Regra inegociável:** a auditoria começa em modo somente leitura. Não modificar schema, dados, migrações, índices, configuração ou código de aplicação; não executar DDL/DML, migrações, limpeza, restauração ou operações de escrita sem autorização explícita. Não criar issues antes de o usuário aprovar IDs específicos. Issues aprovadas serão criadas com GitHub CLI (`gh`).

## Princípios

- **Preservar os dados acima de tudo:** nenhuma escrita, migração, exclusão, restauração ou alteração de schema durante a análise padrão.
- **Evidências antes de conclusões:** referencie arquivos de schema/migração, consultas, planos de execução fornecidos, constraints, testes ou métricas reais.
- **Não invente performance:** sem `EXPLAIN`/plano de execução, volume, cardinalidade e métricas, classifique gargalos como hipóteses.
- **Não exponha dados sensíveis:** não imprima credenciais, tokens, dados pessoais, linhas reais de tabelas ou dumps. Prefira metadados e dados sintéticos.
- **Contexto antes de regras absolutas:** avalie banco, volume, carga, requisitos de consistência, retenção e criticidade. Não exija normalização máxima, índices em todas as colunas ou particionamento sem justificativa.
- **Separar defeito de oportunidade:** distinga violação de integridade, risco de desempenho, dívida técnica e melhoria opcional.
- **Segurança e menor privilégio:** use credenciais read-only quando houver acesso ao banco; não conecte nem consulte produção sem autorização explícita.
- **Não altere o projeto:** não edite código, migrações, schema, dependências ou configurações durante a auditoria.
- **Aprovação humana:** somente criar issues para IDs explicitamente aprovados pelo usuário.

## Fluxo de trabalho obrigatório

### Fase 0 — Contexto, escopo e segurança

1. Identifique a raiz do repositório e consulte `git status --short --branch`.
2. Preserve mudanças locais e arquivos não commitados.
3. Leia instruções do projeto (`AGENTS.md`, `CLAUDE.md` ou equivalentes), README, documentação de arquitetura, configuração de persistência, entidades/modelos, migrations, scripts SQL e testes.
4. Identifique SGBD e versão quando disponível, ORM/query builder, driver, estratégia de migração, ambientes e dependências entre serviços.
5. Determine quais domínios e fluxos são críticos (por exemplo, pagamentos, saldos, inventário, permissões, histórico ou relatórios).
6. Identifique requisitos conhecidos de consistência, volume, concorrência, retenção, RPO/RTO e disponibilidade. Não invente os que estiverem ausentes.
7. Se faltar contexto essencial, faça perguntas breves. Se puder avançar, documente lacunas e suposições.
8. Verifique ferramentas já instaladas. Não instale dependências nem altere configurações sem autorização.
9. Se GitHub for relevante, verifique `gh --version` e `gh auth status`; use `gh` somente para leitura nesta fase.

**Restrições de segurança:**

- Nunca mostre `.env`, strings de conexão, senhas, tokens, certificados, chaves privadas ou credenciais de CI.
- Não conecte a produção nem a bancos remotos sem autorização explícita.
- Se autorizado a consultar um banco, use conta read-only, escopo mínimo e consultas limitadas.
- Não selecione registros de clientes nem extraia conteúdo de tabelas sensíveis. Prefira catálogos do sistema, schema, estatísticas agregadas e amostras sintéticas.
- Não execute `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `TRUNCATE`, `DROP`, `ALTER`, `CREATE`, migrações, procedimentos com efeitos colaterais ou `EXPLAIN ANALYZE` sem autorização específica e análise de risco. Mesmo consultas aparentemente de leitura podem ser custosas; avalie limites e impacto.
- Não revele detalhes de segredos detectados; indique apenas o tipo de exposição e localização segura.

### Fase 1 — Mapear a arquitetura de dados

Inspecione seletivamente:

- Schemas, tabelas, views, sequences, tipos e convenções de nomenclatura.
- Chaves primárias, estrangeiras, constraints `UNIQUE`, `CHECK` e `NOT NULL`.
- Cardinalidades, relacionamentos, tabelas associativas e regras de exclusão.
- Tipos de dados, precisão numérica, datas/horários, fusos, collation e tratamento de valores nulos.
- Índices, índices compostos, únicos, parciais e funcionais quando declarados.
- Migrações, versionamento de schema, seeds e compatibilidade entre versões.
- Entidades ORM, mapeamentos, cascatas, fetch types e comportamento de lazy/eager loading.
- Consultas SQL, repositories, queries geradas relevantes e operações em lote.
- Transações, isolamento, locking, concorrência e idempotência.
- Dados históricos, auditoria, soft delete, retenção e anonimização, se aplicáveis.
- Backups, recuperação, replicação, alta disponibilidade e monitoramento quando documentados.
- Integrações entre banco e backend: pool de conexões, timeouts, transações e tratamento de falhas.

Não trate ausência de um recurso como defeito sem demonstrar sua necessidade. A ausência de uma migration tool, por exemplo, é um risco contextual, não necessariamente um bug comprovado.

### Fase 2 — Verificações seguras

1. Priorize análise estática de schema, migrations, SQL e mapeamentos ORM.
2. Execute apenas verificações existentes, seguras e de leitura, sem instalar dependências.
3. Se houver acesso autorizado a um banco não produtivo, prefira usuário read-only, metadados do catálogo, consultas agregadas e limites de tempo/linhas.
4. Não execute migrações, `EXPLAIN ANALYZE`, benchmarks de carga, vacuum manual, reindexação, locks, restauração, exportação de dados ou scripts de manutenção sem autorização específica.
5. Registre comandos, escopo, ambiente e resultados resumidos. Não declare verificação executada se não foi.
6. Se um plano de execução ou estatísticas não estiverem disponíveis, indique que a análise de performance é preliminar.
7. Se o ambiente de banco não estiver acessível, prossiga com o repositório e documente essa limitação.

### Fase 3 — Áreas de avaliação

Avalie somente as áreas pertinentes e marque as demais como não aplicáveis ou não verificadas.

#### 3.1 Modelagem e normalização

- Representação correta das entidades e relacionamentos.
- Normalização e redundância de dados, considerando requisitos de leitura e escrita.
- Cardinalidades e tabelas associativas.
- Entidades com responsabilidades misturadas ou duplicação de conceitos.
- Modelagem de estados e histórico.
- Consistência de nomenclatura e convenções.
- Uso de tipos adequados ao domínio.

Não recomende normalização ou desnormalização sem explicar anomalia, custo ou requisito que a justifique.

#### 3.2 Integridade e regras de domínio

- Chaves primárias e estrangeiras adequadas.
- Constraints `UNIQUE`, `CHECK` e `NOT NULL` para invariantes de domínio quando apropriado.
- Integridade referencial e comportamento `ON DELETE`/`ON UPDATE`.
- Valores inválidos, estados impossíveis e colunas sem regra clara de nulabilidade.
- Precisão e arredondamento para dinheiro/quantidades.
- Tratamento de datas, timezone e intervalos.
- Risco de duplicidade e condições de corrida em operações concorrentes.
- Consistência entre validações da aplicação e garantias do banco.

Não recomende depender exclusivamente da aplicação para invariantes que precisam ser garantidos em concorrência, sem considerar a compatibilidade com o modelo de negócio.

#### 3.3 Consultas e desempenho

- Índices coerentes com filtros, joins, ordenações e constraints de unicidade.
- Ordem e seletividade de índices compostos quando houver evidência suficiente.
- Consultas N+1, selects repetidos e carregamento excessivo no ORM.
- Full scans em tabelas grandes, joins custosos e filtros não sargable, apenas quando evidenciados.
- Paginação eficiente, limites de resultados e consultas sem filtros.
- Uso excessivo de `SELECT *` em caminhos relevantes.
- Agregações, relatórios e operações em lote.
- Pool de conexões, concorrência e consultas longas.
- Estatísticas, cardinalidade, planos e métricas quando disponíveis.

Não adicione índices por reflexo: eles consomem armazenamento e aumentam custo de escrita. Relacione qualquer sugestão às consultas reais e valide a seletividade e o plano antes de propor mudanças definitivas.

#### 3.4 Transações e concorrência

- Escopo transacional e atomicidade de operações de negócio.
- Nível de isolamento e anomalias relevantes.
- Deadlocks e ordem de aquisição de locks.
- Atualizações perdidas e condições de corrida.
- Estratégias de locking otimista/pessimista quando justificadas.
- Idempotência, constraints únicas e repetição segura.
- Fronteiras entre transações da aplicação e chamadas externas.
- Uso de transações longas e recursos mantidos durante I/O.

Não afirme que há deadlock ou race condition sem evidência; classifique riscos estáticos como hipóteses a reproduzir em ambiente seguro.

#### 3.5 Migrações e evolução de schema

- Migrações versionadas, reprodutibilidade e histórico.
- Diferenças entre schema esperado e schema aplicado, se verificáveis.
- Alterações destrutivas e perda potencial de dados.
- Compatibilidade entre versões antigas e novas da aplicação.
- Estratégias expand/contract para deploys sem parada quando necessárias.
- Backfill, índices e constraints em tabelas grandes.
- Rollback e estratégia de recuperação.
- Seeds idempotentes e separação entre dados de exemplo e produção.

Não execute ou aplique migrações durante a auditoria. Qualquer plano de migração deve indicar riscos, pré-condições, backup e validação.

#### 3.6 Segurança e privacidade

- Privilégios mínimos de contas e separação de usuários por função.
- Credenciais e strings de conexão expostas em arquivos ou logs.
- TLS e configuração segura conforme ambiente.
- SQL injection e uso de consultas parametrizadas.
- Separação de dados por usuário/tenant e permissões.
- Dados pessoais, financeiros ou sensíveis desnecessários.
- Criptografia, retenção, anonimização e exclusão quando pertinentes.
- Auditoria de alterações e acesso a dados críticos.
- Backups protegidos e acesso restrito.
- Mascaramento de dados em logs e ambientes de teste.

Não teste credenciais nem explore banco remoto. Se um segredo estiver versionado, não copie o valor para o relatório; recomende remoção segura do histórico conforme processo aprovado e rotação do segredo.

#### 3.7 Backup, recuperação e disponibilidade

- Existência de estratégia de backup documentada.
- Frequência, retenção, criptografia e isolamento dos backups.
- Testes de restauração, RPO e RTO conhecidos.
- Replicação, failover e monitoramento quando exigidos pela criticidade.
- Procedimentos para recuperação de corrupção, exclusão acidental ou falha de região.
- Alertas de espaço, conexões, replicação e saúde do banco.

Não afirme que backups são restauráveis apenas porque existem arquivos. Sem teste de restauração documentado, registre como “não verificado”.

#### 3.8 Observabilidade e operação

- Métricas de latência, erros, conexões e saturação.
- Slow query logs e identificação de consultas problemáticas.
- Monitoramento de locks, deadlocks e replicação quando aplicável.
- Alertas e runbooks.
- Pool de conexões, timeouts e limites.
- Gestão de capacidade e custos com dados disponíveis.
- Procedimentos operacionais e responsabilidade por mudanças.

#### 3.9 Integração com a aplicação

- Mapeamento entre schema, entidades ORM e DTOs.
- N+1 e fetch strategies.
- Cascatas e orphan removal com risco de exclusão.
- Limites transacionais e consistência das regras de negócio.
- SQL nativo e consultas específicas do SGBD.
- Divergência entre migrations e modelos.
- Tratamento de erros de constraint e conflitos concorrentes.
- Compatibilidade de tipos e serialização.

### Fase 4 — Registrar achados acionáveis

Para cada achado, registre:

1. **ID estável:** `DB-001`, `DB-002` etc.
2. **Título:** problema concreto e claro.
3. **Categoria:** modelagem, integridade, performance, transações, migrações, segurança, recuperação, operação ou integração ORM.
4. **Localização:** arquivo/linha, tabela/coluna, constraint, índice, consulta ou entidade ORM.
5. **Evidência:** schema, código, migration, teste, plano de execução, métrica ou comportamento reproduzível.
6. **Tipo:** defeito comprovado, risco estático, hipótese de performance, dívida técnica ou oportunidade.
7. **Impacto:** integridade, confidencialidade, disponibilidade, custo ou manutenção.
8. **Recomendação:** ação concreta e alternativa considerada.
9. **Critérios de aceite:** condições verificáveis.
10. **Severidade:** crítica, alta, média ou baixa.
11. **Prioridade:** P0, P1, P2 ou P3.
12. **Confiança:** alta, média ou baixa.
13. **Esforço:** XS, S, M, L ou XL, com justificativa.
14. **Riscos e dependências:** lock, downtime, backfill, compatibilidade, recursos e reversão.
15. **Plano de validação:** testes, consulta segura, plano de execução, benchmark controlado ou restauração em ambiente isolado.
16. **Decisão sugerida:** corrigir, medir, mitigar, investigar, aceitar o risco ou adiar.

Não inclua dados de linhas reais no relatório. Para exemplos, use dados sintéticos. Não invente cardinalidade, volume, latência ou impacto financeiro.

### Fase 5 — Priorizar

Considere impacto, probabilidade, evidência, exposição, volume conhecido, esforço, risco de mudança e reversibilidade.

- **P0 — crítico:** perda/corrupção de dados confirmada ou altamente provável, exposição grave de dados, falha crítica de integridade ou risco de indisponibilidade severa.
- **P1 — alto:** violação importante de integridade, risco de segurança significativo ou problema de consulta/transação com evidência consistente.
- **P2 — moderado:** melhoria de consistência, manutenção, migração ou desempenho que merece planejamento.
- **P3 — exploratório:** hipótese ou otimização sem dados suficientes para justificar execução imediata.

Não confunda severidade com prioridade. Uma hipótese de consulta lenta não deve ser P0 sem evidências. Inclua a seção **“O que não recomendo fazer agora”** para propostas de indexação, particionamento, sharding, troca de SGBD ou refatoração que não estejam justificadas.

### Fase 6 — Gerar relatório PDF

Produza PDF profissional, legível e em português brasileiro, salvo pedido contrário.

Nome sugerido:
`relatorio-analise-banco-dados-AAAA-MM-DD.pdf`

Salve em `reports/` se adequado e não sobrescreva arquivos existentes. Não faça commit automaticamente.

Estrutura recomendada:

1. Capa: projeto, data, branch/versão e SGBD identificado.
2. Resumo executivo.
3. Escopo, metodologia, ambientes consultados e limitações.
4. Visão geral do modelo de dados.
5. Mapa de tabelas/relacionamentos relevantes, se puder ser derivado com segurança.
6. Matriz priorizada de achados.
7. Detalhamento de cada achado com evidências, impacto e recomendação.
8. Integridade, consultas, transações e migrações.
9. Segurança, privacidade, backup e recuperação.
10. O que não fazer agora.
11. Plano de melhoria por fases e riscos de rollout.
12. Decisões solicitadas: IDs para aprovar, rejeitar, revisar ou investigar.
13. Apêndice com referências a arquivos, migrations, consultas e verificações.

Não inclua credenciais, dados pessoais, amostras de produção ou conteúdo sensível. Separe claramente os achados confirmados dos riscos e hipóteses.

Para gerar o PDF:
- Prefira ferramentas já disponíveis.
- Se usar Python, prefira `reportlab.platypus`.
- Não instale dependências sem autorização.
- Se não houver ferramenta disponível, ofereça Markdown como alternativa e informe que o PDF não foi criado.
- Valide existência, tamanho, abertura e seções esperadas quando possível.
- Informe o caminho real; não declare sucesso sem validação.

### Fase 7 — Apresentar e solicitar aprovação

Depois de validar o PDF:

1. Informe caminho/link real do relatório, quando disponível.
2. Resuma os principais achados, com ID, severidade, prioridade, confiança e esforço.
3. Separe defeitos comprovados de riscos e hipóteses.
4. Informe testes/consultas executados, ambiente e limitações.
5. Peça aprovação explícita dos IDs para criação de issues.
6. Aprovar o relatório não autoriza criar todas as issues.
7. Se a resposta for ambígua, não execute a criação.

Exemplo:
“Quais achados você aprova para criação de issues? Posso criar `DB-001` e `DB-003`. Não farei alterações no banco.”

### Fase 8 — Criar issues aprovadas com GitHub CLI

Somente após aprovação explícita dos IDs e escopo.

#### Verificações

1. Confirme o repositório:
   `gh repo view --json nameWithOwner,url`
2. Verifique autenticação:
   `gh auth status`
3. Pesquise issues abertas e fechadas semelhantes:
   `gh issue list --repo OWNER/REPO --state all --search "termos relevantes" --json number,title,state,url`
4. Confira labels:
   `gh label list --repo OWNER/REPO`
5. Se houver duplicata provável, não crie outra; apresente a issue existente e peça autorização para vinculá-la ou atualizá-la.
6. Se repositório ou permissões não estiverem corretos, pare e informe.
7. Não crie labels/milestones, branches, commits, PRs nem modifique código/schema sem autorização separada.

#### Conteúdo da issue

Cada issue deve conter:

- **Contexto e localização:** tabela, coluna, consulta, migration, entidade ORM ou módulo.
- **Problema e evidência:** sem dados reais sensíveis.
- **Impacto:** integridade, segurança, desempenho, recuperação ou manutenção.
- **Correção proposta:** escopo claro.
- **Fora de escopo:** o que não deve ser alterado.
- **Critérios de aceite:** condições objetivas.
- **Plano de validação:** testes e verificações de banco.
- **Plano de rollout/rollback:** quando a mudança puder afetar dados, locks ou disponibilidade.
- **Riscos e dependências.**
- **Severidade, prioridade, confiança e esforço.**
- **Referência:** ID `DB-XXX` e relatório PDF.

Não misture alterações independentes. Para mudanças destrutivas, migrações ou backfills, os critérios devem exigir revisão explícita do plano e backup/recuperação apropriados.

#### Criar com `gh`

Exemplo (substitua valores reais; não execute placeholders):

```bash
gh issue create \
  --repo OWNER/REPO \
  --title "Adicionar constraint de unicidade ao vínculo de usuário" \
  --body-file /caminho/temporario/issue-body.md \
  --label "bug"
```

- Prefira `--body-file` com arquivo temporário fora do repositório.
- Use somente labels existentes.
- Crie apenas IDs aprovados.
- Verifique duplicatas antes de cada criação.
- Se falhar, investigue antes de repetir para evitar duplicação.
- Confirme número e URL na saída antes de declarar sucesso.
- Interrompa se a aprovação mudar.

#### Segurança e idempotência

- Não inclua credenciais, dados pessoais, valores reais de registros ou dumps.
- Não altere issues existentes sem autorização específica.
- Não execute DDL/DML, migrações, deploys, backfills ou mudanças no banco como parte da criação de issues.
- Registre ID, título, número, URL e resultado de cada operação.

## Formato da resposta final

Após a auditoria:
- Caminho real do PDF.
- Escopo, SGBD e limitações.
- Principais achados com severidade, prioridade e confiança.
- Verificações executadas e resultados.
- Hipóteses que exigem validação.
- Solicitação de aprovação por IDs.

Após a criação de issues:
- Repositório confirmado.
- Issues criadas com número e URL.
- Duplicatas não recriadas.
- Falhas e pendências.
- Confirmação de que banco, schema e código não foram alterados.

## Checklist de qualidade

- [ ] Escopo e ambiente foram identificados sem expor segredos.
- [ ] A análise separa defeitos comprovados, riscos e hipóteses.
- [ ] Modelagem, constraints, consultas, transações e migrações foram avaliadas quando aplicáveis.
- [ ] Segurança, privacidade, backup e recuperação foram tratados com honestidade sobre limitações.
- [ ] Não foram inventados dados de performance, volume ou disponibilidade.
- [ ] Recomendações incluem evidência, impacto, critérios de aceite e validação.
- [ ] Riscos de migração, locks, perda de dados e rollback foram considerados.
- [ ] O PDF foi realmente criado e validado.
- [ ] Nenhuma issue foi criada antes da aprovação explícita.
- [ ] Duplicatas foram pesquisadas e somente IDs aprovados foram processados.
- [ ] Nenhuma alteração no banco ou no código foi realizada.
