import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { Field } from "./Field";

afterEach(cleanup);

describe("Field", () => {
  it("liga dica e erro ao controle via aria-describedby", () => {
    render(
      <Field id="valor" label="Valor" hint="Use duas casas" error="Valor inválido">
        <input />
      </Field>,
    );

    const input = screen.getByLabelText("Valor");
    expect(input.getAttribute("aria-invalid")).toBe("true");
    const describedBy = input.getAttribute("aria-describedby") ?? "";
    expect(describedBy.split(" ")).toEqual(expect.arrayContaining(["valor-error", "valor-hint"]));
    expect(screen.getByText("Valor inválido").id).toBe("valor-error");
    expect(screen.getByText("Use duas casas").id).toBe("valor-hint");
  });
});
