import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ThemeProvider } from "../../theme/ThemeProvider";
import { AppShell } from "./AppShell";

afterEach(cleanup);

describe("AppShell", () => {
  it("reúne navegação, seletor de área, botão de gasto rápido, conta e aparência", () => {
    const onQuickExpense = vi.fn();
    render(
      <ThemeProvider>
        <AppShell
          current="envelopes"
          email="pessoa@example.com"
          month="2026-08"
          onLogout={vi.fn()}
          onQuickExpense={onQuickExpense}
        >
          <h1>Verbas</h1>
        </AppShell>
      </ThemeProvider>,
    );

    expect(screen.getByRole("navigation", { name: "Navegação principal" })).toBeDefined();
    expect(screen.getByText("Dia a dia")).toBeDefined();
    expect(screen.getByRole("link", { name: "Verbas" }).getAttribute("aria-current")).toBe("page");
    expect(screen.getByRole("link", { name: "Verbas" }).getAttribute("href")).toBe("/verbas?month=2026-08");
    expect(screen.getByRole("link", { name: "Orçamento" }).getAttribute("aria-current")).toBe("true");
    expect(screen.getByRole("link", { name: "Patrimônio" }).getAttribute("href")).toBe("/carteira");
    expect(screen.getByLabelText("Tema")).toBeDefined();
    expect(screen.getByText("pessoa@example.com")).toBeDefined();
    expect(screen.getByRole("main").querySelector("h1")?.textContent).toBe("Verbas");

    fireEvent.click(screen.getByRole("button", { name: /Registrar gasto/i }));
    expect(onQuickExpense).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText("Verbas, visão geral").querySelector("svg")).toBeTruthy();
  });

  it("aciona o lançamento rápido pelo atalho de teclado N no contexto de Orçamento", () => {
    const onQuickExpense = vi.fn();
    render(
      <ThemeProvider>
        <AppShell current="overview" email="pessoa@example.com" onLogout={vi.fn()} onQuickExpense={onQuickExpense}>
          <h1>Visão geral</h1>
        </AppShell>
      </ThemeProvider>,
    );

    fireEvent.keyDown(window, { key: "n" });
    expect(onQuickExpense).toHaveBeenCalledTimes(1);
  });

  it("aciona o lançamento rápido com Ctrl+N e impede o atalho nativo", () => {
    const onQuickExpense = vi.fn();
    render(
      <ThemeProvider>
        <AppShell current="overview" email="pessoa@example.com" onLogout={vi.fn()} onQuickExpense={onQuickExpense}>
          <h1>Visão geral</h1>
        </AppShell>
      </ThemeProvider>,
    );

    const event = new KeyboardEvent("keydown", { key: "n", ctrlKey: true, cancelable: true });
    const prevented = !window.dispatchEvent(event) || event.defaultPrevented;
    expect(onQuickExpense).toHaveBeenCalledTimes(1);
    expect(prevented).toBe(true);
  });

  it("não dispara o atalho N quando o foco está em um campo de texto", () => {
    const onQuickExpense = vi.fn();
    render(
      <ThemeProvider>
        <AppShell current="overview" email="pessoa@example.com" onLogout={vi.fn()} onQuickExpense={onQuickExpense}>
          <input aria-label="Busca" />
        </AppShell>
      </ThemeProvider>,
    );

    const input = screen.getByLabelText("Busca");
    input.focus();
    fireEvent.keyDown(input, { key: "n" });
    fireEvent.keyDown(input, { key: "n", ctrlKey: true });
    expect(onQuickExpense).not.toHaveBeenCalled();
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

  it("alterna para a área Patrimônio em /carteira exibindo Carteira antes de Configurações", () => {
    render(
      <ThemeProvider>
        <AppShell current="carteira" email="pessoa@example.com" onLogout={vi.fn()}>
          <h1>Carteira</h1>
        </AppShell>
      </ThemeProvider>,
    );

    expect(screen.getByRole("link", { name: "Patrimônio" }).getAttribute("aria-current")).toBe("true");
    const carteira = screen.getByRole("link", { name: "Carteira" });
    expect(carteira.getAttribute("href")).toBe("/carteira");
    expect(carteira.getAttribute("aria-current")).toBe("page");
    expect(screen.queryByRole("link", { name: "Verbas" })).toBeNull();

    const navLinks = screen.getByRole("navigation", { name: "Navegação principal" }).querySelectorAll("a");
    const hrefs = Array.from(navLinks).map((link) => link.getAttribute("href"));
    expect(hrefs.indexOf("/carteira")).toBeLessThan(hrefs.indexOf("/configuracoes"));
  });
});
