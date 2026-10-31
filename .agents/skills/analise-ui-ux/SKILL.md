---
name: analise-ui-ux
description: Audita a interface e a experiência de uso de um projeto de software com base no código e nas telas disponíveis, identifica problemas e oportunidades de UI/UX com evidências, gera relatório PDF priorizado e, somente após aprovação explícita, cria issues aprovadas no GitHub usando gh CLI. Use para avaliações heurísticas, auditorias de usabilidade, acessibilidade, consistência visual, fluxos de usuário e melhorias de interface.
---

# Skill: Análise de UI/UX

## Objetivo

Avaliar criticamente a interface e a experiência do usuário do projeto existente. Identificar fricções reais ou prováveis, explicar seu impacto e propor melhorias concretas e verificáveis. Produzir um relatório PDF que permita ao responsável decidir quais melhorias implementar.

**Regra inegociável:** analisar e gerar o relatório não autoriza alterar o código, refatorar a interface, instalar dependências, criar branches, commits, pull requests ou issues. Criar issues no GitHub somente após aprovação explícita e específica do usuário para os IDs escolhidos.

## Princípios

- **Avalie a experiência, não apenas a aparência:** investigue se usuários conseguem concluir tarefas, entender o estado do sistema, recuperar-se de erros e encontrar o que precisam.
- **Evidências antes de preferências pessoais:** aponte páginas, componentes, rotas, estados, screenshots ou comportamentos observados. Diferencie problemas comprovados de hipóteses que exigem teste com usuários.
- **Contexto primeiro:** compreenda o tipo de produto, público, dispositivos-alvo, tarefas principais, design system e restrições técnicas antes de julgar decisões visuais.
- **Não invente pesquisas:** não afirme que usuários estão confusos ou abandonam fluxos sem dados. Use expressões como “risco de confusão” ou “hipótese a validar” quando não houver evidência direta.
- **Não redesenhe por gosto:** justifique cada recomendação com heurísticas de usabilidade, acessibilidade, consistência, necessidades da tarefa ou objetivo do produto.
- **Considere o sistema completo:** avalie estados de carregamento, vazio, sucesso, erro, validação, permissões, responsividade e fluxos de retorno, não apenas a tela ideal.
- **Acessibilidade é parte da qualidade:** examine teclado, foco, semântica, contraste, nomes acessíveis, mensagens de erro e uso sem depender exclusivamente de cor ou gestos.
- **Respeite o design existente:** prefira melhorias consistentes com os padrões atuais; proponha novo padrão apenas quando houver justificativa.
- **Não altere durante a auditoria:** preserve mudanças locais e execute apenas verificações seguras. Não instale pacotes, não gere commits e não modifique arquivos sem autorização.
- **Privacidade:** não inclua tokens, credenciais, dados pessoais, dados reais de clientes ou informações sensíveis em screenshots, logs, relatório ou issues.
- **Aprovação humana:** nenhuma issue deve ser criada antes da aprovação explícita dos IDs correspondentes.

## Fluxo de trabalho obrigatório

### Fase 0 — Descobrir o contexto e preservar o repositório

1. Identifique a raiz do projeto e consulte `git status --short --branch`.
2. Preserve alterações locais; não descarte nem sobrescreva arquivos.
3. Leia instruções locais (`AGENTS.md`, `CLAUDE.md` ou equivalentes), README, documentação de produto, arquitetura, setup e testes.
4. Identifique as tecnologias de UI, framework, roteamento, bibliotecas de componentes, estilos, design tokens e testes de interface existentes.
5. Mapeie rotas/telas e os fluxos principais com base na implementação e documentação.
6. Determine o que é conhecido sobre usuários, tarefas prioritárias, plataformas, navegadores e requisitos de acessibilidade.
7. Se faltar contexto que mude substancialmente a avaliação, faça perguntas curtas. Caso seja possível avançar, registre as limitações em vez de inventar respostas.
8. Se GitHub for relevante, verifique `gh --version` e `gh auth status`, mas use o CLI apenas para leitura nesta fase.

Não leia nem exponha `.env`, tokens, credenciais ou chaves privadas. Não execute comandos destrutivos, não envie dados para serviços externos sem autorização e não instale dependências sem aprovação.

### Fase 1 — Inspecionar a interface e os fluxos

Use os meios disponíveis no ambiente, de forma proporcional ao projeto:

- Inspeção do código de páginas, componentes, estilos, tokens e navegação.
- Execução local somente se o projeto já puder ser executado com ferramentas e dependências disponíveis, sem instalar ou alterar nada.
- Browser, screenshots ou ferramenta de automação visual, se disponíveis e autorizados.
- Testes de UI, testes de acessibilidade e verificações já configuradas.
- Inspeção responsiva em larguras representativas quando houver browser/visualização disponível.
- Revisão de componentes reutilizados e inconsistências entre telas.

**Se não for possível renderizar a interface**, não declare ter feito uma inspeção visual. Faça uma auditoria estática do código e rotas, identifique o que não pôde ser observado e reduza a confiança das conclusões visuais. Solicite screenshots, acesso ao ambiente local ou instruções para executar o projeto se isso for essencial.

Mapeie, quando aplicável:

- Entrada/onboarding e primeira utilização.
- Navegação principal e descoberta de funcionalidades.
- Fluxos essenciais de ponta a ponta.
- Formulários, validação e mensagens de erro.
- Busca, filtros, ordenação, paginação e ações em lote.
- Estados vazios, carregamento, sucesso, erro e indisponibilidade.
- Confirmações, cancelamento, desfazer e recuperação de ações.
- Preferências, perfil, autenticação e permissões.
- Experiência em desktop, tablet e mobile.
- Consistência visual entre páginas e componentes.
- Acessibilidade e uso por teclado/leitores de tela.
- Conteúdo, microcopy, nomenclatura e clareza das ações.

Não marque um item como defeito se a funcionalidade ou contexto não estiver presente. Indique “não aplicável” ou “não foi possível avaliar”.

### Fase 2 — Avaliar com critérios explícitos

Use as heurísticas de Nielsen como lente, não como checklist cego:

1. Visibilidade do estado do sistema.
2. Correspondência entre sistema e mundo real.
3. Controle e liberdade do usuário.
4. Consistência e padrões.
5. Prevenção de erros.
6. Reconhecimento em vez de memorização.
7. Flexibilidade e eficiência.
8. Design minimalista com hierarquia adequada.
9. Reconhecimento, diagnóstico e recuperação de erros.
10. Ajuda e documentação quando necessárias.

Complemente, conforme aplicável, com:

- **Usabilidade:** conclusão da tarefa, esforço, clareza, número de passos e recuperação de erros.
- **Arquitetura da informação:** hierarquia, agrupamento, navegação, rótulos e encontrabilidade.
- **Design visual:** hierarquia tipográfica, espaçamento, alinhamento, contraste, densidade, legibilidade e consistência.
- **Design de interação:** affordances, feedback, foco, estados de controles, prevenção de ações acidentais e confirmação proporcional ao risco.
- **Acessibilidade:** semântica HTML, labels, nomes acessíveis, ordem de foco, foco visível, contraste, teclado, mensagens anunciadas, zoom/reflow e alvos de interação.
- **Responsividade:** overflow, conteúdo cortado, navegação, formulários, tabelas, modais e ações em telas estreitas.
- **Conteúdo e microcopy:** linguagem clara, rótulos específicos, instruções úteis e mensagens acionáveis.
- **Confiança e segurança:** transparência sobre ações, permissões, exclusão, privacidade e consequências.
- **Desempenho percebido:** feedback durante espera, skeleton/loading, prevenção de bloqueios e estados de falha.
- **Consistência de produto:** padrões de navegação, botões, formulários, feedback e componentes reutilizáveis.

Quando fizer sentido, avalie com WCAG 2.2 AA como referência. Não declare conformidade WCAG apenas com inspeção visual ou automatizada parcial. Diferencie verificações automáticas, inspeção manual e testes reais com tecnologias assistivas.

### Fase 3 — Registrar cada achado

Para cada achado, registre:

1. **ID estável:** `UX-001`, `UX-002` etc.
2. **Título:** problema concreto, não uma solução genérica.
3. **Local:** rota, tela, componente e arquivo/linha, quando disponível.
4. **Tipo:** usabilidade, navegação, visual, acessibilidade, responsividade, conteúdo, feedback, confiança ou outro.
5. **Evidência:** comportamento observado, código, screenshot, teste ou reprodução. Informe se a avaliação foi estática ou visual.
6. **Problema e tarefa afetada:** o que pode impedir, atrasar ou confundir o usuário.
7. **Impacto:** consequência para o usuário e para o objetivo do produto.
8. **Recomendação:** alteração específica e justificada.
9. **Critérios de aceite:** condições objetivas para considerar o problema resolvido.
10. **Severidade do problema:** crítica, alta, média ou baixa.
11. **Prioridade de execução:** P0, P1, P2 ou P3, considerando também risco, alcance, confiança e dependências.
12. **Confiança:** alta, média ou baixa, com motivo.
13. **Esforço:** XS, S, M, L ou XL, com justificativa breve.
14. **Validação proposta:** teste manual, teste automatizado, teste de acessibilidade, teste de usabilidade ou métrica.
15. **Dependências e trade-offs:** design system, backend, analytics, conteúdo, arquitetura ou risco de regressão.

Não invente métricas de abandono, conversão, tempo de tarefa ou satisfação. Se não houver analytics ou pesquisa, proponha como coletar evidências. Não confunda severidade (gravidade do problema) com prioridade (ordem de execução).

Evite recomendações vagas como “deixar mais moderno”, “melhorar a experiência” ou “usar cores melhores”. Explique o que mudar, onde, por que e como validar.

### Fase 4 — Recomendar melhorias sem excesso de redesign

Agrupe recomendações em:

- **Correções de fricção:** remover obstáculos de tarefas existentes.
- **Acessibilidade e inclusão:** resolver barreiras de uso.
- **Consistência e sistema visual:** reduzir variações injustificadas e melhorar reutilização.
- **Clareza e conteúdo:** melhorar rótulos, instruções e mensagens.
- **Fluxos e arquitetura da informação:** simplificar caminhos e facilitar descoberta.
- **Melhorias exploratórias:** hipóteses que exigem pesquisa ou teste.

Para cada proposta, prefira a menor alteração capaz de resolver o problema. Se a causa ainda não estiver comprovada, proponha um experimento ou teste de usabilidade antes de uma reformulação ampla.

Inclua uma seção **“O que não recomendo mudar agora”** para explicar quando uma alteração estética é subjetiva, tem baixo impacto, depende de dados ou cria risco desnecessário.

### Fase 5 — Priorizar

Considere:

- Gravidade e frequência observada ou provável.
- Quantidade e importância das tarefas/usuários afetados.
- Impacto em conclusão da tarefa, compreensão, confiança e acessibilidade.
- Confiança nas evidências.
- Esforço, dependências, risco de regressão e reversibilidade.

Use a escala:

- **P0 — crítico:** bloqueio de tarefa central, barreira grave de acesso ou risco importante comprovado.
- **P1 — alto:** fricção relevante em fluxo principal, erro recorrente demonstrado ou barreira importante.
- **P2 — moderado:** melhoria útil de clareza, consistência ou eficiência.
- **P3 — exploratório:** preferência visual, hipótese incerta ou melhoria de menor impacto.

Não atribua P0/P1 por gosto pessoal. Explicite incertezas e evite sugerir que uma heurística isolada prova impacto real.

### Fase 6 — Gerar relatório PDF

Produza um PDF profissional, legível e em português brasileiro, salvo se o usuário pedir outro idioma.

Nome sugerido:
`relatorio-ui-ux-AAAA-MM-DD.pdf`

Salve em `reports/` se essa pasta for adequada e não sobrescreva arquivos existentes. Não faça commit automaticamente. Se a criação dentro do repositório não for desejada ou segura, salve em uma pasta de saída acordada.

Estrutura recomendada:

1. Capa: projeto, data, branch/versão, ambiente e tipo de inspeção.
2. Resumo executivo: estado geral, principais riscos e decisões recomendadas.
3. Escopo e metodologia: telas/rotas examinadas, dispositivos/tamanhos avaliados, métodos e limitações.
4. Mapa das jornadas/tarefas analisadas.
5. Matriz priorizada dos achados.
6. Detalhamento dos achados, com evidências e recomendações.
7. Acessibilidade: itens avaliados, limitações e testes necessários.
8. Consistência visual e padrões reutilizáveis.
9. Plano de melhoria em etapas.
10. O que não mudar agora.
11. Decisões solicitadas: IDs para aprovar, rejeitar, revisar ou investigar.
12. Apêndice: rotas, arquivos, screenshots e verificações executadas.

Inclua screenshots somente quando disponíveis e seguros, com legenda e explicação. Não gere imagens que pareçam screenshots reais nem apresente mockups conceituais como estado atual do produto. Se sugerir um redesign conceitual, rotule-o claramente como proposta, não como evidência.

Para gerar o PDF:
- Prefira ferramentas já disponíveis.
- Se usar Python, prefira `reportlab.platypus`.
- Não instale dependências sem autorização.
- Se não houver ferramenta disponível, ofereça Markdown como alternativa e informe que o PDF não foi criado.
- Valide existência, tamanho e abertura do arquivo quando possível, e confira as seções esperadas.
- Informe o caminho real do arquivo; não declare sucesso se a validação falhar.

### Fase 7 — Apresentar e solicitar aprovação

Após validar o PDF:

1. Informe o caminho/link real do relatório quando o ambiente permitir.
2. Resuma os principais achados, separando observações comprovadas de hipóteses.
3. Liste os IDs com título, severidade, prioridade e esforço.
4. Solicite aprovação específica por ID para criar issues.
5. Não interprete aprovação do relatório como autorização para criar todas as issues.
6. Se o usuário aprovar apenas alguns itens, processe somente esses itens.
7. Se a resposta for ambígua, pergunte antes de executar.

Exemplo:
“Quais achados você aprova para virar issue? Posso criar `UX-001` e `UX-004`. Os demais não serão alterados.”

### Fase 8 — Criar issues aprovadas usando GitHub CLI

Só execute após aprovação explícita dos IDs e do escopo. Não altere o código como parte desta etapa.

#### Verificações

1. Confirme o repositório:
   `gh repo view --json nameWithOwner,url`
2. Verifique autenticação:
   `gh auth status`
3. Pesquise issues abertas e fechadas semelhantes:
   `gh issue list --repo OWNER/REPO --state all --search "termos relevantes" --json number,title,state,url`
4. Confira labels disponíveis:
   `gh label list --repo OWNER/REPO`
5. Não crie issues em repositório presumido ou incorreto.
6. Se houver duplicata provável, não crie outra; apresente a issue existente e pergunte se o usuário quer vinculá-la ou atualizá-la.
7. Não crie labels, milestones, branches, commits ou PRs sem autorização separada.

#### Formato da issue

Cada issue deve conter:

- **Contexto e localização:** tela/rota, componente e caminho de arquivo.
- **Problema observado:** evidência e tarefa afetada.
- **Impacto:** efeito no usuário, acessibilidade ou objetivo do produto.
- **Melhoria proposta:** escopo concreto.
- **Fora de escopo:** mudanças que não estão autorizadas.
- **Critérios de aceite:** lista verificável.
- **Validação:** testes manuais/automatizados, acessibilidade ou usabilidade.
- **Dependências e riscos:** itens relevantes.
- **Prioridade e esforço:** com incertezas identificadas.
- **Referência:** ID `UX-XXX` e caminho/nome do relatório, sem dados sensíveis.

Se uma recomendação for grande, divida em issues menores apenas se a divisão estiver coberta pela aprovação do usuário. Caso contrário, proponha a divisão e peça aprovação.

#### Criar com `gh`

Exemplo de padrão (substitua os valores pelos reais; não execute placeholders):

```bash
gh issue create \
  --repo OWNER/REPO \
  --title "Corrigir feedback de validação no formulário de cadastro" \
  --body-file /caminho/temporario/issue-body.md \
  --label "enhancement"
```

- Prefira `--body-file` e conteúdo temporário fora do repositório.
- Use somente labels existentes.
- Crie apenas IDs aprovados.
- Antes de cada criação, pesquise novamente possíveis duplicatas.
- Se a operação falhar, avalie o resultado antes de tentar novamente para evitar duplicação.
- Só declare sucesso quando a saída confirmar número e URL da issue.
- Interrompa se a aprovação mudar durante o processo.

#### Segurança e idempotência

- Não inclua tokens, dados pessoais, credenciais ou screenshots com informações reais.
- Não edite ou feche issues existentes sem autorização específica.
- Não crie branches, commits, PRs nem modifique código nesta etapa.
- Registre ID, título, número, URL e resultado de cada operação.

## Formato da resposta final

Após a auditoria:

- PDF e caminho real.
- Escopo e limitações.
- Principais achados com IDs, severidade, prioridade e esforço.
- Quais achados são hipóteses.
- Decisão solicitada: quais IDs aprovar para issues.

Após a criação:

- Repositório confirmado.
- Issues criadas com número e URL.
- Duplicatas que não foram recriadas.
- Falhas e itens pendentes.
- Confirmação de que nenhum código foi alterado.

## Checklist de qualidade

- [ ] Contexto do produto e tarefas principais foram considerados.
- [ ] Foi informado se a interface foi renderizada ou avaliada apenas estaticamente.
- [ ] Achados têm evidência/localização ou estão marcados como hipóteses.
- [ ] Usabilidade, feedback, estados, responsividade e acessibilidade foram avaliados quando aplicáveis.
- [ ] Severidade, prioridade, confiança e esforço não foram confundidos.
- [ ] Recomendações são concretas e testáveis, sem redesign arbitrário.
- [ ] O relatório PDF foi criado e validado de verdade.
- [ ] Nenhuma issue foi criada antes da aprovação específica.
- [ ] Duplicatas foram pesquisadas antes de criar issues.
- [ ] Apenas IDs aprovados foram processados e resultados reais foram reportados.
