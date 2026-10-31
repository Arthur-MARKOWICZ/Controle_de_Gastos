import { act, cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import OAuthCallbackPage from "./page";

const mockCompleteOAuthCallback = vi.fn().mockResolvedValue(undefined);
const mockVerifyMfa = vi.fn();
const mockVerifyRecoveryCode = vi.fn();
const mockUseAuth = vi.fn();
const mockReplace = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace }),
  useSearchParams: () => new URLSearchParams("mfaRequired=true&challengeId=desafio-1"),
}));

vi.mock("../../../auth/auth-context", () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => children,
  useAuth: () => mockUseAuth(),
}));

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.useRealTimers();
});

describe("OAuthCallbackPage", () => {
  it("processa os parâmetros da URL assim que a página carrega", () => {
    mockUseAuth.mockReturnValue({
      state: "loading",
      error: null,
      completeOAuthCallback: mockCompleteOAuthCallback,
      verifyMfa: mockVerifyMfa,
      verifyRecoveryCode: mockVerifyRecoveryCode,
    });

    render(<OAuthCallbackPage />);

    expect(mockCompleteOAuthCallback).toHaveBeenCalledWith(expect.any(URLSearchParams));
  });

  it("mostra a etapa de MFA quando o segundo fator é exigido", () => {
    mockUseAuth.mockReturnValue({
      state: "mfa-pending",
      error: null,
      completeOAuthCallback: mockCompleteOAuthCallback,
      verifyMfa: mockVerifyMfa,
      verifyRecoveryCode: mockVerifyRecoveryCode,
    });

    render(<OAuthCallbackPage />);

    expect(screen.getByLabelText("Código de 6 dígitos")).toBeDefined();
  });

  it("mostra o erro genérico quando o login social falha", () => {
    mockUseAuth.mockReturnValue({
      state: "anonymous",
      error: "Não foi possível entrar com esse provedor. Tente novamente.",
      completeOAuthCallback: mockCompleteOAuthCallback,
      verifyMfa: mockVerifyMfa,
      verifyRecoveryCode: mockVerifyRecoveryCode,
    });

    render(<OAuthCallbackPage />);

    expect(screen.getByRole("alert").textContent).toMatch(/não foi possível entrar/i);
    expect(screen.getByRole("link", { name: "Voltar ao login" })).toBeDefined();
  });

  it("oferece volta ao login quando o callback fica anônimo sem erro", () => {
    mockUseAuth.mockReturnValue({
      state: "anonymous",
      error: null,
      completeOAuthCallback: mockCompleteOAuthCallback,
      verifyMfa: mockVerifyMfa,
      verifyRecoveryCode: mockVerifyRecoveryCode,
    });

    render(<OAuthCallbackPage />);

    expect(screen.getByRole("alert").textContent).toMatch(/não foi possível concluir/i);
    expect(screen.getByRole("link", { name: "Voltar ao login" })).toBeDefined();
  });

  it("sai do loading indefinido por tempo limite", async () => {
    mockUseAuth.mockReturnValue({
      state: "loading",
      error: null,
      completeOAuthCallback: mockCompleteOAuthCallback,
      verifyMfa: mockVerifyMfa,
      verifyRecoveryCode: mockVerifyRecoveryCode,
    });

    render(<OAuthCallbackPage />);
    expect(screen.getByText("Concluindo login…")).toBeDefined();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(15_000);
    });

    expect(screen.getByRole("alert").textContent).toMatch(/não foi possível concluir/i);
    expect(screen.getByRole("link", { name: "Voltar ao login" })).toBeDefined();
  });
});
