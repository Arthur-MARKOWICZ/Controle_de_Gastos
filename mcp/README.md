# MCP — controle de gastos

Servidor [Model Context Protocol](https://modelcontextprotocol.io) em stdio que
atua como cliente da API REST (`/api/v1`). Decisão: [ADR-023](../docs/decisions/0023-mcp-cliente-rest-stdio.md).

## Autenticação (estilo mobile)

1. Configure só a URL da API no host MCP.
2. Prefira o CLI local (senha fica no terminal, não no chat do agente):

```bash
cd mcp
CONTROLE_GASTOS_API_BASE_URL=https://gastos.arthurmlopes.systems pnpm auth-login
```

   Use `pnpm auth-login` (não `pnpm login` — esse é o login do npm).
   Se a API pedir MFA, o CLI solicita o código TOTP na sequência.
3. Alternativa: chame a tool `login` / `verify_mfa` no host MCP (a senha passa
   pelo contexto do agente — evite quando possível).
4. Access + refresh ficam em `~/.config/controle-gastos/mcp-session.json` (ou
   `CONTROLE_GASTOS_SESSION_PATH`), modo `0600`.
5. Em `401`, o cliente renova via `/auth/refresh` e repete a chamada uma vez.
6. Se o refresh falhar, as tools respondem `AUTH_REQUIRED` pedindo login de novo.

As tools e o CLI **não** devolvem tokens nem ecoam a senha.

## Tools

| Tool | Ação |
|---|---|
| `login` / `verify_mfa` / `logout` / `auth_status` | Sessão local |
| `get_user_email` | Consulta **somente** o e-mail da conta |
| `list_envelopes` / `get_envelope` | Lista e detalha verbas |
| `create_envelope` / `update_envelope` | Cria e edita verbas |
| `list_expenses` | Histórico de gastos no intervalo |
| `list_envelope_expenses` | Gastos de uma verba |
| `get_ledger_summary` | Saldos autoritativos do mês |
| `register_expense` | Registra gasto (`EXPENSE`) |

Fora do escopo: mutação/consulta de perfil além do e-mail, editar/excluir
lançamentos, aportes, renda, arquivar verba, OAuth social.

## Configuração

```bash
cd mcp
cp .env.example .env   # ou exporte as variáveis no host MCP
pnpm install
pnpm test
pnpm build
```

Variáveis:

- `CONTROLE_GASTOS_API_BASE_URL` — origem HTTP(S) da API (obrigatória)
- `CONTROLE_GASTOS_SESSION_PATH` — arquivo de sessão (opcional)

## Cursor (`mcp.json`)

```json
{
  "mcpServers": {
    "controle-gastos": {
      "command": "node",
      "args": ["/caminho/absoluto/para/controle_gastos/mcp/dist/index.js"],
      "env": {
        "CONTROLE_GASTOS_API_BASE_URL": "https://SEU_SUBDOMINIO"
      }
    }
  }
}
```

Depois de conectar o servidor, rode `pnpm auth-login` no terminal (recomendado)
ou peça ao agente a tool `login`. Em desenvolvimento sem build: `"command": "pnpm"`,
`"args": ["--dir", ".../mcp", "exec", "tsx", "src/index.ts"]`.

## Privacidade

Não registre tokens, Authorization, cookies, senhas, e-mail em falhas de auth
nem descrições de gastos em logs do host. O processo MCP só escreve banner em
**stderr**.
