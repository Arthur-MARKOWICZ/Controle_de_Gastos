# Operação de renda mensal

Registro de engenharia para privacidade desde a concepção. Não certifica
conformidade jurídica nem escolhe a hipótese legal pelo controlador.

## Escopo e premissas

- Finalidade: permitir que o titular distribua sua renda entre verbas e consulte
  a evolução do valor mensal.
- Titular e origem: usuário autenticado que informa manualmente a própria renda.
- Dados: UUID do proprietário/ator, valor total em BRL, mês de vigência e
  instantes técnicos. Não são coletados empregador, fonte, descrição ou conta
  bancária.
- Operações: coleta, validação, armazenamento, consulta e exclusão em cascata
  com a conta. Não há integração bancária, compartilhamento, perfilamento,
  telemetria nem decisão automatizada relevante neste corte.

## Evidências e estado

| Requisito | Estado | Evidência ou pendência |
|---|---|---|
| Necessidade e minimização | atende com evidência | Esquema `V2` guarda somente campos necessários; a API aceita apenas o valor total. |
| Autorização e isolamento | atende com evidência | Proprietário vem do JWT validado; teste de integração comprova que outra conta recebe 404. |
| Exatidão do dado | atende com evidência | `BigDecimal`/`NUMERIC(19,2)`, sem arredondamento implícito, e histórico append-only. |
| Segurança de logs | atende com evidência | Nenhum código da renda registra corpo, valor ou identidade; permanecem as proibições gerais de log. |
| Dados de teste | atende com evidência | Testes usam UUIDs, e-mails e valores sintéticos. |
| Retenção detalhada do histórico | pendente | Controlador deve definir prazo, exceções e descarte verificável antes da abertura pública. |
| Hipótese legal e aviso de privacidade | pendente | Controlador/jurídico deve associar finalidade e hipótese do art. 7º e publicar transparência adequada. |
| Controlador, encarregado e canal do titular | pendente | Responsáveis e canal não foram informados. |
| Operadores, região da VPS e backups | pendente | Contrato, país de armazenamento/acesso, criptografia e transferência internacional da Hostinger devem ser verificados. |
| Exportação, correção e exclusão verificável | pendente | A cascata do banco existe, mas o fluxo completo de direitos ainda não foi entregue. |

## Riscos e critérios antes de uso público

- **Acesso entre contas:** mitigado na API; manter teste de isolamento em toda
  nova consulta ou mutação.
- **Exposição em logs/backups:** renda tem alta confidencialidade. Critério:
  logs sem payload e backup cifrado com restauração/exclusão documentadas.
- **Retenção indefinida:** definir evento inicial, prazo, exceções e propagação
  da exclusão antes do cadastro público.
- **Transferência internacional:** confirmar regiões de armazenamento, suporte
  e backup e, se aplicável, documentar o mecanismo do art. 33.

Fontes jurídicas e data de verificação permanecem no mapa da skill LGPD e no
[ADR-003](../decisions/0003-privacidade-desde-a-concepcao.md).
