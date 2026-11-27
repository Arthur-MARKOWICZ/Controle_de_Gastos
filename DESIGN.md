# Verbas — Design System e UX (fonte única)

**Status:** Aprovado em 2026-11-27  
**Combinação visual:** `1B + 2E + 3B + 4B + 5A + 6D`  
**Combinação de fluxo:** cards `ux-3a-6d` · backlog UX `5A`, `4A`, `6A`

Este arquivo é a **única** fonte de verdade de identidade visual, navegação e
padrões de UI/UX do produto. Agentes e humanos devem seguir este documento;
não reabrir as propostas descartadas nem inventar uma direção paralela.

Documentos substituídos (removidos):
`docs/design-proposal/*`, `docs/ux/analise-ux-completa.md`,
`docs/ux/prototipos-comparativos.html`, `docs/divergencias-design-ux.html`.
A direção visual do `docs/plans/plano_ui_ux_pro_max.md` (manter teal/Inter)
está **superseded** por este arquivo; tarefas de fluxo/a11y daquele plano só
valem se não contradisserem o que está aqui.

---

## 1. Decisão registrada

| Eixo | Escolha | Código interno |
|---|---|---|
| Identidade | Suíço Preciso + Carimbo & Tinta + Newsreader/DM Sans/IBM Plex Mono + monograma V | `design-swiss` |
| Navegação | Sidebar tonal agrupada (~8 rotas), não hub de 5 destinos | `design-5a` |
| Cards / Visão geral | Híbrido: layout operacional UX 3A + anel radial Design 6D | `ux-3a-6d` |
| Backlog UX aceito | Histórico cronológico · formulários contextuais · toast+undo+menu ARIA | `5A`, `4A`, `6A` |

### Alternativas rejeitadas (não implementar)

- Manter teal `#0b6b69` / coral fintech / Inter monolítica / sidebar preta + grid ciano.
- Hub de 5 destinos com Metas/Anuais como abas de `/verbas` (UX 1A).
- Cards só com anel (6D puro) **sem** seção de últimos lançamentos.
- Dashboard com histórico de renda ocupando metade da home.

### Isolamento Orçamento × Patrimônio

O [ADR-0034](docs/decisions/0034-modulo-de-investimentos.md) isola investimentos
do orçamento. A UI pode usar um seletor de área (Orçamento | Patrimônio), desde
que, **dentro do modo Orçamento**, a sidebar continue tonal e agrupada
(*Dia a dia* / *Planejamento* / *Conta*) com as rotas de orçamento. Não fundir
Carteira no meio das verbas sem novo ADR.

---

## 2. Identidade visual (`design-swiss`)

### 2.1 Universo (1B — Suíço Preciso)

| Token / papel | Claro | Escuro |
|---|---|---|
| Fundo / canvas | `#F6F7F9` | `#0F1114` |
| Superfície | `#FFFFFF` (ou equivalente token) | superfície elevada carvão |
| Sidebar | `#EFF1F5` | `#121519` |

- Bordas hairline `1px`.
- Raios: `2px` / `4px` / `6px` / `10px` (sem “pill” decorativo em cards).
- **Zero** sombras decorativas em cards/sidebar; sombra só em overlays
  (dialog/toast) se já existir token de overlay.
- **Zero** grid ciano / milimetrado no login, MFA, recovery ou shell.

### 2.2 Paleta (2E — Carimbo & Tinta)

| Papel | Claro | Escuro (aprox.) |
|---|---|---|
| Acento / Limite | `#1F3A73` | `#7DA2F0` |
| Alerta / Compromisso fixo | `#BD2D28` | `#F26B66` |
| Sucesso / Meta | `#1D6B52` | `#5EC7A3` |
| Poupança | `#5B3E7A` | `#B592DB` |
| Anual | `#9A5B13` | `#E6A65C` |

Proibido reintroduzir a antiga “fintech teal” `#0b6b69` como acento de marca
(PWA `theme_color`, MFA, toasts, etc.).

### 2.3 Tipografia (3B)

| Papel | Família |
|---|---|
| Display / valores hero | `Newsreader` (500–700) |
| UI / corpo | `DM Sans` (400–700, `tabular-nums`) |
| Metadados / datas | `IBM Plex Mono` (400–600) |

Auto-hospedadas em `.woff2` (Web e Desktop). Sem Google Fonts em runtime.
Mobile deve aproximar a mesma hierarquia (famílias embarcadas ou fallbacks
disciplinados); não deixar Roboto/Material default como identidade final.

### 2.4 Marca (4B)

Monograma **V dobrado** unificado: `docs/brand/logo.svg`, `web/src/app/icon.svg`,
launcher Android, Web e Desktop. Sem quadrado teal genérico nem ícone de
gráfico financeiro genérico.

### 2.5 Ícones

Phosphor (outline) na Web e Desktop, ou SVGs locais com o mesmo peso/traço.
Mobile: Material Outlined com peso alinhado. Sem glifos Unicode (`⚠`, `×`, `≡`)
como ícone estrutural.

---

## 3. Navegação (`design-5a`)

- Sidebar tonal que **acompanha** o tema (não bloco preto fixo no claro).
- Agrupamento por domínio no modo Orçamento:
  - **Dia a dia:** Visão geral, Verbas, Histórico
  - **Planejamento:** Metas, Gastos anuais, Relatórios
  - **Conta:** Configurações
- **Patrimônio:** Carteira (pode viver no seletor de área do ADR-0034).
- Manter rotas próprias (`/metas`, `/gastos-anuais`, …). **Não** colapsar em
  hub de 5 itens com sub-abas.
- CTA global **+ Registrar gasto** / `N` · `Ctrl+N` no shell de orçamento
  (já alinhado ao fluxo UX 2A).

Mobile: aproximar a mesma hierarquia (não promover Metas como 3ª aba global se
isso contradisser o agrupamento); Desktop Tauri pode permanecer num corte
menor de rotas se o ADR-0029 assim definir — documentar o corte, não inventar
hub visual diferente.

---

## 4. Cards e Visão geral (`ux-3a-6d`)

### Layout (UX 3A)

- Home operacional: **verbas prioritárias** + seção **Últimos lançamentos**.
- Histórico de renda **não** ocupa a home; fica no fluxo de editar renda.
- Cards sem `purposeRule` repetida em todos; natureza via pill/rótulo curto;
  detalhe da regra no glossário / tooltip / formulário.

### Indicador (Design 6D)

- Anel SVG radial compacto (**~36px**, não ~52px+) ao lado do nome.
- Barra hairline limpa (visível, fina); **sem** `border-top` / `border-left`
  de 4px em cards arredondados.
- Sem caixas aninhadas com borda grossa colorida.

---

## 5. Backlog UX aprovado

Implementar nestes termos (sem reabrir alternativas):

### 5A — Histórico

Lista agrupada por dia (`Hoje` / `Ontem` / data), chips de período
(`Este mês`, `Mês passado`, `7 dias`, personalizado) com estado selecionado;
filtros avançados retráteis.

### 4A — Formulários

- Natureza com descrição inline (sem destruir o rascunho ao abrir glossário).
- Saldo “Não alocado” ao vivo ao criar verba.
- Máscara BRL consistente; taxa CDB em **% a.a.** na UI (decimal só na API).
- Relatórios com seletor de mês / presets (não dois `date` manuais cegos).

### 6A — Feedback e a11y

- Toast global (`aria-live`) com **Desfazer** em ações reversíveis.
- `ActionMenu` acessível no lugar de `<details>` nos cards.
- `prefers-reduced-motion` sem kill global de `0.01ms` que apague feedback.

---

## 6. Tokens e paridade

- Web: `web/src/app/globals.css` (+ `next/font` local).
- Desktop: `desktop/src/styles/tokens.css` espelhando os semânticos.
- Mobile: `VerbasTheme.kt` com os mesmos hex semânticos.
- Qualquer mudança de acento/fonte/raio atualiza os três canais (ou registra
  exceção explícita neste arquivo).

---

## 7. Regras para agentes

1. Ler este arquivo antes de mudar UI, CSS, tema, navegação ou componentes
   visuais.
2. Não recriar propostas paralelas em `docs/design-proposal/`, `docs/ux/` ou
   novos HTMLs de “5 opções” sem pedido explícito.
3. Não “melhorar” a identidade para teal, glass, purple glow, Inter-only ou
   hub de 5 abas.
4. Mudança durável de direção visual ou de IA de navegação exige atualizar
   **este** documento (e ADR se tocar isolamento de módulos / contratos).
5. Após implementar gaps, validar contra as seções 2–5; não contra docs
   removidos.
