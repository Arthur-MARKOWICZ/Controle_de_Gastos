# Intent: rastreamento de investimentos

Status: confirmado (entrevista 2026-11-10)

## Outcome

Área de investimentos no mesmo app, com UX separada do orçamento, para ver o valor atual da carteira e por ativo.

## User

Você (uso pessoal).

## Why now

Acompanhar patrimônio (ações + renda fixa) sem misturar com o orçamento de gastos/verbas.

## Success

- Cadastra posição na mão (sem import da corretora).
- Ações atualizam por cotação automática.
- CDB cresce pela taxa (estimativa pelo rendimento).
- Vê total da carteira + valor por ativo + P&L simples desde a compra.

## Constraint

- Dia 1: ações (B3) + CDB.
- Fundos depois.
- Sempre 100% isolado das verbas/envelopes (mesmo produto/login; experiência e domínio separados).
- Continua monólito modular — não é microserviço/deployável separado.

## Out of scope

- Microserviço ou API isolada só de investimentos
- Sync/import automático da corretora
- Terminal de análise com gráficos elaborados
- Fundos no MVP
- Misturar compras/saldos com envelopes
- Relação com [alocacao-patrimonio.md](alocacao-patrimonio.md): aquele intent classifica verba de aporte no orçamento; este é a carteira por ativo. São complementares e não se misturam.
