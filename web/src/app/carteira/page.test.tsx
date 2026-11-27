import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ThemeProvider } from "../../theme/ThemeProvider";
import { PAGE_TITLES } from "../page-titles";
import CarteiraRoute from "./page";

const mockUseAuth = vi.fn();

vi.mock("../../auth/auth-context", () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => children,
  useAuth: () => mockUseAuth(),
}));

function authState(overrides: Record<string, unknown>) {
  return {
    state: "anonymous",
    user: null,
    error: null,
    client: {
      request: vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            complete: true,
            totalCost: { amount: "0.00", currency: "BRL" },
            totalCurrent: { amount: "0.00", currency: "BRL" },
            totalPnl: { amount: "0.00", currency: "BRL" },
            stocks: [],
            cdbs: [],
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    },
    mfaRestrictedToken: null,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    verifyMfa: vi.fn(),
    verifyRecoveryCode: vi.fn(),
    finishMfaRecoverySetup: vi.fn(),
    startOAuthLogin: vi.fn(),
    ...overrides,
  };
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Rota /carteira", () => {
  it("define título de documento Carteira", () => {
    expect(PAGE_TITLES.carteira).toBe("Carteira");
  });

  it("bloqueia visitante sem sessão e não exibe o hub", () => {
    mockUseAuth.mockReturnValue(authState({ state: "anonymous" }));

    render(
      <ThemeProvider>
        <CarteiraRoute />
      </ThemeProvider>,
    );

    expect(screen.queryByRole("heading", { level: 1, name: "Carteira" })).toBeNull();
    expect(screen.queryByRole("navigation", { name: "Navegação principal" })).toBeNull();
  });

  it("exibe um único h1 Carteira dentro do AppShell marcado como página atual", async () => {
    mockUseAuth.mockReturnValue(
      authState({ state: "authenticated", user: { id: "u1", email: "titular@example.com" } }),
    );

    render(
      <ThemeProvider>
        <CarteiraRoute />
      </ThemeProvider>,
    );

    const headings = await screen.findAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0].textContent).toBe("Carteira");
    expect(screen.getByRole("link", { name: "Carteira" }).getAttribute("aria-current")).toBe("page");
  });
});
