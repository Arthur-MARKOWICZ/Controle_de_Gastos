# Tasks

## 1. ADR-0034

- [x] 1.1 Criar `docs/decisions/0034-modulo-de-investimentos.md` com Status "Aceito", Data 2026-11-26 e as seções Contexto, Decisão, Alternativas consideradas e Consequências, cobrindo D1 a D7 do `design.md`. Verificar que o arquivo existe e que cada uma das decisões D1 a D7 aparece em "Decisão" ou "Alternativas consideradas"
- [x] 1.2 Na seção Consequências do ADR-0034, listar o que as issues #76 a #80 herdam: a dependência `big-math` (#78), o ADR filho do provedor de cotação (#77), as novas tabelas no inventário LGPD (`docs/privacy/inventario-de-dados.md`) e MCP/desktop fora do dia 1. Verificar por leitura que os quatro itens constam

## 2. Referências cruzadas

- [x] 2.1 Em `docs/intent/rastreamento-investimentos.md`, adicionar o link para o ADR-0034 e verificar que o link relativo resolve (`ls docs/decisions/0034-modulo-de-investimentos.md`)
- [x] 2.2 Em `docs/decisions/0028-classificacao-de-alocacao-de-patrimonio.md`, acrescentar apenas uma linha nas Consequências apontando para o ADR-0034, sem alterar Status nem Decisão. Verificar com `git diff` que só essa linha mudou
- [x] 2.3 Em `docs/ideas/fundacao-tecnica.md`, registrar o módulo de investimentos onde a fundação lista módulos ou decisões, com link para o ADR-0034. Verificar que o link relativo resolve

## 3. Configuração do OpenSpec

- [x] 3.1 Em `openspec/config.yaml`, incluir `investments` na lista de módulos de negócio da regra `specs` e verificar que `openspec instructions specs --change adr-modulo-investimentos --json` devolve a regra atualizada

## 4. Verificação

- [x] 4.1 Rodar `openspec validate adr-modulo-investimentos` e verificar que passa (change com `skip_specs: true`)
- [x] 4.2 Conferir por busca (`grep -rn "ADR-0034\|0034-modulo" docs`) que intent, ADR-0028 e fundação apontam para o ADR novo, e que nenhum arquivo fora de `docs/` e `openspec/` mudou

## Workflow follow-up

- Arquivar a change depois da revisão humana.
- Abrir as changes das issues #76 a #80 nesta mesma branch `investimentos`, começando pela #76.
