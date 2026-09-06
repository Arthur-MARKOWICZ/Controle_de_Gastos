# Inventário inicial de dados pessoais

Este documento é uma base de engenharia, não uma definição definitiva de bases legais.

| Dado | Finalidade | Local | Retenção inicial | Observação |
|---|---|---|---|---|
| UUID de usuário | Identificar proprietário e participantes | API/PostgreSQL | Vida da conta e obrigações de auditoria | Não usar e-mail como chave de domínio |
| E-mail normalizado | Cadastro, login e recuperação de senha | API/PostgreSQL e Gmail SMTP | Vida da conta; descarte/anomização conforme política de exclusão | Uma recuperação concluída confirma o e-mail; nunca registrar em logs |
| Hash Argon2id da senha | Verificar credencial | API/PostgreSQL | Até troca ou exclusão da conta | Irreversível; senha original nunca é persistida |
| ID e hash da sessão | Renovar, detectar reuso e revogar acesso | API/PostgreSQL | Sessão ativa + 30 dias após encerramento | Refresh original existe somente no cliente; descarte diário automatizado |
| Hash de token de recuperação | Autorizar uma única troca de senha | API/PostgreSQL | Até 24 horas após expiração ou consumo | Token bruto só compõe o e-mail; válido por 15 minutos e nunca entra em logs |
| Chave HMAC de tentativa e contadores | Limitar abuso de login/cadastro | API/PostgreSQL | 24 horas | Derivada de e-mail/IP; não armazena os valores brutos; descarte diário e oportunístico |
| Segredo HMAC de JWT | Assinar e validar access tokens | Variável protegida no runtime | Enquanto houver access tokens emitidos (máximo de 15 min) | Nunca entra no Git, logs ou imagens; uma troca invalida os access tokens anteriores |
| Renda mensal e histórico de alterações | Distribuir verbas, validar limites e apresentar a evolução ao titular | API/PostgreSQL | Até exclusão da conta; política final de histórico pendente | Dado financeiro pessoal; somente valor total, mês de vigência, ator e instantes técnicos |
| Verbas (`envelope`: id, owner_id, name, purpose, base_amount, target_amount, target_reached_at, annual_amount, annual_due_month, annual_due_day, annual_funding_mode, created_at, archived_at, version) | Controle financeiro, cálculo de `unallocated` e acompanhamento de metas e gastos anuais | API/PostgreSQL | Até exclusão da conta | Isolamento obrigatório por `owner_id`; nome 1..80; purpose LIMIT/GOAL/FIXED/SAVINGS_TARGET/ANNUAL_EXPENSE; valores em `NUMERIC(19,2)` validados por `Money`; vencimento e modo anual existem somente para gastos anuais |
| Movimentações (`ledger_entry`: id, envelope_id, owner_id, author_id, amount, kind, occurred_at, description, created_at) | Saldo disponível (com carry), histórico e auditoria; `available = base*meses + contributions - expenses` | API/PostgreSQL | Até exclusão da conta | `amount NUMERIC(19,2) >0`, `kind EXPENSE/CONTRIBUTION`, `occurred_at DATE` em `America/Sao_Paulo`, `description VARCHAR(140)` opcional e sem PII obrigatório; saldo negativo permitido com alerta |
| Participação em verba (`envelope_participant`: envelope_id, user_id, added_at, added_by) | Compartilhamento e autorização (participante visualiza e registra `EXPENSE`; só `owner` faz `CONTRIBUTION`, arquiva e convida) | API/PostgreSQL | Até remoção/encerramento | PK composta `(envelope_id, user_id)`; preserva `author_id` em `ledger_entry` para auditoria |
| Token push | Entregar notificações | API/PostgreSQL | Até logout/revogação | Nunca registrar em logs |
| Eventos de auditoria | Segurança e responsabilização | API/PostgreSQL | Prazo a definir | Sem tokens ou payload financeiro completo |
| Segredo TOTP cifrado (AES-256-GCM) + nonce + keyVersion | Validar o segundo fator de login (MFA) | API/PostgreSQL | Até desabilitar o MFA ou excluir a conta | Nunca sai em texto claro do backend após a configuração; nonce aleatório por segredo; keyVersion existe para uma futura rotação da chave de runtime |
| Hash de recovery code (10 por ativação) | Autorizar acesso restrito de recuperação de MFA, uso único | API/PostgreSQL | Até 24 horas após consumo ou invalidação | Código bruto é exibido uma única vez ao titular; nunca em localStorage, logs ou telemetria |
| Hash de desafio de login MFA | Vincular a etapa de senha à etapa de segundo fator sem expor sessão | API/PostgreSQL | Até 24 horas após expiração ou consumo | Curta duração (5 minutos); nunca contém o código TOTP nem o recovery code |
| `AUTH_TOTP_ENCRYPTION_KEY` | Cifrar e decifrar segredos TOTP | Variável protegida no runtime | Enquanto houver segredos TOTP cifrados com essa versão de chave | Nunca entra no Git, logs ou imagens; perda da chave impede a decifragem de todos os segredos TOTP ativos |
| Vínculo de provedor social (`identity_provider_link`: id, user_id, provider, provider_user_id, provider_email, linked_at) | Autenticar por Google/GitHub sem duplicar conta; permitir múltiplos métodos de login por usuário | API/PostgreSQL | Até desvincular o provedor ou excluir a conta | `provider_user_id` nunca é usado como chave de recurso financeiro, só o UUID interno; `provider_email` é somente informativo (data do vínculo), nunca chave de busca; único por `(provider, provider_user_id)` e por `(user_id, provider)` |
| Hash de `state` de autorização OAuth (`oauth_authorization_state`) | Proteger o fluxo de login/vínculo social contra CSRF (RFC 6749 §10.12) | API/PostgreSQL | Até 24 horas após expiração ou consumo | Curta duração (10 minutos); `linking_user_id` só é preenchido quando a origem é "conectar provedor" a partir de uma sessão já autenticada |

## Operador para recuperação de senha

O Google, por meio do Gmail SMTP, recebe o endereço destinatário e o link de
recuperação para entregar a mensagem. Ambos são dados pessoais e o link contém
um segredo de acesso. A conta remetente usa app password exclusiva, mantida
somente como Secret no GitHub Environment; token, URL e senha jamais entram em
logs, backups de diagnóstico ou dados de teste. Finalidade, contrato,
região/transferência, retenção, descarte e resposta a incidentes do operador
continuam pendentes de registro pelo controlador antes da abertura pública.

## Operadores de login social

Google e GitHub recebem, durante o fluxo de autorização OAuth, o e-mail e o
identificador de conta da pessoa que opta por entrar com esse provedor —
ambos dados pessoais. Nenhum segredo do provedor (token de acesso) é
persistido pela API além do necessário para concluir a troca do código de
autorização. Finalidade, contrato, região/transferência, retenção, descarte e
resposta a incidentes de cada operador continuam pendentes de registro pelo
controlador antes da abertura pública, na mesma linha do operador Gmail SMTP
acima.

## Regras

- Toda nova coluna pessoal deve atualizar este inventário.
- Ambientes de desenvolvimento não devem usar dados reais.
- Backups seguem a mesma classificação e prazo dos dados de origem.
- Exportação deve usar formato legível e estruturado.
- Exclusão deve alcançar dados ativos, filas internas e backups conforme política documentada.
- Cookies de sessão e armazenamento cifrado do Android devem ser apagados no
  logout; a revogação no servidor prevalece se a limpeza local falhar.
