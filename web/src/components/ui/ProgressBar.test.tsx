import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { ProgressBar } from "./ProgressBar";

afterEach(cleanup);

describe("ProgressBar", () => {
  it("anuncia estado negativo por texto", () => {
    render(
      <ProgressBar
        label="Progresso de Mercado"
        value={0}
        purpose="LIMIT"
        negative
        valueText="Saldo negativo"
      />,
    );

    const bar = screen.getByRole("progressbar", { name: "Progresso de Mercado" });
    expect(bar.getAttribute("aria-valuetext")).toBe("Saldo negativo");
    expect(bar.getAttribute("data-negative")).toBe("true");
    expect(bar.getAttribute("data-purpose")).toBe("LIMIT");
  });
});
