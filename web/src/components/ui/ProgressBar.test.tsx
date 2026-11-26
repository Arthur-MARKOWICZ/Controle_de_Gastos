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

  it("usa máximo 100 e estado não negativo por padrão", () => {
    render(<ProgressBar label="Uso de Casa" value={40} />);

    const bar = screen.getByRole("progressbar", { name: "Uso de Casa" });
    expect(bar.getAttribute("max")).toBe("100");
    expect(bar.getAttribute("value")).toBe("40");
    expect(bar.getAttribute("data-negative")).toBe("false");
    expect(bar.getAttribute("aria-valuetext")).toBeNull();
  });

  it("respeita máximo personalizado", () => {
    render(<ProgressBar label="Meta" value={250} max={1000} />);

    expect(screen.getByRole("progressbar", { name: "Meta" }).getAttribute("max")).toBe("1000");
  });
});
