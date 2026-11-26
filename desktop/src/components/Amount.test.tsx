import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Amount } from "./Amount";
import { MonthSelector } from "./MonthSelector";

afterEach(cleanup);

describe("Amount", () => {
  it("mostra em reais e guarda a string exata da API", () => {
    const { container } = render(<Amount plain="8000.00" label="Renda" />);

    const span = container.querySelector("span")!;
    expect(span.textContent).toMatch(/R\$\s*8\.000,00/);
    expect(span.getAttribute("data-amount")).toBe("8000.00");
    expect(span.getAttribute("aria-label")).toBe("Renda");
  });

  it("saldo negativo aparece com sinal", () => {
    const { container } = render(<Amount plain="-45.90" />);

    expect(container.textContent).toMatch(/-\s*R\$\s*45,90/);
  });

  it("valor zero aparece com duas casas", () => {
    const { container } = render(<Amount plain="0.00" />);

    expect(container.textContent).toMatch(/R\$\s*0,00/);
  });

  it("valor fora do contrato não é arredondado nem reformatado", () => {
    const { container } = render(<Amount plain="10.555" />);

    expect(container.textContent).toBe("10.555");
    expect(container.querySelector("span")!.getAttribute("data-amount")).toBe("10.555");
  });

  it("escala de uma casa também não é completada", () => {
    const { container } = render(<Amount plain="10.5" />);

    expect(container.textContent).toBe("10.5");
  });
});

describe("MonthSelector", () => {
  it("anuncia o mês em pt-BR", () => {
    render(<MonthSelector month="2026-11" onChange={vi.fn()} />);

    expect(screen.getByText("novembro de 2026").getAttribute("data-month")).toBe("2026-11");
  });

  it("avança e volta atravessando o ano", () => {
    const onChange = vi.fn();
    const { rerender } = render(<MonthSelector month="2026-12" onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: "Mês seguinte" }));
    rerender(<MonthSelector month="2026-01" onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: "Mês anterior" }));

    expect(onChange.mock.calls).toEqual([["2027-01"], ["2025-12"]]);
  });

  it("mês inválido não vira outro mês ao navegar", () => {
    const onChange = vi.fn();
    render(<MonthSelector month="2026-13" onChange={onChange} />);

    fireEvent.click(screen.getByRole("button", { name: "Mês seguinte" }));

    expect(onChange).toHaveBeenCalledWith("2026-13");
    expect(screen.getByText("2026-13")).toBeDefined();
  });
});
