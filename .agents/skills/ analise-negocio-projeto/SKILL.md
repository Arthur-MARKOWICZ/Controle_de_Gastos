---
name: analise-negocio-projeto
description: Analisa um repositório de software sob a perspectiva de produto e negócio, identifica melhorias sustentadas por evidências, gera um relatório PDF priorizado e, somente após aprovação explícita do usuário, cria issues aprovadas no GitHub usando gh CLI. Use quando pedirem auditoria de produto, oportunidades de negócio, melhorias de roadmap ou conversão de recomendações aprovadas em issues.
---

# Skill: Análise de Negócio e Melhorias do Projeto

## Objetivo

Atuar como analista crítico de produto, negócio e software. Investigar o projeto existente, compreender seu propósito e estágio, encontrar oportunidades de melhoria e produzir um relatório PDF que ajude o responsável a decidir o que fazer.

**Regra inegociável:** análise e geração do relatório não autorizam mudanças no código, criação de branches, commits, pull requests ou issues. Criar issues no GitHub somente depois de o usuário aprovar explicitamente as recomendações específicas que deseja transformar em issues.

## Princípios

- **Evidência antes de opinião:** vincule observações a arquivos, trechos, testes, documentação, comportamento demonstrável ou fontes externas identificadas.
- **Não invente contexto:** diferencie fatos observados, hipóteses e perguntas em aberto. Não afirme que clientes querem algo sem evidência.
- **Negócio primeiro, tecnologia como meio:** priorize valor para usuários e negócio; não transforme toda dívida técnica em oportunidade comercial.
- **Crítica honesta:** procure riscos, custos, complexidade, dependências, efeitos colaterais e razões para não implementar.
- **Incrementalidade:** prefira experimentos pequenos e reversíveis quando a incerteza for alta.
- **Sem promessas falsas:** estimativas de receita, conversão ou retenção são hipóteses, não previsões, salvo quando houver dados que as sustentem.
- **Menor privilégio:** não exiba, copie ou inclua no relatório segredos, tokens, chaves, credenciais ou dados pessoais desnecessários.
- **Não altere o produto durante a análise:** leitura e execução de verificações seguras são permitidas; não modifique arquivos do projeto nem instale dependências sem autorização.
- **Não duplique trabalho:** consulte issues existentes antes de recomendar ou criar novas.

## Fluxo de trabalho obrigatório

### Fase 0 — Confirmar o contexto e o estado do repositório

1. Identifique a raiz do repositório e verifique `git status --short --branch`.
2. Preserve mudanças locais do usuário. Nunca descarte, formate ou sobrescreva alterações existentes.
3. Leia primeiro os arquivos de contexto disponíveis, como `README`, documentação de produto, arquitetura, `CONTRIBUTING`, instruções de agentes (`AGENTS.md`, `CLAUDE.md` ou equivalentes), arquivos de build, testes e estrutura de diretórios.
4. Determine, com base no repositório, o que o produto faz, quem parece atender, qual problema resolve, estágio de maturidade e limitações conhecidas.
5. Se faltar informação essencial sobre público-alvo, modelo de negócio ou objetivo, faça perguntas curtas. Se for possível avançar, registre as lacunas e prossiga sem inventar respostas.
6. Se GitHub for relevante, verifique se `gh` está instalado e autenticado (`gh --version`, `gh auth status`) e determine o repositório correto. A falta de autenticação não impede a análise; apenas impede a criação de issues posteriormente.

Não execute comandos destrutivos. Não mostre conteúdo de `.env`, arquivos de credenciais, chaves privadas ou tokens. Evite comandos que possam enviar dados para serviços externos sem autorização.

### Fase 1 — Investigar o projeto

Inspecione seletivamente e de forma proporcional ao tamanho do repositório:

- Proposta de valor, público-alvo, jornada do usuário e funcionalidades existentes.
- Documentação, roadmap, decisões arquiteturais e limitações conhecidas.
- Estrutura do código, integrações, pontos de entrada e fluxos principais.
- Testes existentes, cobertura observável, falhas conhecidas e facilidade de validar mudanças.
- UX/acessibilidade, onboarding, mensagens de erro e fluxos incompletos, quando houver interface.
- Segurança, privacidade, confiabilidade, desempenho, observabilidade, manutenção e custos operacionais, quando pertinentes.
- Modelo de distribuição, monetização, retenção, aquisição, suporte e diferenciação, quando sustentados pelo contexto.
- Issues abertas, labels, milestones e pull requests relevantes, usando `gh` somente em modo de leitura nesta fase.

Use verificações seguras e existentes, por exemplo testes já configurados, linters ou análise estática. Não instale ferramentas nem altere dependências sem autorização. Informe quais verificações foram executadas e quais não foram possíveis.

Não faça uma auditoria genérica de todos os tópicos se alguns não se aplicarem ao projeto. Explique a relevância de cada área investigada.

### Fase 2 — Criar oportunidades acionáveis

Para cada oportunidade, documente:

1. **ID estável:** `OPP-001`, `OPP-002` etc.
2. **Título claro e orientado a resultado.**
3. **Categoria:** produto, UX, aquisição, ativação, retenção, monetização, operação/custo, segurança/privacidade, confiabilidade, desempenho, manutenção ou outra categoria justificada.
4. **Problema observado:** quem é afetado e qual consequência existe ou pode existir.
5. **Evidências:** arquivos/linhas, fluxo reproduzível, teste, issue ou fonte citada. Se não houver evidência suficiente, marcar explicitamente como hipótese.
6. **Recomendação:** o que fazer e por que essa opção é preferível às alternativas.
7. **Valor esperado:** benefício para usuário e/ou negócio, sem inventar métricas.
8. **Métrica de sucesso:** indicador observável e como medi-lo; inclua baseline necessária se desconhecida.
9. **Esforço:** XS, S, M, L ou XL, com justificativa breve. Não use precisão falsa em horas.
10. **Confiança:** alta, média ou baixa, explicando incertezas.
11. **Risco e trade-offs:** custos, manutenção, segurança, privacidade, complexidade e possíveis efeitos negativos.
12. **Dependências e pré-requisitos.**
13. **Primeiro passo/critério de aceite:** resultado verificável e escopo mínimo.
14. **Decisão sugerida:** implementar, validar com usuários, fazer experimento, investigar mais ou não fazer agora.

Evite recomendações vagas como “melhorar UX”, “adicionar IA” ou “otimizar performance”. Converta-as em ações concretas, testáveis e ligadas a um problema. Se a evidência for fraca, proponha validação antes de desenvolvimento.

### Fase 3 — Priorizar

Use uma priorização qualitativa baseada em:

- Impacto potencial para usuário/negócio: baixo, médio ou alto.
- Confiança nas evidências: baixa, média ou alta.
- Esforço e custo: XS–XL.
- Risco e reversibilidade.
- Urgência, dependências e alinhamento com o propósito do produto.

Agrupe as recomendações em:

- **P0 — urgente:** risco relevante, bloqueio ou problema crítico comprovado.
- **P1 — alto valor:** oportunidade prioritária, com boa justificativa.
- **P2 — valor moderado:** útil, mas pode esperar ou depende de validação.
- **P3 — exploratória:** hipótese ou melhoria de menor prioridade.

Não use a prioridade como substituto da explicação. Não classifique uma ideia como P0 apenas por parecer interessante. Diferencie severidade de risco, prioridade de produto e esforço.

Inclua também uma seção **“O que não recomendo fazer agora”**, com ideias tentadoras descartadas ou adiadas e a justificativa.

### Fase 4 — Gerar o relatório em PDF

Produza um PDF legível, profissional e em português brasileiro, salvo se o usuário pedir outro idioma.

Nome sugerido:
`relatorio-oportunidades-negocio-AAAA-MM-DD.pdf`

Salve em uma pasta de saída acordada com o usuário; se não houver preferência, use `reports/` na raiz do repositório, mas **não sobrescreva um relatório existente**. Se criar a pasta/arquivo dentro do repositório, informe que esse artefato foi adicionado. Não inclua o relatório em commit automaticamente.

Estrutura recomendada:

1. Capa: nome do projeto, data, versão/branch analisada e objetivo.
2. Resumo executivo: estado observado, 3–5 principais conclusões e decisões que precisam ser tomadas.
3. Escopo e metodologia: áreas inspecionadas, comandos/verificações executados, limitações e áreas não avaliadas.
4. Entendimento do produto: proposta de valor inferida, público aparente e estágio, separando fatos de hipóteses.
5. Matriz priorizada de oportunidades.
6. Detalhamento individual de cada oportunidade.
7. Riscos transversais e dependências.
8. O que não fazer agora.
9. Plano sugerido: próximos passos em fases, sem assumir aprovação.
10. Decisões solicitadas: lista de IDs para aprovar, rejeitar ou pedir revisão.
11. Apêndice: evidências e referências, com caminhos de arquivo e linhas quando disponíveis.

O resumo executivo deve ser compreensível por alguém de negócio, sem perder a rastreabilidade técnica. Use tabelas apenas quando forem legíveis no PDF; evite texto minúsculo e tabelas largas.

Para gerar o PDF:
- Prefira ferramentas já disponíveis no ambiente.
- Se usar Python, prefira `reportlab.platypus` para documentos textuais.
- Não instale dependências sem autorização. Se não houver ferramenta de PDF disponível, explique a limitação e ofereça o Markdown como alternativa; não finja que o PDF foi criado.
- Verifique se o arquivo existe, se não está vazio e, quando possível, valide se pode ser aberto e se contém as seções esperadas.
- Informe o caminho exato do artefato. Não declare sucesso se a validação falhar.

### Fase 5 — Apresentar o relatório e pedir aprovação

Depois de validar o PDF:

1. Apresente o caminho/link real do relatório quando o ambiente permitir.
2. Resuma as principais recomendações, riscos e incertezas.
3. Apresente uma lista numerada com o ID, título, prioridade e esforço de cada oportunidade.
4. Solicite uma decisão explícita por ID: **aprovar para issue**, **rejeitar**, **revisar** ou **investigar mais**.
5. Não interprete silêncio, pedido de análise, geração do PDF ou aprovação genérica do relatório como autorização para criar todas as issues.
6. Se a resposta for ambígua, peça confirmação antes de executar qualquer criação.

Exemplo de pergunta:
“Quais IDs você aprova para criação de issues no GitHub? Posso criar apenas OPP-001 e OPP-003, por exemplo. As demais ficarão sem alteração.”

### Fase 6 — Criar issues aprovadas com GitHub CLI

Só entre nesta fase depois de receber aprovação explícita e específica para os IDs. A aprovação vale apenas para os itens identificados e para o escopo descrito no relatório.

#### 6.1 Verificações antes de criar

1. Reconfirme o repositório com `gh repo view --json nameWithOwner,url`.
2. Verifique `gh auth status`.
3. Consulte issues abertas e fechadas relevantes para evitar duplicatas. Use pesquisa com termos do título e do problema, por exemplo:
   `gh issue list --repo OWNER/REPO --state all --search "termos relevantes" --json number,title,state,url`
4. Confira os labels e milestones existentes:
   `gh label list --repo OWNER/REPO`
   `gh milestone list --repo OWNER/REPO` (se disponível na versão instalada do CLI).
5. Revalide o escopo e os critérios de aceite de cada oportunidade aprovada.
6. Se o repositório estiver incorreto, a autenticação falhar, uma duplicata provável existir ou faltar permissão, pare e pergunte/relate o bloqueio. Não crie em outro repositório por suposição.
7. Não crie labels, milestones ou projetos automaticamente. Se um label desejado não existir, omita-o ou pergunte ao usuário.

#### 6.2 Conteúdo de cada issue

Cada issue deve ser autossuficiente, concisa e pronta para entrar no backlog. Use este formato:

- **Título:** verbo/resultado claro; não inclua o ID da oportunidade como prefixo obrigatório, a menos que o usuário prefira.
- **Contexto:** problema e público afetado.
- **Motivação/valor:** por que importa, sem promessas não comprovadas.
- **Evidências:** caminhos de arquivos, linhas, testes, documentação ou links pertinentes.
- **Escopo proposto:** o que está incluído.
- **Fora de escopo:** o que não deve ser feito nesta issue.
- **Critérios de aceite:** lista objetiva, verificável e testável.
- **Métrica de sucesso:** indicador e baseline necessária, se aplicável.
- **Dependências/riscos:** itens conhecidos.
- **Esforço/confiança:** estimativa qualitativa e incertezas.
- **Referência:** `OPP-XXX` e nome/caminho do relatório PDF, sem anexar segredos ou dados privados.

Não misture múltiplas melhorias independentes em uma única issue. Se uma oportunidade precisar ser dividida, explique a divisão e peça aprovação para as issues adicionais, a menos que o usuário tenha autorizado explicitamente a decomposição.

#### 6.3 Criar com `gh`

Use `gh issue create` para cada issue aprovada. Exemplo de padrão (substitua valores pelos reais; não execute literalmente os placeholders):

```bash
gh issue create \
  --repo OWNER/REPO \
  --title "Título objetivo da melhoria" \
  --body-file /caminho/para/issue-body.md \
  --label "enhancement"
```

- Use `--body-file` com um arquivo temporário fora do repositório quando possível; não use concatenação insegura de strings para conteúdo arbitrário.
- Só passe labels que existam no repositório.
- Não crie branches, commits, pull requests ou alterações de código como parte desta fase.
- Crie apenas as issues explicitamente aprovadas. Não use comandos em lote que incluam IDs não aprovados.
- Registre o resultado de cada criação (ID da oportunidade, título, número da issue e URL).
- Se uma criação falhar, pare para avaliar a causa. Não repita cegamente, pois a primeira chamada pode ter criado a issue mesmo que a resposta tenha falhado.
- Não declare uma issue criada sem URL/identificador confirmado pela saída do CLI.
- Ao terminar, informe issues criadas, itens ignorados por duplicidade, falhas e qualquer ação pendente.

#### 6.4 Idempotência e segurança

- Antes de cada criação, pesquise novamente uma possível duplicata pelo título e termos centrais.
- Se houver uma issue equivalente, não crie outra: apresente a issue existente e pergunte se deseja vinculá-la/atualizá-la. Não altere a issue existente sem autorização explícita.
- Nunca inclua tokens, segredos, dados pessoais ou logs sensíveis no título/corpo.
- Não use `gh issue close`, `gh issue edit`, exclusão ou atualização de labels/milestones sem autorização separada.
- Se a aprovação mudar durante o processo, interrompa as criações restantes.

## Formato da resposta final

Após a análise, informe:

- PDF gerado e caminho real.
- Escopo efetivamente analisado e limitações.
- 3–5 principais oportunidades com ID, prioridade e esforço.
- Hipóteses que exigem validação.
- Próxima ação solicitada: aprovar IDs específicos.

Após a fase de criação de issues, informe:

- Repositório confirmado.
- Issues criadas com número e URL.
- Duplicatas encontradas e não recriadas.
- Falhas e itens não processados.
- Confirmação de que nenhuma alteração de código foi feita.

## Checklist de qualidade

Antes de entregar o relatório, confirme:

- [ ] As recomendações estão ligadas ao propósito real do projeto.
- [ ] Fatos, hipóteses e perguntas abertas estão claramente separados.
- [ ] Cada oportunidade tem evidência ou é identificada como hipótese.
- [ ] Há impacto esperado, esforço, confiança, risco e critério de sucesso.
- [ ] A priorização tem justificativa.
- [ ] Há recomendações que foram adiadas/rejeitadas, quando pertinente.
- [ ] O PDF foi realmente criado e validado.
- [ ] Nenhuma issue foi criada antes da aprovação específica.
- [ ] Após aprovação, apenas os IDs autorizados foram processados.
- [ ] Issues existentes foram pesquisadas para evitar duplicatas.
- [ ] O resultado final informa URLs reais e falhas honestamente.
