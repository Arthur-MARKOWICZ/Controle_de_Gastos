# Validação das premissas de produto (OPP-009 / issue #37)

Protocolo leve para marcar as premissas de
[`fundacao-tecnica.md`](../fundacao-tecnica.md) com evidência mínima — sem
analytics pesado e sem implementar features novas.

## Objetivo

Em ~14 dias de uso pessoal + 2 entrevistas curtas, decidir com transparência se
cada premissa fica **validada**, **rejeitada** ou **ainda aberta**, e se o
roadmap deve seguir, adiar ou redesenhar sharing (OPP-005), alertas (OPP-006) e
onboarding (OPP-004).

## O que já está no repositório

| Artefato | Uso |
|---|---|
| Este README | Como rodar o ciclo |
| [`diario.md`](diario.md) | Diário de 14 dias (preencher um bloco por dia) |
| [`roteiro-entrevista.md`](roteiro-entrevista.md) | Roteiro das 2 entrevistas |
| [`entrevistas/`](entrevistas/) | Notas anonimizadas (E1, E2) |
| [`sintese.md`](sintese.md) | Status de cada premissa + decisão de roadmap |

## Como executar

1. **Defina o período** — escolha 14 dias corridos e anote início/fim em
   [`diario.md`](diario.md).
2. **Use o produto de verdade** — web e/ou Android; registre gastos reais (ou
   próximos do real). Não otimize o diário para “parecer bom”.
3. **Preencha o diário no mesmo dia** — se pular um dia, marque “sem
   lançamento” e o motivo em uma linha.
4. **Agende 2 entrevistas** (15–20 min) com pessoas que poderiam compartilhar
   verba (casal, família, colega de casa). Use o
   [`roteiro-entrevista.md`](roteiro-entrevista.md).
5. **Arquive notas sem PII** em `entrevistas/e1.md` e `entrevistas/e2.md`
   (iniciais ou “Participante A/B”; sem e-mail, telefone ou nome completo).
6. **Atualize** [`sintese.md`](sintese.md) e a seção “Premissas a validar” em
   [`fundacao-tecnica.md`](../fundacao-tecnica.md).

## Regras de evidência

- Amostra pequena = resultado **indicativo**, não estatístico.
- Não invente diário nem entrevistas para fechar a issue.
- Dúvidas de tipos de verba (`LIMIT`, `GOAL`, `FIXED`, `SAVINGS_TARGET`,
  `ANNUAL_EXPENSE`) contam como sinal para onboarding/glossário.
- Métricas de VPS: anote pico aproximado de RAM/CPU se observar; se não medir,
  deixe a premissa **ainda aberta** com justificativa.

## Critérios de aceite (issue #37)

- [ ] Diário preenchido no período acordado → [`diario.md`](diario.md)
- [ ] Notas das 2 entrevistas em `docs/` sem PII desnecessária → `entrevistas/`
- [ ] Cada premissa com status e justificativa → [`sintese.md`](sintese.md) +
  fundação técnica

## Fora de escopo

Implementar compartilhamento, push, auditoria detalhada ou analytics de produto.
