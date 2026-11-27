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
| Verbas (`envelope`: id, owner_id, name, purpose, base_amount, target_amount, target_reached_at, annual_amount, annual_due_month, annual_due_day, annual_funding_mode, wealth_allocation, created_at, archived_at, version) | Controle financeiro, cálculo de `unallocated`, acompanhamento de metas/gastos anuais e classificação consumo vs alocação de patrimônio (ADR-0028) | API/PostgreSQL | Até exclusão da conta | Isolamento obrigatório por `owner_id`; nome 1..80; purpose LIMIT/GOAL/FIXED/SAVINGS_TARGET/ANNUAL_EXPENSE; `wealth_allocation` booleano (padrão false); valores em `NUMERIC(19,2)` validados por `Money`; vencimento e modo anual existem somente para gastos anuais |
| Movimentações (`ledger_entry`: id, envelope_id, owner_id, author_id, amount, kind, occurred_at, description, created_at, deleted_at) | Saldo disponível, histórico e auditoria; `LIMIT`/`FIXED`: `available = base + aportes_mês − gastos_mês`; demais com carry: `available = base*meses + contributions − expenses` (ADR-0022) | API/PostgreSQL | Até exclusão da conta; listagens por verba e saldos ignoram `deleted_at` preenchido (DB-001) | `amount NUMERIC(19,2) >0`, `kind EXPENSE/CONTRIBUTION`, `occurred_at DATE` em `America/Sao_Paulo`, `description VARCHAR(140)` opcional e sem PII obrigatório; saldo negativo permitido com alerta; exclusão do titular preserva UUID de `author_id` (ADR-0023) |
| Participação em verba (`envelope_participant`: envelope_id, user_id, added_at, added_by) | Compartilhamento e autorização (participante visualiza e registra `EXPENSE`; só `owner` faz `CONTRIBUTION`, arquiva e convida) | API/PostgreSQL | Até remoção/encerramento | PK composta `(envelope_id, user_id)`; convite por e-mail de conta já verificada (ADR-0026); logs de convite usam só UUIDs, sem e-mail em claro; preserva `author_id` em `ledger_entry` para auditoria |
| Recorrência (`recurring_entry`: id, envelope_id, kind, amount, day_of_month, description, active, first_competence, created_at, deleted_at, version) | Sugerir lançamentos mensais previsíveis ao proprietário da verba (ADR-0031) | API/PostgreSQL | Até exclusão da verba ou da conta (`ON DELETE CASCADE` com a verba); exclusão pelo usuário é lógica (`deleted_at`) | Só o owner da verba acessa; `amount NUMERIC(19,2) >0`; dia 1..31; `description VARCHAR(140)` opcional; incluída na exportação do titular |
| Ocorrência de recorrência (`recurring_occurrence`: id, recurring_entry_id, envelope_id, competence, due_date, suggested_amount, status PENDING/CONFIRMED/DISCARDED, ledger_entry_id, resolved_at, created_at, version) | Pendência de confirmação por competência; métrica de lançamentos vindos de recorrência (ADR-0031) | API/PostgreSQL | Acompanha a recorrência e a verba (`ON DELETE CASCADE`) | Única por `(recurring_entry_id, competence)`; não afeta saldo até virar `ledger_entry`; logs da geração só com contagens e IDs; incluída na exportação do titular |
| Assinatura Web Push (`web_push_subscription`: id, user_id, endpoint, p256dh, auth, created_at, last_success_at) | Entregar avisos opt-in ao navegador do titular (ADR-0033) | API/PostgreSQL | Até a remoção pelo titular, o logout no navegador, resposta 404/410 do push service ou a exclusão da conta (`ON DELETE CASCADE`) | Endpoint único; endpoint e chaves nunca entram em logs; a exportação do titular traz endpoint e datas, sem `p256dh`/`auth`; o payload só leva categoria, `EnvelopePurpose` e caminho |
| Preferências de notificação (`notification_preference`: user_id, daily_reminder, negative_balance, shared_expense, reminder_time, last_reminder_date, updated_at) | Registrar o consentimento por tipo e o horário do lembrete diário (ADR-0033) | API/PostgreSQL | Até a exclusão da conta (`ON DELETE CASCADE`) | Tudo desligado por padrão; horário em `America/Sao_Paulo`, passos de 15 minutos; incluída na exportação do titular |
| Lote de ativo da B3 (`stock_lot`: id, owner_id, ticker, quantity, unit_price, purchase_date, created_at) | Acompanhar a carteira de investimentos do titular, separada do orçamento (ADR-0034, issue #76) | API/PostgreSQL | Até a exclusão pelo titular (física) ou a exclusão da conta (removido pelo módulo `privacy`) | Só o titular acessa; sem participantes; ticker no formato da B3, `quantity >0`, `unit_price NUMERIC(19,2) >0`, data da compra até hoje em `America/Sao_Paulo`; custo de aquisição é derivado e não é gravado; ticker, quantidade e valores nunca entram em logs; incluído na exportação do titular |
| Posição de CDB prefixado (`cdb_position`: id, owner_id, principal, annual_rate, start_date, maturity_date, created_at) | Registrar CDB prefixado e estimar saldo pelo rendimento contratado, separado do orçamento (ADR-0034, issue #78) | API/PostgreSQL | Até a exclusão pelo titular (física) ou a exclusão da conta (removido pelo módulo `privacy`) | Só o titular acessa; sem participantes; `principal NUMERIC(19,2) >0`, `annual_rate NUMERIC(7,4) >0` com quatro casas, `maturity_date > start_date`, início até hoje em `America/Sao_Paulo`; estimado é derivado (fórmula do ADR-0034) e não é gravado; principal, taxa e valores estimados nunca entram em logs; incluído na exportação do titular (`cdbPositions`, sem `estimatedValue`) |
| Cotação de ativo da B3 (`stock_quote`: ticker, price, quoted_at, fetched_at) | Calcular o valor atual dos lotes pela última cotação válida (ADR-0034, ADR-0035, issue #77) | brapi.dev/PostgreSQL | Enquanto algum lote de qualquer titular usar o ticker; apagada no início da rodada seguinte à exclusão do último lote ou da conta (sem token configurado não há rodada nem limpeza) | Dado de referência global, sem `owner_id`; fora da exportação do titular; preço `NUMERIC(19,2) >0` arredondado a 2 casas (`HALF_EVEN`) só no adaptador; o provedor recebe apenas o ticker, com token no header `Authorization`; logs das rodadas só com contagens e códigos, nunca ticker com preço, resposta bruta ou token |
| Revisão de gasto (`ledger_entry_revision`: id, ledger_entry_id, owner_id, actor_user_id, action EDIT/DELETE, previous_envelope_id, previous_amount, previous_description, changed_at) | Trilha de edições e exclusões lógicas de `EXPENSE` (ADR-0027) | API/PostgreSQL | Acompanham o lançamento (`ON DELETE CASCADE`); UUID do ator preservado (RESTRICT) | Somente o owner lista; exportação inclui onde o titular é owner ou ator; logs só com IDs |
| Eventos de auditoria transversais | Segurança e responsabilização futuras (módulo `audit`) | — | — | Reservado; não confundir com `ledger_entry_revision` |
| Segredo TOTP cifrado (AES-256-GCM) + nonce + keyVersion | Validar o segundo fator de login (MFA) | API/PostgreSQL | Até desabilitar o MFA ou excluir a conta | Nunca sai em texto claro do backend após a configuração; nonce aleatório por segredo; keyVersion existe para uma futura rotação da chave de runtime |
| Hash de recovery code (10 por ativação) | Autorizar acesso restrito de recuperação de MFA, uso único | API/PostgreSQL | Até 24 horas após consumo ou invalidação | Código bruto é exibido uma única vez ao titular; nunca em localStorage, logs ou telemetria |
| Hash de desafio de login MFA | Vincular a etapa de senha à etapa de segundo fator sem expor sessão | API/PostgreSQL | Até 24 horas após expiração ou consumo | Curta duração (5 minutos); nunca contém o código TOTP nem o recovery code |
| `AUTH_TOTP_ENCRYPTION_KEY` | Cifrar e decifrar segredos TOTP | Variável protegida no runtime | Enquanto houver segredos TOTP cifrados com essa versão de chave | Nunca entra no Git, logs ou imagens; perda da chave impede a decifragem de todos os segredos TOTP ativos |
| Vínculo de provedor social (`identity_provider_link`: id, user_id, provider, provider_user_id, provider_email, linked_at) | Autenticar por Google/GitHub sem duplicar conta; permitir múltiplos métodos de login por usuário | API/PostgreSQL | Até desvincular o provedor ou excluir a conta | `provider_user_id` nunca é usado como chave de recurso financeiro, só o UUID interno; `provider_email` é somente informativo (data do vínculo), nunca chave de busca; único por `(provider, provider_user_id)` e por `(user_id, provider)` |
| Hash de `state` de autorização OAuth (`oauth_authorization_state`) | Proteger o fluxo de login/vínculo social contra CSRF (RFC 6749 §10.12) | API/PostgreSQL | Até 24 horas após expiração ou consumo | Curta duração (10 minutos); `linking_user_id` só é preenchido quando a origem é "conectar provedor" a partir de uma sessão já autenticada; `client` distingue web de nativo e decide se a sessão volta em cookie ou por código de handoff |
| Hash de código de handoff móvel (`oauth_mobile_handoff`: id, user_id, code_hash, created_at, expires_at, consumed_at) | Entregar a sessão do login social a um aplicativo nativo, que não compartilha o cookie do navegador do sistema (ADR-020) | API/PostgreSQL | Até 24 horas após expiração ou consumo | Vida de 60 segundos e uso único; o código bruto existe apenas na URL do App Link e na resposta da troca, nunca em log; o refresh devolvido no corpo é guardado pelo aplicativo com proteção da plataforma (Keystore no Android) |
| Alerta in-app (`in_app_alert`: id, owner_id, envelope_id, envelope_name, alert_type, reference_month, created_at, dismissed_at, resolved_at) | Persistir aviso de saldo negativo da verba para o titular ver/dispensar depois | API/PostgreSQL | Até exclusão da conta; ativos até dismiss/resolve | Sem valor monetário; logs só com `alertId`/`envelopeId`/`ownerId` (issue #35 / OPP-006 fase 1) |
| Refresh de sessão no cofre do sistema (cliente desktop) | Restaurar a sessão do aplicativo desktop sem pedir a senha a cada arranque (ADR-0029) | Cofre do sistema operacional do dispositivo (`secret-service` no Linux; equivalentes nas demais plataformas) | Até o logout, a revogação na API ou a desinstalação | Guarda apenas o valor e o nome do cookie de refresh; o access JWT fica só na memória do processo nativo e nunca é persistido; a interface do webview não recebe nenhum dos dois; sem cofre disponível o login falha, sem fallback para arquivo em texto claro; nunca em logs |
| Preferência de tema do desktop | Apresentar a interface em `SYSTEM`/`LIGHT`/`DARK` (ADR-013) | Arquivo de configuração do aplicativo no dispositivo | Até o usuário trocar a escolha ou remover os dados locais | Não vai à API, não é associada ao UUID do usuário e não entra em logs ou telemetria; fora do cofre do sistema |
| Termo de busca e filtros do histórico (`q`, `envelopeId`, `author` em `GET /api/v1/history`) | Encontrar gastos pela descrição, verba ou autor e somar os resultados (ADR-0032, issue #94) | Transitório: query string da requisição e URL da página web | Só durante a requisição; na web, enquanto a URL estiver no histórico do navegador | Não é persistido nem registrado em logs da API; o nginx opera com `access_log off`; a busca só alcança gastos que a pessoa já pode ver (dono ou participante) |

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

No aplicativo nativo o fluxo roda no navegador do sistema (Custom Tabs no
Android), nunca em WebView embutida: uma WebView daria ao aplicativo acesso às
credenciais digitadas no provedor. O aplicativo não recebe o código de
autorização do provedor nem guarda `client_secret`; recebe apenas o código de
handoff emitido pelo próprio backend. Ver
[ADR-020](../decisions/0020-login-social-google-e-github.md).

## Push service do navegador

Quando o titular ativa o push num navegador, o serviço de push do fornecedor
do navegador (Google para Chrome/Edge, Mozilla para Firefox, Apple para
Safari) recebe da API o payload **cifrado ponta a ponta** (RFC 8291) e
metadados de envio: endereço do servidor, horário, tamanho e TTL. O payload
não contém nome de verba, valores, descrição nem identificação de pessoas
(ADR-0033). Finalidade, contrato e região de cada operador continuam pendentes
de registro pelo controlador antes da abertura pública, na mesma linha dos
operadores acima.

## Regras

- Toda nova coluna pessoal deve atualizar este inventário.
- Ambientes de desenvolvimento não devem usar dados reais.
- Backups seguem a mesma classificação e prazo dos dados de origem.
- Exportação deve usar formato legível e estruturado.
- Exclusão deve alcançar dados ativos, filas internas e backups conforme política documentada.
- Cookies de sessão e armazenamento cifrado do Android devem ser apagados no
  logout; a revogação no servidor prevalece se a limpeza local falhar.
