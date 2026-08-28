# Checklist operacional

Use somente as seções pertinentes ao artefato em revisão. Registre evidência observável; política escrita sem implementação não prova eficácia.

## Registro por operação

Crie uma linha por combinação de finalidade e hipótese legal:

| Campo | Evidência esperada |
|---|---|
| Finalidade | Específica, explícita e comunicada |
| Titulares e dados | Categorias, origem, inferências e sensibilidade |
| Operações | Ciclo completo, da coleta ao descarte |
| Agentes | Controlador, operador, suboperador, encarregado e dono interno |
| Hipótese legal | Artigo, requisito e evidência por finalidade |
| Necessidade | Por que cada campo é indispensável; alternativa menos invasiva |
| Transparência | Aviso, momento da informação e linguagem adequada |
| Compartilhamento | Destinatário, finalidade, instruções, contrato e auditoria |
| Internacional | País, hipótese legal e mecanismo do art. 33 |
| Retenção | Evento inicial, prazo, exceção legal e descarte verificável |
| Direitos | Canal, autenticação proporcional, prazos e propagação a terceiros |
| Segurança | Controles preventivos, detectivos, resposta e evidência de teste |
| Risco | Dano possível, probabilidade, impacto, salvaguarda e risco residual |
| Responsabilidade | Dono, aprovador, data de revisão e documentos relacionados |

## Produto, código e infraestrutura

- Torne campos opcionais sempre que possível e bloqueie a coleta de campos sem finalidade comprovada.
- Desabilite telemetria, gravação de sessão, rastreamento e uso para treinamento até haver análise e configuração explícita.
- Separe dados pessoais de identificadores operacionais quando possível.
- Aplique autorização no servidor e menor privilégio; não confie em controles apenas na interface.
- Não registre corpo de requisição, prompt, resposta de modelo, token ou cabeçalho que possa carregar dado pessoal ou segredo sem necessidade documentada.
- Defina retenção e exclusão também para logs, backups, filas, caches, vetores, embeddings, arquivos temporários e ambientes de desenvolvimento.
- Garanta que correção, bloqueio e eliminação se propaguem a índices, réplicas e terceiros, respeitadas exceções legais registradas.
- Use dados sintéticos em desenvolvimento e teste. Se dados reais forem inevitáveis, reduza o conjunto, isole o ambiente, limite acesso e documente necessidade e descarte.
- Teste acesso indevido entre contas, exportação, enumeração de identificadores, segredos, retenção e exclusão, além da função principal.
- Mantenha inventário de modelos, conjuntos de dados, prompts de sistema, fornecedores, regiões de processamento e versões relevantes.

## Sistemas de IA

- Separe claramente dado fornecido, dado observado, inferência, perfil, rótulo e decisão.
- Não use interações para ajuste, treinamento, avaliação humana ou criação de dataset por finalidade implícita.
- Minimize prompts e contexto recuperado; aplique filtros antes do envio ao modelo e antes de exibir a resposta.
- Avalie memorização, extração, vazamento entre usuários, reidentificação, inferência sensível e prompt injection que exponha dados.
- Não prometa anonimização apenas por remover nome ou CPF. Avalie identificadores indiretos, singularidade, ligação com outras bases e meios razoáveis de reversão.
- Para perfilamento ou decisão automatizada relevante, documente dados e critérios determinantes, qualidade, testes de vieses, grupos afetados, explicação inteligível, contestação, revisão e supervisão humana efetiva.
- Supervisão humana não é simbólica: o revisor precisa de autoridade, informação, tempo e capacidade para alterar o resultado.

## Interface, aviso e consentimento

- Informe identidade e contato do controlador, finalidades, forma e duração, compartilhamentos, responsabilidades e direitos em linguagem clara e acessível.
- Mostre informação no momento relevante; não esconda finalidade inesperada apenas em política extensa.
- Separe consentimentos por finalidade e registre versão do texto, data, contexto e manifestação.
- Recusar ou revogar deve ser tão simples quanto consentir, sem consequência indevida.
- Não use caixas pré-marcadas, autorização genérica, linguagem enganosa ou elementos que induzam a escolha.
- Para crianças, adapte conteúdo, interação e verificação à idade e ao risco, priorizando o melhor interesse.

## Fornecedores e contratos

- Confirme o papel real de cada parte por operação.
- Registre objeto, finalidade, categorias, titulares, duração, instruções, confidencialidade, segurança, suboperadores, cooperação com direitos e incidentes, auditoria, devolução e exclusão.
- Verifique onde ocorre acesso, suporte, armazenamento, backup e inferência; todos podem criar fluxo internacional.
- Não aceite mudança unilateral de finalidade, uso próprio para treinamento ou suboperador sem transparência e governança compatíveis.
- Prove operacionalmente exclusão, exportação, isolamento entre clientes e resposta a incidente.

## Direitos do titular

- Identifique o pedido e o controlador correto sem exigir narrativa jurídica do titular.
- Autentique de forma proporcional ao risco do pedido; ofereça alternativa quando a verificação original não for possível.
- Pesquise todos os sistemas e terceiros relevantes, preservando dados de outras pessoas.
- Registre recebimento, identidade verificada, decisão, fundamento, sistemas consultados, resposta e propagação.
- Se não for possível atender imediatamente, explique as razões de fato ou de direito; não silencie nem encerre automaticamente.

## Critério de saída

Uma revisão está pronta para decisão humana quando cada finalidade tem hipótese e evidência, os dados foram minimizados, os riscos e direitos possuem controles testáveis, as transferências e retenção estão documentadas e toda lacuna tem responsável e prazo. Isso não equivale a certificação jurídica.
