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
verbas podem ser criadas e editadas. Edição/exclusão de lançamentos, renda,
arquivamento e autenticação ficam fora deste corte.

## Decisão

- Adicionar o pacote `mcp/` no monorepo: servidor MCP em TypeScript, transporte
  **stdio**, que chama a API em `/api/v1` com `Authorization: Bearer`.
- Autenticar com `CONTROLE_GASTOS_API_BASE_URL` e
  `CONTROLE_GASTOS_ACCESS_TOKEN` no ambiente do processo (token obtido fora do
  MCP, via login web/mobile). Não persistir senha nem refresh no servidor MCP.
- Manter a API Spring como única fonte de regras e autorização por recurso.
- Escopo das tools neste ADR:
  - consultar gastos (`/history`, entradas da verba, resumo do ledger);
  - registrar gasto (`POST .../entries` com `kind=EXPENSE`);
  - consultar somente o e-mail (`GET /users/me`, resposta minimizada);
  - listar/detalhar, criar e editar verbas (`GET/POST/PATCH /envelopes`).
- Proibido neste servidor: mutar usuário, expor `id`/`emailVerified`/datas da
  conta, alterar ou excluir lançamentos, renda, MFA, OAuth, arquivar verba ou
  aportes (`CONTRIBUTION`).

## Alternativas consideradas

### Módulo Spring embutido (MCP HTTP no mesmo processo)

- Vantagem: um único deploy.
- Desvantagens: novo contrato no processo da API, dependências MCP no backend
  Java e risco de acoplar o host de agentes ao ciclo de vida da API.
- Rejeitado: o MCP é um cliente a mais, não uma capacidade de negócio.

### MCP remoto Streamable HTTP na VPS

- Vantagem: um endpoint compartilhado.
- Desvantagens: autenticação de host, exposição pública e operação a mais na VPS.
- Adiado: o corte atual prioriza stdio local no host do agente.

## Consequências

- O OpenAPI permanece a fonte de verdade dos contratos; o MCP só envelopa.
- Tokens no ambiente do host MCP nunca devem ir para logs (já exigido em
  `docs/privacy/`).
- Access JWT curto (15 min) exige renovar o token no host quando expirar.
- Ampliar tools (aportes, arquivar, renda) exige atualizar este ADR.
