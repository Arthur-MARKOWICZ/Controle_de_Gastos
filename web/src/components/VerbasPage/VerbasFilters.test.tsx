import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { VerbasFilters } from "./VerbasFilters";

afterEach(cleanup);

function setup(overrides: Partial<Parameters<typeof VerbasFilters>[0]> = {}) {
  const props = {
    active: "ALL" as const,
    onChangePurpose: vi.fn(),
    counts: { ALL: 3, LIMIT: 2, FIXED: 1 },
    query: "",
    onQuery: vi.fn(),
    sort: "progress" as const,
    onSort: vi.fn(),
    ...overrides,
  };
  render(<VerbasFilters {...props} />);
  return props;
}

describe("VerbasFilters", () => {
  it("mostra as abas com a contagem e marca a ativa", () => {
    setup({ active: "LIMIT" });

    expect(screen.getByRole("tab", { name: "Limite de gasto (2)" }).getAttribute("aria-selected")).toBe("true");
    expect(screen.getByRole("tab", { name: "Todas (3)" }).getAttribute("aria-selected")).toBe("false");
  });

  it("troca de natureza ao clicar na aba", () => {
    const props = setup();

    fireEvent.click(screen.getByRole("tab", { name: "Compromisso fixo (1)" }));

    expect(props.onChangePurpose).toHaveBeenCalledWith("FIXED");
  });

  it("repassa o texto da busca", () => {
    const props = setup();

    fireEvent.change(screen.getByLabelText("Buscar verba por nome"), { target: { value: "merc" } });

    expect(props.onQuery).toHaveBeenCalledWith("merc");
  });

  it("repassa a ordenação escolhida", () => {
    const props = setup();

    fireEvent.change(screen.getByLabelText("Ordenar verbas"), { target: { value: "nome" } });

    expect(props.onSort).toHaveBeenCalledWith("nome");
  });

  it("contagem ausente aparece como zero em vez de undefined", () => {
    setup({ counts: { ALL: 1 } as never });

    expect(screen.getByRole("tab", { name: "Limite de gasto (0)" })).toBeDefined();
  });

  it("busca com caracteres especiais é repassada como texto, sem interpretar", () => {
    const props = setup();

    fireEvent.change(screen.getByLabelText("Buscar verba por nome"), { target: { value: "<script>" } });

    expect(props.onQuery).toHaveBeenCalledWith("<script>");
    expect(document.querySelector("script")).toBeNull();
  });

  it("botões das abas não enviam formulário", () => {
    setup();

    screen.getAllByRole("tab").forEach((tab) => expect(tab.getAttribute("type")).toBe("button"));
  });
});
