import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App } from "./App";
import { ALERTS_PATH, NO_PENDING, PENDING_PATH, summary, summaryPath } from "./lib/fixtures";
import { currentMonthSaoPaulo } from "./lib/dates";
import { installFakeIpc, resetIpc } from "./lib/test-ipc";
import type { FakeIpc } from "./lib/test-ipc";

const MES = currentMonthSaoPaulo();

afterEach(() => {
  cleanup();
  resetIpc();
  document.documentElement.removeAttribute("data-theme");
  document.documentElement.removeAttribute("data-theme-preference");
});

type Opcoes = {
  sessao?: { email: string } | null;
  tema?: "SYSTEM" | "LIGHT" | "DARK";
};

function montar({ sessao = { email: "titular@exemplo.com" }, tema = "SYSTEM" }: Opcoes = {}): FakeIpc {
  const ipc = installFakeIpc()
    .on("restore", () => ({ session: sessao }))
    .on("theme_get", () => tema)
    .on("theme_set", () => null)
    .on("logout", () => null)
    .onApi(summaryPath(MES), { status: 200, body: summary() })
    .onApi(PENDING_PATH, { status: 200, body: NO_PENDING })
    .onApi(ALERTS_PATH, { status: 200, body: { items: [] } });
  render(<App />);
  return ipc;
}

describe("5.3 restauração de sessão no arranque", () => {
  it("refresh válido restaura a sessão sem pedir a senha", async () => {
    montar();

    expect(await screen.findByRole("heading", { name: "Visão geral" })).toBeTruthy();
    expect(screen.getByText("titular@exemplo.com")).toBeTruthy();
    expect(screen.queryByLabelText("Senha")).toBeNull();
  });

  it("sem refresh válido mostra a tela de login", async () => {
    montar({ sessao: null });

    expect(await screen.findByRole("heading", { name: "Entrar" })).toBeTruthy();
    expect(screen.getByLabelText("Senha")).toBeTruthy();
  });

  it("cofre indisponível no arranque avisa e cai no login", async () => {
    installFakeIpc()
      .on("restore", () => {
        throw {
          message: "Nenhum cofre de senhas disponível. Instale e habilite um serviço compatível.",
          authRequired: false,
        };
      })
      .on("theme_get", () => "SYSTEM");
    render(<App />);

    expect(await screen.findByRole("heading", { name: "Entrar" })).toBeTruthy();
    expect(screen.getByRole("status")).toHaveTextContent("Nenhum cofre de senhas disponível");
  });
});

describe("6.1 chrome e navegação da janela autenticada", () => {
  it("a navegação lista Visão geral e Verbas e anuncia a tela atual", async () => {
    montar();
    await screen.findByRole("heading", { name: "Visão geral" });

    const nav = screen.getByRole("navigation", { name: "Navegação principal" });
    const destinos = Array.from(nav.querySelectorAll("button")).map((b) => b.textContent);
    expect(destinos).toEqual(["Visão geral", "Verbas"]);

    expect(screen.getByRole("button", { name: "Visão geral" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("button", { name: "Verbas" })).not.toHaveAttribute("aria-current");
  });

  it("navegar para Verbas move o aria-current", async () => {
    const user = userEvent.setup();
    montar();
    await screen.findByRole("heading", { name: "Visão geral" });

    await user.click(screen.getByRole("button", { name: "Verbas" }));

    expect(await screen.findByRole("heading", { name: "Verbas" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Verbas" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("button", { name: "Visão geral" })).not.toHaveAttribute(
      "aria-current",
    );
  });

  it("oferece o link de pular para o conteúdo principal", async () => {
    montar();
    await screen.findByRole("heading", { name: "Visão geral" });

    const skip = screen.getByRole("link", { name: "Pular para o conteúdo" });
    expect(skip).toHaveAttribute("href", "#conteudo");
    const main = screen.getByRole("main");
    expect(main.id).toBe("conteudo");
    // O destino recebe foco por programa, como manda o padrão de skip link.
    expect(main).toHaveAttribute("tabindex", "-1");
  });
});

describe("6.2 tema local", () => {
  it("escolher o tema escuro troca as superfícies e grava no dispositivo", async () => {
    const user = userEvent.setup();
    const ipc = montar();
    await screen.findByRole("heading", { name: "Visão geral" });

    await user.selectOptions(screen.getByLabelText("Tema"), "DARK");

    await waitFor(() =>
      expect(document.documentElement.dataset.theme).toBe("dark"),
    );
    expect(document.documentElement.dataset.themePreference).toBe("DARK");
    const gravacao = ipc.calls.find((c) => c.command === "theme_set");
    expect(gravacao?.args).toEqual({ mode: "DARK" });
    // Preferência de apresentação não vai à API.
    expect(ipc.pathsCalled().some((p) => p.includes("theme"))).toBe(false);
  });

  it("escolha escura guardada no dispositivo vale no arranque seguinte", async () => {
    montar({ tema: "DARK" });
    await screen.findByRole("heading", { name: "Visão geral" });

    await waitFor(() => expect(document.documentElement.dataset.theme).toBe("dark"));
    expect(screen.getByLabelText("Tema")).toHaveValue("DARK");
  });

  it("sem preferência guardada o tema segue o sistema", async () => {
    const matchMedia = vi.fn().mockReturnValue({
      matches: true, // sistema em escuro
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    });
    vi.stubGlobal("matchMedia", matchMedia);

    montar({ tema: "SYSTEM" });
    await screen.findByRole("heading", { name: "Visão geral" });

    await waitFor(() => expect(document.documentElement.dataset.theme).toBe("dark"));
    expect(document.documentElement.dataset.themePreference).toBe("SYSTEM");
    expect(matchMedia).toHaveBeenCalledWith("(prefers-color-scheme: dark)");
    vi.unstubAllGlobals();
  });
});

describe("6.3 sair", () => {
  it("o botão Sair no rodapé volta ao login", async () => {
    const user = userEvent.setup();
    const ipc = montar();
    await screen.findByRole("heading", { name: "Visão geral" });

    await user.click(screen.getByRole("button", { name: "Sair" }));

    expect(await screen.findByRole("heading", { name: "Entrar" })).toBeTruthy();
    expect(ipc.calls.some((c) => c.command === "logout")).toBe(true);
  });
});
