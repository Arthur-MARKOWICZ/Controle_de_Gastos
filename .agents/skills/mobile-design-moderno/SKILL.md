---
name: mobile-design-moderno
description: Cria e refina experiências mobile modernas, simples, acessíveis e centradas em toque. Use para fluxos, telas e sistemas de interface de apps iOS ou Android; não use como guia principal para sites apenas responsivos.
---

# Mobile Design Moderno

Projete para uso em movimento, espaço limitado, toque impreciso e interrupções. Simplicidade mobile é deixar a próxima decisão óbvia sem esconder informação essencial, recuperação de erros ou recursos importantes.

## Entenda o contexto

- Identifique público, tarefa principal, frequência de uso, ambiente, conectividade, dados necessários e sensibilidade das ações.
- Defina se a experiência é iOS, Android, multiplataforma ou apenas uma especificação conceitual. Respeite convenções da plataforma e o design system existente.
- Confirme o artefato solicitado: fluxo, wireframe, visual final, protótipo, especificação ou implementação. Não transforme um pedido de design em uma reconstrução técnica não autorizada.
- Quando faltarem detalhes não críticos, registre hipóteses simples e reversíveis. Não invente funcionalidades para preencher a tela.

## Modele o fluxo antes da aparência

1. Dê a cada tela um objetivo dominante e uma ação principal.
2. Desenhe o caminho mais comum com o menor número razoável de decisões e interrupções.
3. Inclua entrada, retorno, cancelamento, confirmação, erro e retomada; não modele apenas o caminho feliz.
4. Revele complexidade no momento em que ela se torna necessária, mantendo contexto e possibilidade de voltar.
5. Use padrões de navegação familiares à plataforma. Uma inovação visual não deve tornar a navegação misteriosa.

Mantenha destinos de primeiro nível poucos e estáveis. Use barra inferior apenas para áreas principais recorrentes; ações contextuais pertencem à própria tela. Não esconda uma função essencial exclusivamente em gesto, menu ou pressionamento longo.

## Crie uma hierarquia visual calma

- **Tipografia:** use a escala e os estilos da plataforma ou do produto, com poucos pesos e contraste claro. Suporte texto ampliado e evite alturas rígidas que cortem conteúdo.
- **Cor:** trabalhe com base neutra e acentos controlados. Preserve contraste e significado em tema claro ou escuro quando ambos fizerem parte do produto.
- **Espaçamento:** mantenha uma escala curta e áreas de respiro que agrupem conteúdo sem desperdiçar a tela.
- **Superfícies:** use cards, divisores, sombras e modais somente para explicar agrupamento, elevação ou mudança de contexto.
- **Identidade:** escolha imagens, ícones, forma e movimento coerentes com a marca. Evite a estética genérica de “app futurista” quando ela não serve ao produto.

Uma interface moderna não exige gradientes, vidro, sombras profundas ou animação constante. Prefira conteúdo legível, contraste preciso, formas consistentes e uma composição com personalidade controlada.

## Projete para mãos e dispositivos reais

- Posicione ações frequentes em áreas alcançáveis sem comprometer a hierarquia ou causar toques acidentais.
- Preserve áreas seguras, recortes, barras do sistema, indicador de gesto e teclado virtual.
- Use alvos de toque confortáveis: como referência, pelo menos 44 × 44 pt no iOS e 48 × 48 dp no Android, com separação suficiente.
- Não coloque ações destrutivas junto de ações frequentes; confirme apenas consequências difíceis de desfazer.
- Considere tela pequena e grande, orientação relevante e adaptação para tablet ou layout dobrável quando fizerem parte do escopo.
- Quando o teclado abrir, mantenha campo ativo, contexto, erro e ação de conclusão visíveis ou facilmente alcançáveis.

Não copie mecanicamente um layout de desktop. Reordene, agrupe e revele conteúdo conforme a prioridade mobile.

## Cubra estados e condições do mundo real

Desenhe os estados necessários: inicial, pressionado, selecionado, desabilitado, carregando, offline, vazio, parcial, sucesso, erro e sincronização. Para fluxos com dados, considere atualização, conflito e conteúdo desatualizado quando forem possíveis.

- Mostre progresso apenas quando ele ajudar a interpretar a espera.
- Preserve entrada já fornecida após erros recuperáveis.
- Explique permissões no contexto, antes do pedido do sistema, e ofereça um caminho útil caso sejam negadas.
- Forneça feedback visual e, quando apropriado, tátil sem depender dele para comunicar o resultado.
- Use gestos como aceleradores acompanhados por uma alternativa visível.
- Faça transições explicarem origem, destino ou mudança de estado e respeite redução de movimento.

## Garanta acessibilidade e confiança

- Mantenha ordem de leitura e foco coerentes para tecnologias assistivas.
- Forneça nomes acessíveis a controles e descrições somente quando acrescentarem significado.
- Não dependa apenas de cor, posição, gesto, som ou vibração para comunicar estado.
- Verifique contraste, texto ampliado, inversão ou tema, preferências de movimento e controles do sistema.
- Use linguagem direta e específica. Informe consequência antes de ações sensíveis e torne desfazer preferível a confirmações repetidas quando for seguro.
- Colete ou revele apenas os dados necessários para a tarefa; não use padrões manipulativos para consentimento, assinatura ou permissão.

## Valide o resultado

Antes de concluir, verifique:

- a próxima ação está clara e os destinos de navegação permanecem previsíveis;
- o fluxo sobrevive a interrupção, retorno, perda de conexão, permissão negada e erro recuperável quando aplicáveis;
- nenhum conteúdo é cortado com texto longo, tradução, fonte ampliada, teclado ou área segura;
- alvos de toque, contraste, ordem de foco, rótulos e redução de movimento estão adequados;
- a experiência foi considerada em ao menos um dispositivo pequeno e um grande da plataforma-alvo;
- estados, componentes e tokens são consistentes, sem decoração ou telas desnecessárias.

Quando houver implementação, use as ferramentas de inspeção e acessibilidade da plataforma e execute os testes e verificações existentes no projeto. Na entrega, resuma o fluxo, a direção visual, os compromissos específicos de plataforma e o que foi validado.

## Referências oficiais

- [Accessibility — Apple Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/accessibility)
- [Make apps more accessible — Android Developers](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views)
