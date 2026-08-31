import { cleanup, render } from "@testing-library/react";
import axe from "axe-core";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthScreen } from "./AuthScreen/AuthScreen";
import { ThemeProvider } from "../theme/ThemeProvider";
import { THEME_STORAGE_KEY, type ThemePreference } from "../theme/theme";

afterEach(() => {
  cleanup();
  localStorage.clear();
});

describe.each<ThemePreference>(["light", "dark"])("acessibilidade no tema %s", (theme) => {
  it("não introduz violações estruturais na autenticação", async () => {
    localStorage.setItem(THEME_STORAGE_KEY, theme);
    render(
      <ThemeProvider>
        <AuthScreen onLogin={vi.fn()} onRegister={vi.fn()} />
      </ThemeProvider>,
    );

    const result = await axe.run(document.body, {
      rules: {
        // JSDOM não calcula contraste; ele é validado no navegador real.
        "color-contrast": { enabled: false },
      },
    });

    expect(result.violations).toEqual([]);
  });
});
