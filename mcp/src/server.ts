import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import {
  ApiError,
  AuthRequiredError,
  ControleGastosApiClient,
  type EnvelopePurpose,
} from "./api-client.js";

const moneySchema = z.object({
  amount: z
    .string()
    .regex(/^\d{1,17}\.\d{2}$/, "Use decimal string with exactly two places, e.g. 12.50")
    .describe("Valor monetário BRL com exatamente duas casas decimais"),
  currency: z.literal("BRL").default("BRL"),
});

const purposeSchema = z.enum([
  "LIMIT",
  "GOAL",
  "FIXED",
  "SAVINGS_TARGET",
  "ANNUAL_EXPENSE",
]);

const uuidSchema = z.string().uuid();

const monthSchema = z
  .string()
  .regex(/^\d{4}-(0[1-9]|1[0-2])$/, "Use YYYY-MM")
  .optional()
  .describe("Mês YYYY-MM; padrão = mês corrente na API");

function textResult(data: unknown) {
  return {
    content: [{ type: "text" as const, text: JSON.stringify(data, null, 2) }],
  };
}

function errorResult(error: unknown) {
  if (error instanceof AuthRequiredError) {
    return {
      content: [
        {
          type: "text" as const,
          text: JSON.stringify(
            {
              code: "AUTH_REQUIRED",
              message: error.message,
              action: "Chame a tool login com e-mail e senha. Se a API pedir MFA, use verify_mfa em seguida.",
            },
            null,
            2,
          ),
        },
      ],
      isError: true as const,
    };
  }
  if (error instanceof ApiError) {
    return {
      content: [
        {
          type: "text" as const,
          text: JSON.stringify({ status: error.status, body: safeJson(error.body) }, null, 2),
        },
      ],
      isError: true as const,
    };
  }
  const message = error instanceof Error ? error.message : "Unknown error";
  return {
    content: [{ type: "text" as const, text: message }],
    isError: true as const,
  };
}

function safeJson(body: string): unknown {
  try {
    return JSON.parse(body) as unknown;
  } catch {
    return body;
  }
}

export const MCP_TOOL_NAMES = [
  "login",
  "verify_mfa",
  "logout",
  "auth_status",
  "get_user_email",
  "list_envelopes",
  "get_envelope",
  "create_envelope",
  "update_envelope",
  "list_expenses",
  "list_envelope_expenses",
  "get_ledger_summary",
  "register_expense",
] as const;

export function createMcpServer(client: ControleGastosApiClient): McpServer {
  const server = new McpServer({
    name: "controle-gastos",
    version: "0.1.0",
  });

  server.registerTool(
    "login",
    {
      title: "Entrar na API",
      description:
        "Autentica com e-mail e senha (estilo mobile). Persiste access+refresh localmente. Não devolve tokens. Se mfaRequired, chame verify_mfa.",
      inputSchema: {
        email: z.string().email(),
        password: z.string().min(1),
      },
    },
    async ({ email, password }) => {
      try {
        return textResult(await client.login(email, password));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "verify_mfa",
    {
      title: "Confirmar MFA",
      description:
        "Conclui o login quando login retornou mfaRequired. Informe challengeId e o código TOTP.",
      inputSchema: {
        challengeId: z.string().min(1).max(512),
        code: z.string().min(1).max(10),
      },
    },
    async ({ challengeId, code }) => {
      try {
        return textResult(await client.verifyMfa(challengeId, code));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "logout",
    {
      title: "Sair",
      description: "Revoga a sessão na API (se possível) e apaga a sessão local do MCP.",
      inputSchema: {},
    },
    async () => {
      try {
        await client.logout();
        return textResult({ loggedOut: true });
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "auth_status",
    {
      title: "Status da sessão",
      description: "Indica se há sessão local salva (sem expor tokens).",
      inputSchema: {},
    },
    async () => textResult(client.authStatus()),
  );

  server.registerTool(
    "get_user_email",
    {
      title: "Consultar e-mail do usuário",
      description:
        "Retorna somente o e-mail da conta autenticada. Não expõe id, verificação nem datas do perfil.",
      inputSchema: {},
    },
    async () => {
      try {
        return textResult(await client.getUserEmail());
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "list_envelopes",
    {
      title: "Listar verbas",
      description:
        "Lista verbas visíveis. Para saldo real do mês use get_ledger_summary.",
      inputSchema: {
        month: monthSchema,
      },
    },
    async ({ month }) => {
      try {
        return textResult(await client.listEnvelopes(month));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "get_envelope",
    {
      title: "Detalhar verba",
      description: "Detalha uma verba pelo id. Saldo autoritativo: get_ledger_summary.",
      inputSchema: {
        id: uuidSchema.describe("UUID da verba"),
      },
    },
    async ({ id }) => {
      try {
        return textResult(await client.getEnvelope(id));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "create_envelope",
    {
      title: "Criar verba",
      description:
        "Cria verba. Para ANNUAL_EXPENSE informe annualAmount, dueMonth, dueDay e fundingMode; baseAmount pode ser 0.00. Para SAVINGS_TARGET informe targetAmount.",
      inputSchema: {
        name: z.string().min(1).max(80),
        purpose: purposeSchema,
        baseAmount: moneySchema,
        targetAmount: moneySchema.optional(),
        annualAmount: moneySchema.optional(),
        dueMonth: z.number().int().min(1).max(12).optional(),
        dueDay: z.number().int().min(1).max(31).optional(),
        fundingMode: z.enum(["MONTHLY", "ONE_TIME"]).optional(),
      },
    },
    async (input) => {
      try {
        return textResult(
          await client.createEnvelope({
            name: input.name,
            purpose: input.purpose as EnvelopePurpose,
            baseAmount: input.baseAmount,
            targetAmount: input.targetAmount,
            annualAmount: input.annualAmount,
            dueMonth: input.dueMonth,
            dueDay: input.dueDay,
            fundingMode: input.fundingMode,
          }),
        );
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "update_envelope",
    {
      title: "Editar verba",
      description:
        "Atualiza verba (somente owner). version é obrigatória (lock otimista do GET).",
      inputSchema: {
        id: uuidSchema.describe("UUID da verba"),
        version: z.number().int().min(0),
        name: z.string().min(1).max(80).optional(),
        purpose: purposeSchema.optional(),
        baseAmount: moneySchema.optional(),
        targetAmount: moneySchema.optional(),
        annualAmount: moneySchema.optional(),
        dueMonth: z.number().int().min(1).max(12).optional(),
        dueDay: z.number().int().min(1).max(31).optional(),
        fundingMode: z.enum(["MONTHLY", "ONE_TIME"]).optional(),
      },
    },
    async (input) => {
      try {
        const { id, ...body } = input;
        return textResult(
          await client.updateEnvelope(id, {
            version: body.version,
            name: body.name,
            purpose: body.purpose as EnvelopePurpose | undefined,
            baseAmount: body.baseAmount,
            targetAmount: body.targetAmount,
            annualAmount: body.annualAmount,
            dueMonth: body.dueMonth,
            dueDay: body.dueDay,
            fundingMode: body.fundingMode,
          }),
        );
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "list_expenses",
    {
      title: "Listar gastos",
      description: "Lista gastos no intervalo [from, to] (datas YYYY-MM-DD).",
      inputSchema: {
        from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
        to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
        page: z.number().int().min(0).optional(),
        size: z.number().int().min(1).max(100).optional(),
      },
    },
    async (input) => {
      try {
        return textResult(await client.listExpenses(input));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "list_envelope_expenses",
    {
      title: "Listar gastos de uma verba",
      description: "Lista lançamentos de gasto (EXPENSE) de uma verba.",
      inputSchema: {
        envelopeId: uuidSchema,
        month: monthSchema,
        page: z.number().int().min(0).optional(),
        size: z.number().int().min(1).max(100).optional(),
      },
    },
    async (input) => {
      try {
        return textResult(
          await client.listEnvelopeEntries(input.envelopeId, {
            month: input.month,
            page: input.page,
            size: input.size,
          }),
        );
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "get_ledger_summary",
    {
      title: "Resumo financeiro do mês",
      description:
        "Saldo autoritativo das verbas no mês (available/isNegative reais).",
      inputSchema: {
        month: monthSchema,
      },
    },
    async ({ month }) => {
      try {
        return textResult(await client.getLedgerSummary(month));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "register_expense",
    {
      title: "Registrar gasto",
      description:
        "Registra um gasto (EXPENSE) em uma verba. Não altera nem exclui lançamentos existentes.",
      inputSchema: {
        envelopeId: uuidSchema,
        amount: moneySchema,
        occurredAt: z
          .string()
          .regex(/^\d{4}-\d{2}-\d{2}$/)
          .describe("Data do gasto YYYY-MM-DD"),
        description: z.string().max(140).optional(),
      },
    },
    async (input) => {
      try {
        return textResult(
          await client.registerExpense(input.envelopeId, {
            amount: input.amount,
            occurredAt: input.occurredAt,
            description: input.description,
          }),
        );
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  return server;
}
