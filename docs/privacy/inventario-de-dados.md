# Inventário inicial de dados pessoais

Este documento é uma base de engenharia, não uma definição definitiva de bases legais.

| Dado | Finalidade | Local | Retenção inicial | Observação |
|---|---|---|---|---|
| UUID de usuário | Identificar proprietário e participantes | API/PostgreSQL | Vida da conta e obrigações de auditoria | Não usar e-mail como chave de domínio |
| E-mail normalizado | Cadastro e login | API/PostgreSQL | Vida da conta; descarte/anomização conforme política de exclusão | Não verificado neste corte; não autoriza recuperação ou ação sensível |
| Hash Argon2id da senha | Verificar credencial | API/PostgreSQL | Até troca ou exclusão da conta | Irreversível; senha original nunca é persistida |
| ID e hash da sessão | Renovar, detectar reuso e revogar acesso | API/PostgreSQL | Sessão ativa + 30 dias após encerramento | Refresh original existe somente no cliente; descarte diário automatizado |
| Chave HMAC de tentativa e contadores | Limitar abuso de login/cadastro | API/PostgreSQL | 24 horas | Derivada de e-mail/IP; não armazena os valores brutos; descarte diário e oportunístico |
| Segredo HMAC de JWT | Assinar e validar access tokens | Variável protegida no runtime | Enquanto houver access tokens emitidos (máximo de 15 min) | Nunca entra no Git, logs ou imagens; uma troca invalida os access tokens anteriores |
| Renda mensal e histórico de alterações | Distribuir verbas, validar limites e apresentar a evolução ao titular | API/PostgreSQL | Até exclusão da conta; política final de histórico pendente | Dado financeiro pessoal; somente valor total, mês de vigência, ator e instantes técnicos |
| Verbas (`envelope`: id, owner_id, name, purpose, base_amount, created_at, archived_at, version) | Controle financeiro e cálculo de `unallocated` | API/PostgreSQL | Até exclusão da conta | Isolamento obrigatório por `owner_id`; nome 1..80, purpose LIMIT/GOAL/FIXED, `base_amount NUMERIC(19,2)` validado por `Money` |
| Movimentações (`ledger_entry`: id, envelope_id, owner_id, author_id, amount, kind, occurred_at, description, created_at) | Saldo disponível (com carry), histórico e auditoria; `available = base*meses + contributions - expenses` | API/PostgreSQL | Até exclusão da conta | `amount NUMERIC(19,2) >0`, `kind EXPENSE/CONTRIBUTION`, `occurred_at DATE` em `America/Sao_Paulo`, `description VARCHAR(140)` opcional e sem PII obrigatório; saldo negativo permitido com alerta |
| Participação em verba (`envelope_participant`: envelope_id, user_id, added_at, added_by) | Compartilhamento e autorização (participante visualiza e registra `EXPENSE`; só `owner` faz `CONTRIBUTION`, arquiva e convida) | API/PostgreSQL | Até remoção/encerramento | PK composta `(envelope_id, user_id)`; preserva `author_id` em `ledger_entry` para auditoria |
| Token push | Entregar notificações | API/PostgreSQL | Até logout/revogação | Nunca registrar em logs |
| Eventos de auditoria | Segurança e responsabilização | API/PostgreSQL | Prazo a definir | Sem tokens ou payload financeiro completo |

## Regras

- Toda nova coluna pessoal deve atualizar este inventário.
- Ambientes de desenvolvimento não devem usar dados reais.
- Backups seguem a mesma classificação e prazo dos dados de origem.
- Exportação deve usar formato legível e estruturado.
- Exclusão deve alcançar dados ativos, filas internas e backups conforme política documentada.
- Cookies de sessão e armazenamento cifrado do Android devem ser apagados no
  logout; a revogação no servidor prevalece se a limpeza local falhar.
