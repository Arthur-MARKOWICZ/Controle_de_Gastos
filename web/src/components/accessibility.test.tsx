import { cleanup, render, waitFor } from "@testing-library/react";
import axe from "axe-core";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { AuthScreen } from "./AuthScreen/AuthScreen";
import { Dashboard } from "./Dashboard/Dashboard";
import { HistoryPage } from "./HistoryPage/HistoryPage";
import { ReportsPage } from "./ReportsPage/ReportsPage";
import { VerbasPage } from "./VerbasPage/VerbasPage";
import { ThemeProvider } from "../theme/ThemeProvider";
import { THEME_STORAGE_KEY, type ThemePreference } from "../theme/theme";

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
    this.open = true;
  });
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
    this.open = false;
  });
});

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({ get: () => null }),
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: React.ReactNode; href: string }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

const authClient = vi.hoisted(() => ({ request: vi.fn() }));

vi.mock("../auth/auth-context", () => ({
  useAuth: () => ({ client: authClient, state: "authenticated" }),
}));

const envelope = {
  id: "1",
  name: "Combustível",
  purpose: "LIMIT",
  baseAmount: { amount: "400.00", currency: "BRL" },
  available: { amount: "240.00", currency: "BRL" },
  isNegative: false,
  role: "OWNER",
  createdAt: new Date().toISOString(),
  archivedAt: null,
  version: 0,
};

vi.mock("../hooks/useEnvelopes", () => ({
  useEnvelopes: () => ({
    envelopes: [envelope],
    loading: false,
    error: null,
    refresh: vi.fn(),
    setEnvelopes: vi.fn(),
  }),
}));

vi.mock("../hooks/useLedgerSummary", () => ({
  useLedgerSummary: () => ({
    summary: {
      income: { amount: "5000.00", currency: "BRL", effectiveFrom: "2026-08", changedAt: new Date().toISOString() },
      allocated: { amount: "4250.00", currency: "BRL" },
      unallocated: { amount: "750.00", currency: "BRL" },
      usagePct: 85,
      envelopes: [envelope],
    },
    loading: false,
    error: null,
    refresh: vi.fn(),
    setSummary: vi.fn(),
  }),
}));

vi.mock("../hooks/useIncome", () => ({
  useIncome: () => ({
    income: { amount: "5000.00", currency: "BRL", effectiveFrom: "2026-08", changedAt: new Date().toISOString() },
    loading: false,
    error: null,
    notConfigured: false,
    refresh: vi.fn(),
    setIncome: vi.fn(),
  }),
  useIncomeHistory: () => ({
    data: { items: [], page: 0, size: 20, hasNext: false },
    loading: false,
    error: null,
    page: 0,
    load: vi.fn(),
    refresh: vi.fn(),
  }),
}));

vi.mock("../lib/api", async (importOriginal) => {
  const original = await importOriginal<typeof import("../lib/api")>();
  return {
    ...original,
    createApiClient: () => ({
      getHistory: vi.fn().mockResolvedValue({
        items: [
          {
            purpose: "LIMIT",
            envelopeName: "Combustível",
            role: "OWNER",
            entry: {
              id: "entry-1",
              envelopeId: "1",
              amount: { amount: "50.00", currency: "BRL" },
              occurredAt: "2026-08-10",
              description: "Gasolina",
              deletedAt: null,
            },
          },
        ],
        page: 0,
        size: 10,
        hasNext: false,
      }),
      getHistorySummary: vi.fn().mockResolvedValue({
        income: { amount: "5000.00", currency: "BRL" },
        expenses: { amount: "1200.00", currency: "BRL" },
        netBalance: { amount: "3800.00", currency: "BRL" },
        accumulatedBalance: { amount: "2400.00", currency: "BRL" },
        monthlyTotals: [],
        purposeTotals: [],
      }),
      getEnvelopes: vi.fn().mockResolvedValue([envelope]),
      downloadReport: vi.fn(),
    }),
  };
});

afterEach(() => {
  cleanup();
  localStorage.clear();
  authClient.request.mockReset();
});

async function expectNoAxeViolations() {
  const result = await axe.run(document.body, {
    rules: {
      // JSDOM não calcula contraste; ele é validado no navegador real.
      "color-contrast": { enabled: false },
    },
  });
  expect(result.violations).toEqual([]);
}

function renderInTheme(ui: React.ReactNode, theme: ThemePreference) {
  localStorage.setItem(THEME_STORAGE_KEY, theme);
  return render(<ThemeProvider>{ui}</ThemeProvider>);
}

describe.each<ThemePreference>(["light", "dark"])("acessibilidade no tema %s", (theme) => {
  it("não introduz violações estruturais na autenticação", async () => {
    renderInTheme(<AuthScreen onLogin={vi.fn()} onRegister={vi.fn()} />, theme);
    await expectNoAxeViolations();
  });

  it("não introduz violações estruturais na Visão geral", async () => {
    renderInTheme(<Dashboard email="ana@example.com" onLogout={vi.fn()} />, theme);
    await waitFor(() => expect(document.querySelector("h1")?.textContent).toContain("Visão geral"));
    await expectNoAxeViolations();
  });

  it("não introduz violações estruturais em Verbas", async () => {
    renderInTheme(<VerbasPage email="ana@example.com" onLogout={vi.fn()} />, theme);
    await waitFor(() => expect(document.querySelector("h1")?.textContent).toContain("Verbas"));
    await expectNoAxeViolations();
  });

  it("não introduz violações estruturais no Histórico/Gastos", async () => {
    renderInTheme(<HistoryPage email="ana@example.com" onLogout={vi.fn()} />, theme);
    await waitFor(() => expect(document.querySelector("h1")?.textContent).toContain("Gastos"));
    await expectNoAxeViolations();
  });

  it("não introduz violações estruturais em Relatórios", async () => {
    renderInTheme(<ReportsPage email="ana@example.com" onLogout={vi.fn()} />, theme);
    await waitFor(() => expect(document.querySelector("h1")?.textContent).toContain("Relatórios"));
    await expectNoAxeViolations();
  });
});
