---
name: web-design-moderno
description: Cria e refina interfaces web responsivas com visual moderno, simples e acessível. Use para páginas, landing pages, dashboards, portais e aplicações no navegador; não use como guia principal para apps móveis nativos.
---

# Web Design Moderno

Crie uma experiência clara e distinta, não apenas uma coleção de componentes bonitos. Simplicidade significa reduzir decisões e ruído sem remover contexto, personalidade, estados necessários ou caminhos de recuperação.

## Entenda antes de desenhar

- Identifique público, tarefa principal, conteúdo real, ação prioritária, restrições técnicas e resultado esperado pelo usuário.
- Inspecione a interface, marca e design system existentes. Preserve padrões reconhecíveis e altere a linguagem visual somente quando o pedido justificar.
- Confirme o tipo de entrega solicitado: direção visual, wireframe, especificação, protótipo ou implementação. Não amplie o escopo por conta própria.
- Quando faltarem detalhes não críticos, faça hipóteses explícitas e reversíveis. Solicite uma decisão apenas se ela mudar substancialmente a arquitetura ou o resultado.

## Defina a composição

1. Resuma em uma frase o objetivo da tela e escolha uma ação principal.
2. Organize o conteúdo pela ordem em que ajuda a pessoa a decidir ou concluir a tarefa.
3. Estabeleça uma hierarquia perceptível por tamanho, peso, contraste, posição e espaço; não dependa apenas de cor.
4. Escolha uma direção visual coerente com o produto e aplique-a de forma consistente.
5. Remova cada elemento que não esclareça, não diferencie, não oriente nem permita agir.

Prefira uma composição forte a várias caixas competindo por atenção. Use seções, cards, bordas e sombras somente quando comunicarem agrupamento, elevação ou interação. Não adote gradientes, glassmorphism, brilho, animações ou cantos excessivamente arredondados como sinônimo automático de “moderno”.

## Construa um sistema visual enxuto

- **Tipografia:** use poucas famílias e pesos; mantenha escala clara, corpo confortável e linhas de texto com comprimento legível. Destaque pelo contraste tipográfico antes de adicionar ornamento.
- **Cor:** parta de neutros e uma cor de ação ou marca. Reserve cores semânticas para feedback consistente e verifique contraste em todos os estados.
- **Espaçamento:** derive margens e lacunas de uma escala curta. Espaço em branco deve revelar relações, não apenas deixar a tela vazia.
- **Forma e profundidade:** mantenha raios, bordas, ícones e sombras consistentes. Use o menor nível de profundidade que explique a estrutura.
- **Imagens e ícones:** escolha recursos com função e linguagem compatíveis com o produto. Evite ilustrações genéricas e ícones ambíguos sem rótulo quando o significado não for universal.

Se o projeto já possuir tokens ou componentes, reutilize-os. Ao criar um sistema novo, defina somente os tokens necessários para repetir decisões reais; não invente uma biblioteca completa para uma única tela.

## Faça a interface responder ao espaço

- Projete a partir da prioridade do conteúdo, verificando ao menos uma largura estreita, uma intermediária e uma ampla.
- Faça o layout refluir em vez de apenas encolher. Preserve ordem, legibilidade e ação principal quando colunas se empilharem.
- Use contêineres e limites de largura para impedir linhas longas ou áreas vazias sem propósito em telas grandes.
- Evite rolagem horizontal da página. Em tabelas, comparações e visualizações densas, escolha conscientemente entre rolagem local, priorização de colunas ou uma apresentação alternativa.
- Não esconda conteúdo ou ações importantes somente para “limpar” a versão estreita.

## Desenhe comportamento, não só a tela ideal

Inclua os estados relevantes de cada fluxo: inicial, hover, foco, pressionado, selecionado, desabilitado, carregando, vazio, sucesso e erro. Use feedback próximo da ação e preserve os dados do usuário quando houver falha recuperável.

- Mantenha navegação, títulos e ações previsíveis.
- Torne controles reconhecíveis e áreas clicáveis confortáveis.
- Evite depender apenas de hover, gesto ou ícone sem explicação.
- Prefira skeleton somente quando ele representar a estrutura real; para ações rápidas, feedback discreto costuma bastar.
- Use movimento para explicar mudança, continuidade ou resultado. Respeite redução de movimento e evite animação ornamental recorrente.

## Garanta acesso e compreensão

- Use estrutura semântica, ordem de leitura lógica e hierarquia correta de títulos.
- Garanta operação por teclado, foco visível, rótulos associados e mensagens de erro compreensíveis.
- Não comunique estado apenas por cor. Verifique contraste de texto, ícones informativos, bordas essenciais e estados interativos.
- Preserve a experiência com zoom, texto maior, conteúdo longo, tradução e preferências de movimento.
- Escreva textos curtos e específicos. Botões devem descrever a ação; vazios e erros devem indicar o próximo passo possível.

Atenda ao nível WCAG definido pelo projeto. Na ausência de um alvo explícito, trate WCAG 2.2 AA como referência de aceitação.

## Valide o resultado

Antes de concluir, verifique:

- a ação principal é evidente sem competir com várias chamadas equivalentes;
- a tela continua compreensível com conteúdo real, longo, vazio e com erro;
- o layout funciona nas larguras-alvo sem cortes, sobreposição ou rolagem inesperada;
- navegação por teclado, foco, contraste, zoom e redução de movimento permanecem utilizáveis;
- componentes repetidos seguem os mesmos tokens, estados e padrões de interação;
- elementos decorativos têm propósito e a interface não parece um template genérico.

Quando houver implementação, execute os testes, linter e verificações de acessibilidade disponíveis no projeto. Entregue uma síntese da direção adotada, das decisões que mais afetam o uso e das validações realizadas.

## Referência oficial

- [Web Content Accessibility Guidelines (WCAG) 2.2](https://www.w3.org/TR/WCAG22/)
