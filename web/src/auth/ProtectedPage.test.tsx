import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ProtectedPage } from "./ProtectedPage";

const mockUseAuth = vi.fn();

vi.mock("./auth-context", () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => children,
  useAuth: () => mockUseAuth(),
}));

function authState(overrides: Record<string, unknown>) {
  return {
    state: "anonymous", user: null, error: null, client: {}, mfaRestrictedToken: null,
    login: vi.fn(), register: vi.fn(), logout: vi.fn(), verifyMfa: vi.fn(), verifyRecoveryCode: vi.fn(),
    finishMfaRecoverySetup: vi.fn(), startOAuthLogin: vi.fn(),
    ...overrides,
  };
}

function renderPage() {
  return render(<ProtectedPage>{(user) => <p>Página de {user.email}</p>}</ProtectedPage>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("ProtectedPage", () => {
  it("indica que a sessão está sendo restaurada", () => {
    mockUseAuth.mockReturnValue(authState({ state: "loading" }));

    renderPage();

    expect(screen.getByRole("status").textContent).toBe("Restaurando sua sessão…");
  });

  it("oferece login com e-mail e provedores sociais quando anônimo", () => {
    mockUseAuth.mockReturnValue(authState({ state: "anonymous" }));

    renderPage();

    expect(screen.getByRole("button", { name: /google/i })).toBeDefined();
    expect(screen.queryByText(/Página de/)).toBeNull();
  });

  it("pede o código do segundo fator quando o login exige MFA", () => {
    mockUseAuth.mockReturnValue(authState({ state: "mfa-pending" }));

    renderPage();

    expect(screen.getByLabelText("Código de 6 dígitos")).toBeDefined();
  });

  it("leva à reconfiguração do MFA após usar código de recuperação", () => {
    mockUseAuth.mockReturnValue(authState({
      state: "mfa-recovery-setup",
      mfaRestrictedToken: "token-restrito",
    }));

    renderPage();

    expect(screen.getByRole("heading", { name: "Configurar autenticação em duas etapas" })).toBeDefined();
    expect(screen.queryByText(/Página de/)).toBeNull();
  });

  it("mostra o aviso informado na tela de login", () => {
    mockUseAuth.mockReturnValue(authState({ state: "anonymous" }));

    render(<ProtectedPage loginNotice="MFA ativado com sucesso.">{() => null}</ProtectedPage>);

    expect(screen.getByText("MFA ativado com sucesso.")).toBeDefined();
  });

  it("renderiza a página quando autenticado", () => {
    mockUseAuth.mockReturnValue(authState({ state: "authenticated", user: { id: "u1", email: "pessoa@example.com" } }));

    renderPage();

    expect(screen.getByText("Página de pessoa@example.com")).toBeDefined();
  });
});
