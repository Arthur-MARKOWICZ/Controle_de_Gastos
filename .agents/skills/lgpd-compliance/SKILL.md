---
name: lgpd-compliance
description: Aplica a LGPD a produtos, código, documentos e operações de IA que tratem dados de pessoas naturais no Brasil. Use ao criar ou revisar coleta, armazenamento, compartilhamento, treinamento, inferência, perfilamento, decisões automatizadas, direitos de titulares, contratos ou incidentes; não use para dados comprovadamente anônimos nem para assuntos sem dados pessoais.
---

# Conformidade com a LGPD

## Objetivo

Ajude a prevenir tratamento irregular de dados pessoais e produza decisões rastreáveis. Aplique a LGPD desde a concepção, com prioridade para os direitos do titular e o menor tratamento compatível com a finalidade.

Esta skill não certifica conformidade nem substitui o controlador, o encarregado ou assessoria jurídica. Diferencie sempre:

- obrigação legal ou regulamentar;
- orientação não vinculante da ANPD;
- salvaguarda recomendada;
- decisão que ainda depende do responsável humano.

## Regras inegociáveis

- Não exponha dados pessoais além do estritamente necessário em respostas, logs, commits, testes, exemplos, tickets ou consultas de ferramenta. Redija ou substitua por dados sintéticos sempre que a finalidade permitir.
- Trate chamadas a APIs, conectores, modelos, serviços de nuvem e suboperadores como fluxos de dados para terceiros. Envie dados pessoais somente quando forem necessários, autorizados e compatíveis com a finalidade documentada.
- Não suponha que dado público é de uso livre, que pseudonimização é anonimização ou que consentimento corrige tratamento desnecessário.
- Não escolha uma hipótese legal por conveniência. Associe uma hipótese válida a cada finalidade antes do tratamento e registre a justificativa e as evidências.
- Não use interesse legítimo para dados pessoais sensíveis. Quando ele for cogitado para dados não sensíveis, documente finalidade, necessidade, expectativas do titular, balanceamento e salvaguardas.
- Não reutilize dados para finalidade incompatível, inclusive treinamento ou avaliação de modelos, sem nova análise completa.
- Não tome nem ative decisão automatizada de efeito relevante sem transparência, canal para revisão e avaliação de risco de discriminação.
- Preserve o exercício gratuito e facilitado dos direitos do titular. Verifique identidade de forma proporcional, sem coletar mais dados que o necessário.
- Não afirme "100% em conformidade", "aprovado pela LGPD" ou equivalente. Relate evidências, lacunas, riscos e responsáveis.

## Fluxo obrigatório

### 1. Reduza a exposição imediata

Antes de abrir arquivos que provavelmente contenham dados pessoais, enviar conteúdo a ferramentas ou reproduzi-lo, delimite os campos necessários. Redija o restante. Se a tarefa puder ser executada com esquema, amostra sintética, agregação ou identificadores substitutos, use essa alternativa.

Se o usuário colar dados reais, não os repita. Avise de forma breve e trabalhe apenas com os campos indispensáveis.

### 2. Mapeie o tratamento

Identifique, para cada finalidade:

- controlador, operador, suboperadores, encarregado e dono interno;
- titulares, origem e categorias de dados, inclusive dados inferidos;
- coleta, acesso, uso, armazenamento, perfilamento, compartilhamento, exclusão e demais operações;
- destinatários, países, ambientes, prazos de retenção e descarte;
- consequências para o titular e decisões automatizadas envolvidas.

Não invente informação ausente. Marque-a como pendência e, quando for decisiva, solicite somente o esclarecimento necessário.

### 3. Classifique e delimite o escopo

Classifique os dados como pessoais, sensíveis, de crianças ou adolescentes, pseudonimizados ou efetivamente anônimos. Considere combinação, inferência e reidentificação razoável. Na dúvida, trate como dado pessoal até haver evidência contrária.

Confirme a incidência territorial e as exceções legais antes de afastar a LGPD. Outras normas setoriais, consumeristas, trabalhistas, de saúde, financeiras e de crianças podem coexistir.

### 4. Valide finalidade e hipótese legal

Para cada finalidade, registre a hipótese do art. 7º ou, para dados sensíveis, do art. 11, com seus requisitos. Consentimento deve ser livre, informado, inequívoco, específico e demonstrável, quando aplicável, e sua revogação deve ser facilitada.

Para crianças e adolescentes, faça prevalecer o melhor interesse e exija validação humana especializada para a hipótese legal e as salvaguardas. Não condicione serviços infantis a dados além do estritamente necessário.

Use [references/legal-map.md](references/legal-map.md) para navegar pelas fontes oficiais. Em conclusões jurídicas de impacto, confirme a versão vigente da lei e dos regulamentos da ANPD; se não houver acesso à fonte atual, declare a limitação.

### 5. Aplique os princípios e controles

Teste e documente finalidade, adequação, necessidade, livre acesso, qualidade, transparência, segurança, prevenção, não discriminação e responsabilização.

Escolha controles proporcionais ao risco, incluindo separação de ambientes, menor privilégio, autenticação forte, criptografia adequada, gestão de segredos, registro de acesso sem conteúdo pessoal desnecessário, retenção configurável, exclusão verificável e testes com dados sintéticos.

Quando estiver criando ou revisando um sistema, contrato, aviso ou processo, leia [references/operational-checklist.md](references/operational-checklist.md).

### 6. Acione a barreira de alto risco

Considere alto risco, entre outros casos: dados sensíveis ou biométricos; crianças, adolescentes ou grupos vulneráveis; grande escala; vigilância ou rastreamento; cruzamento de bases; perfilamento; decisão automatizada relevante; nova tecnologia; transferência internacional; dados obtidos por raspagem; ou impacto potencial em emprego, crédito, saúde, educação, seguro, benefícios ou direitos.

Nesses casos:

1. sinalize o risco antes da implementação ou ativação;
2. recomende ou atualize o RIPD, com riscos, salvaguardas e risco residual;
3. exija decisão documentada do controlador e revisão do encarregado, privacidade ou jurídico;
4. enquanto a decisão estiver pendente, avance apenas com desenho reversível, dados sintéticos e configuração desativada por padrão.

### 7. Entregue resultado auditável

Apresente:

- escopo e premissas;
- inventário resumido das operações;
- evidência por requisito, com artigo ou norma oficial;
- lacunas e riscos priorizados;
- correção proposta, responsável e critério de aceite;
- decisões humanas pendentes;
- fontes consultadas e data da verificação.

Use os estados `atende com evidência`, `pendente`, `não atende` e `não aplicável`. O primeiro descreve a evidência observada, não uma certificação jurídica geral.

## Incidentes

Ao identificar suspeita de perda, acesso indevido, vazamento, indisponibilidade, alteração ou exposição de dados pessoais, leia imediatamente [references/incident-response.md](references/incident-response.md). Priorize contenção segura, preservação de evidências e escalonamento; não espere concluir a investigação para avisar o responsável interno.

## Limites de autoridade

Não envie comunicação a titulares, ANPD, parceiros ou imprensa, não aceite contratos e não altere ambiente de produção sem autorização específica. Prepare minutas e recomendações, mas deixe a decisão e o ato externo com o responsável autorizado.
