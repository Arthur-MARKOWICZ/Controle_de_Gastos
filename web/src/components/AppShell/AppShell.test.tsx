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
});
