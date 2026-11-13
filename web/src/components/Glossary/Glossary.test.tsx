import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { Glossary } from "./Glossary";

afterEach(cleanup);

/** Conteúdo estático: os testes garantem que todo tipo de verba da API está explicado. */
describe("Glossary", () => {
  it("explica os conceitos centrais", () => {
    render(<Glossary />);

    const core = within(screen.getByLabelText("Conceitos centrais"));
    expect(core.getAllByRole("term").map((t) => t.textContent)).toEqual(["Verba", "Não alocado", "Saldo acumulado"]);
  });

  it("explica os cinco tipos de verba aceitos pela API", () => {
    render(<Glossary />);

    const types = within(screen.getByLabelText("Tipos de verba"));
    expect(types.getAllByRole("term")).toHaveLength(5);
  });

  it("todo termo tem uma descrição não vazia", () => {
    render(<Glossary />);

    screen.getAllByRole("definition").forEach((d) => expect(d.textContent?.trim().length).toBeGreaterThan(10));
  });
});
