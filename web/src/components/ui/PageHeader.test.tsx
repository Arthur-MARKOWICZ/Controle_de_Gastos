import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { PageHeader } from "./PageHeader";

afterEach(cleanup);

describe("PageHeader", () => {
  it("renderiza sobretítulo, título, descrição e ações", () => {
    render(
      <PageHeader
        eyebrow="Outubro 2026"
        title="Visão geral"
        description="Resumo do mês"
        actions={<button type="button">Nova verba</button>}
      />,
    );

    expect(screen.getByText("Outubro 2026")).toBeDefined();
    expect(screen.getByRole("heading", { name: "Visão geral" })).toBeDefined();
    expect(screen.getByText("Resumo do mês")).toBeDefined();
    expect(screen.getByRole("button", { name: "Nova verba" })).toBeDefined();
  });
});
