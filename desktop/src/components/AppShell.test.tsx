import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppShell } from "./AppShell";
import { ThemeProvider, useTheme } from "./ThemeProvider";

afterEach(cleanup);

describe("useTheme", () => {
  it("exige ThemeProvider acima na árvore", () => {
    function Probe() {
      useTheme();
      return null;
    }
    expect(() => render(<Probe />)).toThrow(/ThemeProvider/);
  });
});


describe("AppShell", () => {
  it("mostra o lembrete diário quando a ação é oferecida", () => {
    const onOpenReminder = vi.fn();
    render(
      <ThemeProvider>
        <AppShell
          email="ana@example.com"
          current="overview"
          onNavigate={vi.fn()}
          onLogout={vi.fn()}
          onOpenReminder={onOpenReminder}
        >
          <p>conteúdo</p>
        </AppShell>
      </ThemeProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Lembrete diário" }));
    expect(onOpenReminder).toHaveBeenCalledTimes(1);
  });

  it("omite o lembrete quando a ação não é oferecida", () => {
    render(
      <ThemeProvider>
        <AppShell
          email="ana@example.com"
          current="envelopes"
          onNavigate={vi.fn()}
          onLogout={vi.fn()}
        >
          <p>conteúdo</p>
        </AppShell>
      </ThemeProvider>,
    );

    expect(screen.queryByRole("button", { name: "Lembrete diário" })).toBeNull();
    expect(screen.getByRole("button", { name: "Sair" })).toBeDefined();
  });
});
