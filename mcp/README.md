# MCP — controle de gastos

Servidor [Model Context Protocol](https://modelcontextprotocol.io) em stdio que
atua como cliente da API REST (`/api/v1`). Decisão: [ADR-023](../docs/decisions/0023-mcp-cliente-rest-stdio.md).

## Tools

| Tool | Ação |
|---|---|
| `get_user_email` | Consulta **somente** o e-mail da conta |
| `list_envelopes` / `get_envelope` | Lista e detalha verbas |
| `create_envelope` / `update_envelope` | Cria e edita verbas |
| `list_expenses` | Histórico de gastos no intervalo |
| `list_envelope_expenses` | Gastos de uma verba |
| `get_ledger_summary` | Saldos autoritativos do mês |
| `register_expense` | Registra gasto (`EXPENSE`) |

Fora do escopo: mutação/consulta de perfil além do e-mail, editar/excluir
lançamentos, aportes, renda, arquivar verba, MFA/OAuth.

## Configuração

```bash
cd mcp
cp .env.example .env   # ou exporte as variáveis no host MCP
pnpm install
pnpm test
pnpm build
```

Variáveis:

- `CONTROLE_GASTOS_API_BASE_URL` — origem HTTP(S) da API
- `CONTROLE_GASTOS_ACCESS_TOKEN` — access JWT (curto; renovar no host quando expirar)

## Cursor (`mcp.json`)

```json
{
  "mcpServers": {
    "controle-gastos": {
      "command": "node",
      "args": ["/caminho/absoluto/para/controle_gastos/mcp/dist/index.js"],
      "env": {
        "CONTROLE_GASTOS_API_BASE_URL": "http://localhost:8080",
        "CONTROLE_GASTOS_ACCESS_TOKEN": "<accessToken>"
      }
    }
  }
}
```

Em desenvolvimento sem build: `"command": "pnpm"`, `"args": ["--dir", ".../mcp", "exec", "tsx", "src/index.ts"]`.

## Privacidade

Não registre tokens, Authorization, e-mail em falhas de auth nem descrições de
gastos em logs do host. O processo MCP só escreve banner em **stderr**.
