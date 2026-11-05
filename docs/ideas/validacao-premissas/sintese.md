# Síntese das premissas — issue #37 / OPP-009

Atualize este arquivo ao final do ciclo (ou assim que uma premissa tiver
evidência suficiente). Status permitidos: **validada**, **rejeitada**,
**ainda aberta**.

Ciclo: início ____/____/________ · fim ____/____/________  
Fontes: [`diario.md`](diario.md) · `entrevistas/e1.md` · `entrevistas/e2.md`

---

## 1. Lançamento manual vira hábito diário

| Campo | Valor |
|---|---|
| Status | ainda aberta |
| Evidência | Aguardando diário de 14 dias |
| Justificativa | Sem contagem de dias com lançamento ainda |
| Implicação de roadmap | Se fraco → considerar onboarding (OPP-004); se forte → não investir em onboarding longo agora |

## 2. Modelo de verbas compreendido sem explicação extensa

| Campo | Valor |
|---|---|
| Status | ainda aberta |
| Evidência | Aguardando dúvidas de tipo de verba no diário |
| Justificativa | Glossário existe; falta observar uso real |
| Implicação de roadmap | Dúvidas recorrentes → reforçar microcopy/onboarding; poucas dúvidas → manter como está |

## 3. Controle assimétrico de verba compartilhada atende uso real

| Campo | Valor |
|---|---|
| Status | validada (desenho) / ainda aberta (demanda quantitativa) |
| Evidência | Resultado confirmado na fundação técnica; ADR-0026 aceito; entrevistas E1/E2 ainda pendentes |
| Justificativa | Papéis assimétricos já eram intenção confirmada do produto; seguimos com corte #34 para piloto. Demanda de co-usuários reais continua a ser medida nas entrevistas |
| Implicação de roadmap | **Seguir** ADR + corte vertical (#34 / OPP-005); revisar utilidade após entrevistas |

## 4. VPS mantém folga com API, web e PostgreSQL

| Campo | Valor |
|---|---|
| Status | ainda aberta |
| Evidência | Medição operacional ainda não registrada neste ciclo |
| Justificativa | Exige observação na VPS (RAM/CPU) durante uso típico; sem inventar números |
| Implicação de roadmap | Se apertado → adiar cargas (push, jobs); se folgado → ok manter monólito Compose |

## 5. Haverá macOS/Xcode antes de declarar iOS de produção

| Campo | Valor |
|---|---|
| Status | ainda aberta |
| Evidência | Ambiente atual de desenvolvimento/CI **sem** macOS/Xcode (fato conhecido) |
| Justificativa | Acesso futuro a Mac continua decisão pessoal/orçamento; não há data comprometida |
| Implicação de roadmap | Manter KMP preparado; **não** declarar suporte iOS de produção (ADR-004) até haver Mac |

---

## Decisão de roadmap (preencher ao fechar o ciclo)

| Tema | Seguir agora? | Nota |
|---|---|---|
| Onboarding mínimo (OPP-004) | [ ] sim [ ] não [ ] depois | |
| Compartilhamento (#34 / OPP-005) | [x] sim [ ] não [ ] depois | ADR-0026 + API/UX mínima |
| Alertas persistentes (#35 / OPP-006) | [ ] sim [ ] não [ ] depois | |
| iOS produção | [ ] sim [ ] não [ ] depois | |

## Conclusão (1 parágrafo)

_(Escrever após preencher diário e entrevistas.)_
