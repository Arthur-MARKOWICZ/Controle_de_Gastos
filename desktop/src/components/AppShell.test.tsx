import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppShell } from "./AppShell";
import { ThemeProvider, useTheme } from "./ThemeProvider";
import { installFakeIpc, resetIpc } from "../lib/test-ipc";

afterEach(() => {
  cleanup();
  resetIpc();
});

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

  it("liga o autostart local sem chamar a API", async () => {
    const ipc = installFakeIpc()
      .on("theme_get", () => "SYSTEM")
      .on("autostart_get", () => false)
      .on("autostart_set", () => undefined);

    render(
      <ThemeProvider>
        <AppShell
          email="ana@example.com"
          current="overview"
          onNavigate={vi.fn()}
          onLogout={vi.fn()}
        >
          <p>conteúdo</p>
        </AppShell>
      </ThemeProvider>,
    );

    const checkbox = await screen.findByLabelText("Iniciar com o sistema");
    expect(checkbox).not.toBeChecked();
    fireEvent.click(checkbox);

    await waitFor(() => {
      expect(ipc.calls.some((call) => call.command === "autostart_set" && call.args.enabled === true)).toBe(true);
    });
    expect(ipc.calls.every((call) => call.command !== "api")).toBe(true);
  });

  it("reverte o checkbox se o autostart_set falhar", async () => {
    installFakeIpc()
      .on("theme_get", () => "SYSTEM")
      .on("autostart_get", () => false)
      .on("autostart_set", () => {
        throw new Error("plugin indisponível");
      });

    render(
      <ThemeProvider>
        <AppShell
          email="ana@example.com"
          current="overview"
          onNavigate={vi.fn()}
          onLogout={vi.fn()}
        >
          <p>conteúdo</p>
        </AppShell>
      </ThemeProvider>,
    );

    const checkbox = await screen.findByLabelText("Iniciar com o sistema");
    fireEvent.click(checkbox);

    await waitFor(() => {
      expect(checkbox).not.toBeChecked();
    });
  });
});
