import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Dashboard } from "./Dashboard";

afterEach(cleanup);

describe("Dashboard", () => {
  it("identifica a visão geral e expõe a conta autenticada", () => {
    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(screen.getByRole("heading", { level: 1, name: "Visão geral" })).toBeDefined();
    expect(screen.getByText("pessoa@example.com")).toBeDefined();
    expect(screen.getByRole("button", { name: "Sair da conta" })).toBeDefined();
  });

  it("expõe um indicador de progresso acessível para cada verba", () => {
    render(<Dashboard email="pessoa@example.com" onLogout={vi.fn()} />);

    expect(screen.getAllByRole("progressbar")).toHaveLength(3);
    expect(screen.getByRole("progressbar", { name: "Progresso de Combustível" })
      .getAttribute("value")).toBe("60");
  });
});
