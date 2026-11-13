import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ThemeSelector } from "./ThemeSelector";

const theme = vi.hoisted(() => ({ preference: "system", setPreference: vi.fn() }));
vi.mock("./ThemeProvider", () => ({ useTheme: () => theme }));

afterEach(() => {
  cleanup();
  theme.setPreference.mockReset();
  theme.preference = "system";
});

describe("ThemeSelector", () => {
  it("mostra a preferência atual selecionada", () => {
    theme.preference = "dark";
    render(<ThemeSelector />);

    expect((screen.getByLabelText("Tema") as HTMLSelectElement).value).toBe("dark");
  });

  it("troca a preferência ao escolher outra opção", () => {
    render(<ThemeSelector />);

    fireEvent.change(screen.getByLabelText("Tema"), { target: { value: "light" } });

    expect(theme.setPreference).toHaveBeenCalledWith("light");
  });

  it("oferece apenas Sistema, Claro e Escuro", () => {
    render(<ThemeSelector />);

    expect(screen.getAllByRole("option").map((o) => o.textContent)).toEqual(["Sistema", "Claro", "Escuro"]);
  });

  it("modo compacto esconde o rótulo visível mas mantém o nome acessível", () => {
    render(<ThemeSelector compact />);

    expect(screen.queryByText("Aparência")).toBeNull();
    expect(screen.getByLabelText("Tema")).toBeDefined();
  });

  it("modo padrão mostra o rótulo Aparência", () => {
    render(<ThemeSelector />);

    expect(screen.getByText("Aparência")).toBeDefined();
  });

  it("valor desconhecido não é aceito pelo select e não chama setPreference", () => {
    render(<ThemeSelector />);

    fireEvent.change(screen.getByLabelText("Tema"), { target: { value: "neon" } });

    expect(theme.setPreference).not.toHaveBeenCalledWith("neon");
  });

  it("sem provedor de tema configurado o padrão continua sendo Sistema", () => {
    render(<ThemeSelector />);

    expect((screen.getByLabelText("Tema") as HTMLSelectElement).value).toBe("system");
  });
});
