import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ThemeProvider } from "../../theme/ThemeProvider";
import { AppShell } from "./AppShell";

afterEach(cleanup);

describe("AppShell", () => {
  it("reúne navegação, conta e aparência em uma única estrutura", () => {
    render(
      <ThemeProvider>
        <AppShell current="envelopes" email="pessoa@example.com" onLogout={vi.fn()}>
          <h1>Verbas</h1>
        </AppShell>
      </ThemeProvider>,
    );

    expect(screen.getByRole("navigation", { name: "Navegação principal" })).toBeDefined();
    expect(screen.getByRole("link", { name: "Verbas" }).getAttribute("aria-current")).toBe("page");
    expect(screen.getByLabelText("Tema")).toBeDefined();
    expect(screen.getByText("pessoa@example.com")).toBeDefined();
    expect(screen.getByRole("main").querySelector("h1")?.textContent).toBe("Verbas");
  });

  it("distingue Histórico de Gastos anuais e marca a página atual", () => {
    render(
      <ThemeProvider>
        <AppShell current="expenses" email="pessoa@example.com" onLogout={vi.fn()}>
          <h1>Histórico</h1>
        </AppShell>
      </ThemeProvider>,
    );

    const historico = screen.getByRole("link", { name: /Histórico/ });
    const anuais = screen.getByRole("link", { name: /Gastos anuais/ });

    expect(historico.getAttribute("href")).toBe("/gastos");
    expect(anuais.getAttribute("href")).toBe("/gastos-anuais");
    expect(historico).not.toBe(anuais);
    expect(historico.getAttribute("aria-current")).toBe("page");
    expect(anuais.getAttribute("aria-current")).toBeNull();
  });
});
