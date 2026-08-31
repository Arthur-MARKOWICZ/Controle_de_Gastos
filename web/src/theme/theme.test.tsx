import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ThemeProvider, useTheme } from "./ThemeProvider";
import { resolveTheme, THEME_STORAGE_KEY } from "./theme";

type Listener = (event: MediaQueryListEvent) => void;

function ThemeProbe() {
  const { preference, resolvedTheme, setPreference } = useTheme();
  return (
    <>
      <output aria-label="Preferência">{preference}</output>
      <output aria-label="Tema efetivo">{resolvedTheme}</output>
      <button type="button" onClick={() => setPreference("dark")}>Usar escuro</button>
    </>
  );
}

describe("tema da web", () => {
  let systemDark = false;
  let listener: Listener | undefined;

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute("data-theme");
    document.documentElement.removeAttribute("data-theme-preference");
    vi.stubGlobal("matchMedia", vi.fn(() => ({
      matches: systemDark,
      media: "(prefers-color-scheme: dark)",
      onchange: null,
      addEventListener: (_event: string, next: Listener) => { listener = next; },
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    systemDark = false;
    listener = undefined;
  });

  it("resolve a preferência do sistema sem alterar escolhas explícitas", () => {
    expect(resolveTheme("system", true)).toBe("dark");
    expect(resolveTheme("system", false)).toBe("light");
    expect(resolveTheme("light", true)).toBe("light");
    expect(resolveTheme("dark", false)).toBe("dark");
  });

  it("restaura e persiste a preferência local", () => {
    localStorage.setItem(THEME_STORAGE_KEY, "light");
    render(<ThemeProvider><ThemeProbe /></ThemeProvider>);

    expect(screen.getByLabelText("Preferência").textContent).toBe("light");
    fireEvent.click(screen.getByRole("button", { name: "Usar escuro" }));

    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe("dark");
    expect(document.documentElement.dataset.theme).toBe("dark");
  });

  it("acompanha mudanças do sistema apenas na preferência automática", () => {
    render(<ThemeProvider><ThemeProbe /></ThemeProvider>);
    expect(screen.getByLabelText("Tema efetivo").textContent).toBe("light");

    systemDark = true;
    act(() => listener?.({ matches: true } as MediaQueryListEvent));

    expect(screen.getByLabelText("Tema efetivo").textContent).toBe("dark");
    expect(document.documentElement.dataset.theme).toBe("dark");
  });

  it("sincroniza a preferência alterada em outra aba", () => {
    render(<ThemeProvider><ThemeProbe /></ThemeProvider>);

    act(() => window.dispatchEvent(new StorageEvent("storage", { key: THEME_STORAGE_KEY, newValue: "dark" })));

    expect(screen.getByLabelText("Preferência").textContent).toBe("dark");
  });

  it("mantém a troca de tema quando o armazenamento local está indisponível", () => {
    const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("Storage blocked", "SecurityError");
    });
    render(<ThemeProvider><ThemeProbe /></ThemeProvider>);

    fireEvent.click(screen.getByRole("button", { name: "Usar escuro" }));

    expect(setItem).toHaveBeenCalledWith(THEME_STORAGE_KEY, "dark");
    expect(screen.getByLabelText("Tema efetivo").textContent).toBe("dark");
    expect(document.documentElement.dataset.theme).toBe("dark");
  });
});
