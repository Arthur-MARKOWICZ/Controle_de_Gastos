# ADR-023: Servidor MCP como cliente REST via stdio

## Status

Aceito

## Data

2026-11-05

## Contexto

Agentes de IA (Cursor e outros hosts MCP) precisam consultar e registrar gastos e
gerir verbas sem passar pela UI. A API REST autenticada por JWT já é o contrato
público entre clientes e o monólito. Expor um segundo canal de mutação dentro do
processo Spring (módulo novo, filas ou cache) aumentaria superfície e conflitaria
com o monólito modular sem ganho claro.

Há restrição explícita de privacidade: o MCP não pode alterar dados de usuário
nem consultar o perfil além do e-mail. Gastos podem ser listados e registrados;
verbas podem ser criadas e editadas. Edição/exclusão de lançamentos, renda e
arquivamento ficam fora deste corte.

O access JWT dura cerca de 15 minutos. Colar o token no `mcp.json` obriga renovação
manual constante. O mobile já resolve isso guardando o refresh e reenviando-o como
cookie em `POST /api/v1/auth/refresh`.

## Decisão

- Adicionar o pacote `mcp/` no monorepo: servidor MCP em TypeScript, transporte
  **stdio**, que chama a API em `/api/v1` com `Authorization: Bearer`.
- Autenticar no estilo mobile:
  - `CONTROLE_GASTOS_API_BASE_URL` no ambiente do processo;
  - tools `login` / `verify_mfa` / `logout`;
  - persistir access + refresh + nome do cookie em arquivo local
    (`CONTROLE_GASTOS_SESSION_PATH` ou
    `~/.config/controle-gastos/mcp-session.json`), modo `0600`;
  - em `401`, renovar via `/auth/refresh` (Cookie) e repetir a chamada **uma**
    vez; se falhar, limpar a sessão e pedir nova chamada a `login`.
- Não devolver tokens nem senha no conteúdo das tools; não registrar tokens,
  cookies, senhas ou corpos de auth em logs (só banner em stderr).
- Manter a API Spring como única fonte de regras e autorização por recurso.
- Escopo das tools neste ADR:
  - autenticar sessão MCP (`login`, `verify_mfa`, `logout`);
  - consultar gastos (`/history`, entradas da verba, resumo do ledger);
  - registrar gasto (`POST .../entries` com `kind=EXPENSE`);
  - consultar somente o e-mail (`GET /users/me`, resposta minimizada);
  - listar/detalhar, criar e editar verbas (`GET/POST/PATCH /envelopes`).
- Proibido neste servidor: mutar perfil/usuário, expor `id`/`emailVerified`/datas
  da conta, alterar ou excluir lançamentos, renda, OAuth social, arquivar verba
  ou aportes (`CONTRIBUTION`).

## Alternativas consideradas

### Access token só no ambiente (`mcp.json`)

- Vantagem: zero persistência local de refresh.
- Desvantagem: renovação manual a cada ~15 min.
- Rejeitado para o uso cotidiano; substituído pela sessão estilo mobile.

### Módulo Spring embutido (MCP HTTP no mesmo processo)

- Vantagem: um único deploy.
- Desvantagens: novo contrato no processo da API, dependências MCP no backend
  Java e risco de acoplar o host de agentes ao ciclo de vida da API.
- Rejeitado: o MCP é um cliente a mais, não uma capacidade de negócio.

### MCP remoto Streamable HTTP com OAuth do protocolo

- Vantagem: UX nativa de conexão no host e multi-cliente.
- Desvantagens: expor MCP na VPS, novo modelo de auth e operação.
- Adiado: permanece alternativa futura; o corte atual prioriza stdio local.

## Consequências

- O OpenAPI permanece a fonte de verdade dos contratos; o MCP só envelopa.
- A sessão local é segredo de máquina: permissões restritas e fora do Git.
- Senha pode passar pelo contexto do agente na tool `login`; orientar uso só
  quando necessário e nunca ecoar a senha na resposta.
- MFA TOTP usa `verify_mfa` após `login` retornar `mfaRequired`.
- Ampliar tools (aportes, arquivar, renda, OAuth social) exige atualizar este ADR.
