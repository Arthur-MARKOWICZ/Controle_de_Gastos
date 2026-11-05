# ADR-0026: Compartilhamento assimétrico de verbas

## Status

Aceito

## Data

2026-11-05

## Contexto

A fundação técnica confirma verbas compartilhadas: participantes visualizam e
registram gastos (`EXPENSE`); somente o proprietário abastece (`CONTRIBUTION`),
convida, remove e encerra. A tabela `envelope_participant` e a autorização de
leitura/ledger já existem; faltavam API de gestão e UX (issue #34 / OPP-005).

A premissa de demanda (#37) ainda coleta entrevistas; o desenho de papéis em si
já é resultado confirmado. Seguimos com o corte vertical para uso pessoal/piloto.

## Decisão

1. **Módulo `sharing`** orquestra convite/listagem/remoção; a linha de
   participação continua em `envelope_participant` (módulo `envelopes`), sem
   duplicar tabela.
2. **Convite por e-mail de conta existente e verificada:** o proprietário informa
   o e-mail; se houver usuário com `emailVerifiedAt` preenchido, ele é adicionado
   como participante. Conta inexistente ou não verificada recebe erro genérico
   (sem enumerar contas). Aceite por token/e-mail fica para evolução.
3. **Papéis:** owner convida/remove/edita/arquiva/aporte; participante vê e
   registra só `EXPENSE`; estranho não vê a verba.
4. **Sem push** neste corte; alertas de saldo negativo (#35) seguem para o owner.

## Alternativas consideradas

### Convite com token e e-mail (como reset de senha)

- Vantagem: consentimento explícito do convidado.
- Desvantagem: mais superfície (SMTP, TTL, abuso) no primeiro corte.
- Adiada; reutilizar o padrão de `PasswordResetService` quando o piloto exigir.

### Adicionar qualquer e-mail sem verificação

- Rejeitada: ADR-0005 condiciona ações sensíveis a e-mail verificado.

## Consequências

- Endpoints sob `/api/v1/envelopes/{id}/participants`.
- Clientes: UI mínima de convite para o owner; participante não vê “Aportar”.
- Inventário e OpenAPI atualizados; logs sem e-mail em claro nas falhas de convite.

## Referências

- Issue: [#34](https://github.com/Arthur-MARKOWICZ/Controle_de_Gastos/issues/34)
- Fundação: `docs/ideas/fundacao-tecnica.md`
- ADR-0005, ADR-0007
