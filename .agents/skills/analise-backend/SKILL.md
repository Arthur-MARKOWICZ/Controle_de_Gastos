---
name: analise-backend
description: Audita o backend de um projeto de software com foco em arquitetura, APIs, regras de negócio, segurança, dados, desempenho, confiabilidade, testes e operação. Produz relatório PDF com achados priorizados e, somente após aprovação explícita por ID, cria issues aprovadas no GitHub usando gh CLI. Use para revisão técnica de backend, health check arquitetural, análise de APIs e plano de melhorias.
---

# Skill: Análise de Backend

## Objetivo

Avaliar criticamente o backend existente, entender como implementa as regras de negócio e identificar riscos, defeitos e oportunidades técnicas concretas. Gerar um relatório PDF priorizado, com evidências, impactos, trade-offs e critérios de aceite, para apoiar decisões de engenharia.

**Regra inegociável:** a auditoria é inicialmente somente leitura. Não modificar código, configuração, banco de dados, infraestrutura ou dependências; não criar branches, commits, pull requests ou issues antes de autorização. Criar issues no GitHub apenas após aprovação explícita dos IDs pelo usuário.

## Princípios

- **Evidências antes de conclusões:** relacione cada achado a arquivos/linhas, endpoints, testes, logs fornecidos ou comportamento reproduzível.
- **Não invente falhas:** diferencie defeito comprovado, risco plausível, dívida técnica e hipótese que precisa de validação.
- **Contexto antes de padrões:** avalie a solução em relação ao tamanho, domínio, criticidade, escala e objetivos do projeto. Não recomende microserviços, cache, filas ou abstrações sem justificativa.
- **Segurança por padrão:** não exponha credenciais, tokens, chaves, dados pessoais ou informações sensíveis. Não execute testes intrusivos nem ataques contra ambientes reais.
- **Preserve o comportamento:** não proponha refatoração ampla quando uma correção pequena e verificável resolve o problema.
- **Negócio e confiabilidade:** priorize integridade de dados, segurança, correção das regras e disponibilidade antes de otimizações prematuras.
- **Estimativas honestas:** não invente números de desempenho, disponibilidade, custo ou impacto sem dados.
- **Menor privilégio:** use ferramentas e comandos somente quando necessários; evite ações destrutivas e comandos que alterem sistemas externos.
- **Aprovação humana:** nenhuma issue pode ser criada antes da aprovação explícita e específica do usuário.

## Fluxo de trabalho obrigatório

### Fase 0 — Contexto e segurança do ambiente

1. Identifique a raiz do repositório e consulte `git status --short --branch`.
2. Preserve alterações locais e arquivos não commitados. Nunca descarte, formate ou sobrescreva mudanças existentes.
3. Leia instruções locais (`AGENTS.md`, `CLAUDE.md` ou equivalentes), README, documentação técnica, arquitetura, contratos de API, arquivos de build, configurações de teste e estrutura de diretórios.
4. Identifique linguagens, frameworks, runtime, entry points, módulos, persistência, mensageria, serviços externos e ambiente de execução.
5. Determine o estágio do projeto, criticidade aparente, tipo de dados processados e principais fluxos de negócio.
6. Se contexto essencial estiver ausente, faça perguntas curtas. Se puder avançar, registre as suposições e limitações sem apresentá-las como fatos.
7. Verifique ferramentas e versões já instaladas. Não instale dependências nem atualize versões sem autorização.
8. Se GitHub for relevante, verifique `gh --version` e `gh auth status`, mas use o CLI apenas em modo de leitura durante a auditoria.

Não leia nem imprima `.env`, arquivos de credenciais, chaves privadas, tokens, dumps de dados reais ou segredos de CI. Redija valores sensíveis de logs antes de citá-los. Não execute comandos destrutivos nem operações contra produção.

### Fase 1 — Mapear a arquitetura e os fluxos

Inspecione de forma seletiva e proporcional ao tamanho do repositório:

- Organização por módulos, camadas, limites de domínio e dependências.
- Controllers/handlers, services/use cases, repositories/adapters e modelos de domínio.
- Contratos HTTP/RPC/eventos: métodos, rotas, status codes, payloads, validação e versionamento.
- Autenticação, autorização, identidade, sessão/token, permissões e isolamento por usuário/tenant.
- Regras de negócio, invariantes, transações e tratamento de estados inválidos.
- Modelo de dados, chaves, constraints, índices, relacionamentos, migrações e estratégia de consistência.
- Tratamento de erros, exceções, logs, métricas, tracing, health checks e auditoria.
- Testes unitários, integração, contrato, ponta a ponta e fixtures.
- Concorrência, idempotência, retries, timeouts, limites, paginação e operações em lote.
- Integrações externas, webhooks, filas, tarefas agendadas e falhas parciais.
- Configuração, segredos, deploy, containers, CI/CD e dependências.
- Desempenho e custo operacional somente quando houver sinais ou requisitos que justifiquem a avaliação.

Não trate a ausência de uma tecnologia específica como defeito por si só. Por exemplo, não exija cache, mensageria, CQRS, microserviços ou uma camada extra se o contexto não demonstrar necessidade.

### Fase 2 — Verificações técnicas seguras

1. Identifique os comandos de teste, lint, build e análise estática documentados no projeto.
2. Execute apenas verificações razoavelmente seguras que já estejam disponíveis e não exijam alterações ou serviços externos não autorizados.
3. Não execute migrações, seeders destrutivos, scripts de limpeza, benchmarks de carga, fuzzing intrusivo, scanners ofensivos ou testes em produção sem autorização explícita.
4. Não modifique banco de dados nem consuma endpoints que gerem efeitos colaterais.
5. Registre o comando executado, o resultado resumido e limitações relevantes. Nunca declare testes aprovados se não foram executados.
6. Se testes falharem, separe falha reproduzida, falha preexistente possível e ambiente de teste incompleto quando não for possível distinguir.
7. Se não for possível executar o projeto, prossiga com análise estática e registre isso claramente.

### Fase 3 — Áreas de avaliação

Avalie as áreas aplicáveis e registre as que não puderam ser verificadas.

#### 3.1 Arquitetura e design

- Coesão e responsabilidade dos módulos.
- Acoplamento e direção das dependências.
- Separação entre domínio, aplicação e infraestrutura quando útil.
- Fronteiras transacionais e dependências circulares.
- Complexidade desnecessária, duplicação e abstrações prematuras.
- Facilidade de testar, alterar e entender regras de negócio.

Não imponha Clean Architecture, Hexagonal, CQRS ou DDD como dogma; explique o problema concreto que uma mudança resolveria.

#### 3.2 APIs e contratos

- Consistência de rotas, verbos HTTP, status codes e formato de resposta.
- Validação de entrada e limites de tamanho.
- Contratos e documentação atualizados.
- Paginação, filtros, ordenação e limites para coleções.
- Compatibilidade e estratégia de versionamento.
- Semântica de idempotência em operações críticas.
- Códigos e mensagens de erro úteis sem revelar detalhes internos.
- CORS e exposição de endpoints, quando pertinentes.

#### 3.3 Segurança

Use práticas alinhadas a OWASP ASVS/API Security Top 10 quando pertinentes, sem afirmar certificação ou cobertura completa sem avaliação apropriada.

Verifique, quando aplicável:

- Autenticação e autorização em cada operação relevante.
- Controle de acesso a objetos e isolamento de dados por usuário/tenant.
- Validação de entrada e proteção contra injeção.
- Mass assignment e binding excessivo de campos.
- Gestão de sessão, tokens, expiração, revogação e armazenamento de credenciais.
- Segredos em código, logs, imagens ou arquivos versionados.
- Rate limiting, abuso de recursos e limites de payload.
- SSRF, path traversal, deserialização insegura e upload de arquivos, quando houver superfície correspondente.
- Criptografia em trânsito e proteção de dados sensíveis, conforme o contexto.
- Dependências vulneráveis, se ferramentas e dados locais confiáveis estiverem disponíveis.
- Erros que revelem stack traces, dados pessoais ou informações internas.
- Auditoria e rastreabilidade de operações críticas.

Não faça exploração ativa nem teste contra sistemas reais. Se detectar um possível segredo, não o reproduza no relatório; descreva a localização de forma segura e recomende rotação quando apropriado.

#### 3.4 Dados e persistência

- Integridade referencial e constraints no banco.
- Índices para consultas importantes, quando evidenciados por schema ou plano disponível.
- Transações, atomicidade e consistência de operações.
- N+1 queries, carregamento excessivo e consultas sem limites.
- Estratégia de migração e compatibilidade entre versões.
- Concorrência, condições de corrida e atualizações perdidas.
- Soft delete, retenção, auditoria e privacidade, se aplicáveis.
- Backup/restore e recuperação de desastre apenas quando houver evidências sobre a configuração.

Não alegue lentidão por olhar apenas para uma query ou entidade. Sem métricas ou planos de execução, classifique o risco como hipótese.

#### 3.5 Confiabilidade e integrações

- Timeouts explícitos e limites de recursos.
- Retries com backoff e tratamento de falhas transitórias.
- Idempotência e duplicidade em pagamentos, webhooks, jobs ou comandos.
- Circuit breakers e bulkheads somente quando a arquitetura justificar.
- Tratamento de falhas parciais e consistência eventual.
- Filas, reprocessamento e dead-letter handling quando houver mensageria.
- Graceful shutdown, health/readiness checks e degradação controlada.
- Gestão de transações distribuídas sem prometer atomicidade inexistente.

#### 3.6 Desempenho e escalabilidade

- Consultas ineficientes e acesso repetido a recursos.
- Operações síncronas bloqueantes em caminhos críticos.
- Paginação e limites de concorrência.
- Uso de memória e CPU somente quando houver evidências ou medições.
- Pool de conexões e limites de recursos.
- Caching somente com estratégia de invalidação, consistência e benefício justificável.
- Processamento assíncrono apenas quando os requisitos de latência/volume justificarem.

Não invente throughput, latência ou capacidade máxima. Proponha benchmarks seguros e reproduzíveis quando dados forem insuficientes.

#### 3.7 Testes e qualidade

- Cobertura dos caminhos críticos e regras de negócio.
- Testes de regressão, validação, autorização e casos de borda.
- Qualidade dos testes e dependência excessiva de mocks.
- Testes de integração com banco e serviços relevantes.
- Testes de contrato para APIs ou eventos quando aplicáveis.
- Reprodutibilidade, isolamento e estabilidade do conjunto de testes.

Não use percentual de cobertura como único indicador de qualidade.

#### 3.8 Observabilidade e operação

- Logs estruturados, níveis adequados e correlação de requisições.
- Métricas e tracing onde o contexto justificar.
- Health/readiness checks.
- Alertas, diagnóstico e runbooks para serviços críticos.
- Configuração por ambiente e validação de configuração na inicialização.
- CI/CD, migrações de deploy e rollback.
- Containers, privilégios mínimos e sinais de configuração insegura.
- Custos operacionais somente com dados ou estimativas claramente qualificadas.

### Fase 4 — Registrar achados acionáveis

Para cada achado, registre:

1. **ID estável:** `BE-001`, `BE-002` etc.
2. **Título:** problema concreto.
3. **Categoria:** arquitetura, API, segurança, dados, confiabilidade, desempenho, testes, observabilidade, deploy ou manutenção.
4. **Localização:** arquivo/linhas, classe, método, endpoint, query ou configuração.
5. **Tipo de evidência:** defeito reproduzido, evidência estática, teste falho, risco plausível ou hipótese.
6. **Descrição do problema:** comportamento atual e condição em que ocorre.
7. **Impacto:** risco para usuários, dados, segurança, operação, custo ou velocidade de desenvolvimento.
8. **Recomendação:** correção concreta e motivo da escolha.
9. **Critérios de aceite:** condições verificáveis, incluindo testes relevantes.
10. **Severidade:** crítica, alta, média ou baixa.
11. **Prioridade:** P0, P1, P2 ou P3.
12. **Confiança:** alta, média ou baixa.
13. **Esforço:** XS, S, M, L ou XL, com justificativa breve.
14. **Dependências e trade-offs:** riscos, migrações, compatibilidade e impacto operacional.
15. **Plano de validação:** testes, métricas, inspeções ou experimento necessários.
16. **Decisão sugerida:** corrigir, mitigar, medir, investigar, aceitar o risco ou adiar.

Não use estimativas precisas de horas sem base. Não transforme preferência arquitetural em defeito. Evite frases vagas como “melhorar segurança” ou “otimizar banco”: indique o caminho, o risco e como verificar a correção.

### Fase 5 — Priorizar

Considere severidade, exposição, probabilidade, impacto, evidência, alcance, esforço e reversibilidade.

- **P0 — crítico:** vulnerabilidade explorável ou risco grave confirmado, perda/corrupção de dados ou bloqueio crítico.
- **P1 — alto:** falha relevante de autorização, integridade, confiabilidade ou fluxo principal com evidência consistente.
- **P2 — moderado:** dívida técnica ou risco de desempenho/manutenção com impacto justificável.
- **P3 — exploratório:** melhoria de baixo impacto ou hipótese que precisa de medição.

A prioridade não substitui a análise de severidade. Para riscos de segurança, explique exposição e pré-condições sem divulgar detalhes que facilitem exploração. Se o achado for apenas suspeita, marque-o como tal e proponha validação segura.

Inclua uma seção **“O que não recomendo fazer agora”**, explicando tecnologias, refatorações ou otimizações que não se justificam pelo contexto atual.

### Fase 6 — Gerar relatório PDF

Gere um PDF profissional, legível e em português brasileiro, salvo se o usuário solicitar outro idioma.

Nome sugerido:
`relatorio-analise-backend-AAAA-MM-DD.pdf`

Salve em `reports/` se adequado, sem sobrescrever relatórios existentes. Não faça commit automaticamente.

Estrutura recomendada:

1. Capa: projeto, data, branch/versão e objetivo.
2. Resumo executivo: estado observado e principais riscos/oportunidades.
3. Escopo e metodologia: módulos, ferramentas, comandos executados e limitações.
4. Visão geral da arquitetura e fluxos críticos.
5. Matriz priorizada de achados.
6. Detalhamento individual com evidências, impactos e correções.
7. Segurança e integridade dos dados.
8. APIs, testes, confiabilidade e operação.
9. O que não fazer agora.
10. Plano de execução por etapas e dependências.
11. Decisões solicitadas: IDs para aprovar, rejeitar, revisar ou investigar.
12. Apêndice: referências a arquivos, linhas, testes e documentação.

O relatório deve separar claramente fatos, riscos e hipóteses. Não incluir segredos, tokens, payloads reais com dados pessoais ou logs sensíveis.

Para gerar o PDF:
- Prefira ferramentas já instaladas.
- Se usar Python, prefira `reportlab.platypus`.
- Não instale dependências sem autorização.
- Se não houver ferramenta disponível, ofereça Markdown como alternativa e informe que o PDF não foi criado.
- Valide existência, tamanho, abertura e seções esperadas, quando possível.
- Informe o caminho real do artefato. Não declare sucesso sem validação.

### Fase 7 — Apresentar e pedir aprovação

Após validar o relatório:

1. Apresente o caminho/link real do PDF, se disponível.
2. Resuma os principais achados com ID, prioridade, severidade e esforço.
3. Separe defeitos comprovados de riscos e hipóteses.
4. Informe verificações executadas, falhas e limitações.
5. Solicite aprovação explícita por ID para criar issues.
6. A aprovação do relatório não significa aprovação de todas as issues.
7. Em caso de ambiguidade, pare e pergunte.

Exemplo:
“Quais achados você aprova para criação de issues? Posso criar `BE-001` e `BE-004`. Nenhum outro item será criado sem sua autorização.”

### Fase 8 — Criar issues aprovadas com GitHub CLI

Só execute após aprovação explícita dos IDs e escopo.

#### Verificações

1. Confirme o repositório:
   `gh repo view --json nameWithOwner,url`
2. Verifique autenticação:
   `gh auth status`
3. Pesquise issues abertas e fechadas semelhantes:
   `gh issue list --repo OWNER/REPO --state all --search "termos relevantes" --json number,title,state,url`
4. Confira labels existentes:
   `gh label list --repo OWNER/REPO`
5. Se houver duplicata provável, não crie outra. Apresente a issue existente e peça autorização para vinculá-la ou atualizá-la.
6. Se repositório, autenticação ou permissões não estiverem corretos, pare e informe o bloqueio.
7. Não crie labels, milestones, branches, commits, PRs nem altere código sem autorização separada.

#### Conteúdo da issue

Cada issue deve ser autossuficiente e incluir:

- **Contexto:** módulo, fluxo e problema observado.
- **Evidência:** arquivo/linha, endpoint, teste ou configuração.
- **Impacto:** consequência técnica e para o produto.
- **Correção proposta:** escopo concreto e limitado.
- **Fora de escopo:** o que não deve ser alterado.
- **Critérios de aceite:** condições testáveis.
- **Testes esperados:** unitários, integração, contrato, segurança ou regressão conforme aplicável.
- **Migração/compatibilidade:** riscos de API, banco ou deploy, se houver.
- **Dependências e riscos.**
- **Prioridade, severidade, confiança e esforço.**
- **Referência:** ID `BE-XXX` e relatório PDF, sem dados sensíveis.

Não combine correções independentes numa issue. Se a divisão criar trabalho adicional além da aprovação, peça autorização.

#### Criar com `gh`

Exemplo de padrão (substitua os valores pelos reais; não execute placeholders):

```bash
gh issue create \
  --repo OWNER/REPO \
  --title "Validar autorização no endpoint de recursos" \
  --body-file /caminho/temporario/issue-body.md \
  --label "bug"
```

- Use `--body-file` com um arquivo temporário fora do repositório, quando possível.
- Use apenas labels existentes.
- Crie somente IDs aprovados.
- Pesquise duplicatas antes de cada criação.
- Se a criação falhar, investigue antes de repetir para evitar duplicação.
- Confirme número e URL na saída antes de declarar sucesso.
- Se a aprovação mudar, interrompa as criações restantes.

#### Segurança e idempotência

- Nunca inclua segredos, dados pessoais ou payloads sensíveis no corpo da issue.
- Não edite ou feche issues existentes sem autorização específica.
- Não execute correções, migrações, deploys ou mudanças no ambiente como parte da criação de issues.
- Registre ID, título, número, URL e resultado de cada operação.

## Formato da resposta final

Após a auditoria:
- Caminho real do PDF.
- Escopo e limitações.
- Principais achados e suas classificações.
- Testes/comandos executados e resultados.
- Hipóteses que precisam de validação.
- Solicitação de aprovação por IDs.

Após criar issues:
- Repositório confirmado.
- Issues criadas com número e URL.
- Duplicatas não recriadas.
- Falhas e itens pendentes.
- Confirmação de que nenhum código ou ambiente foi alterado.

## Checklist de qualidade

- [ ] O contexto e os fluxos de negócio foram considerados.
- [ ] As verificações executadas são relatadas honestamente.
- [ ] Fatos, defeitos, riscos e hipóteses estão separados.
- [ ] Segurança, dados, contratos, testes e operação foram avaliados quando aplicáveis.
- [ ] Cada achado contém localização, impacto, recomendação e critério de aceite.
- [ ] Severidade, prioridade, confiança e esforço não foram confundidos.
- [ ] Não foram recomendadas tecnologias sem justificativa.
- [ ] O PDF foi criado e validado de verdade.
- [ ] Nenhuma issue foi criada antes da aprovação específica.
- [ ] Duplicatas foram pesquisadas.
- [ ] Apenas IDs aprovados foram processados e resultados reais foram reportados.
