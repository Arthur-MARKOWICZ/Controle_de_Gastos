import { describe, expect, it } from "vitest";
import { subtractBRL } from "./money";

describe("subtractBRL", () => {
  it("subtrai o gasto do saldo disponível", () => {
    expect(subtractBRL("240.00", "50.00")).toBe("190.00");
  });

  it("zera o saldo quando o gasto é igual ao disponível", () => {
    expect(subtractBRL("240.00", "240.00")).toBe("0.00");
  });

  it("resulta em saldo negativo quando o gasto supera o disponível", () => {
    expect(subtractBRL("240.00", "300.50")).toBe("-60.50");
  });

  it("aprofunda um saldo que já era negativo", () => {
    expect(subtractBRL("-10.00", "5.25")).toBe("-15.25");
  });

  it("recupera um saldo negativo até o positivo", () => {
    expect(subtractBRL("-0.05", "-0.10")).toBe("0.05");
  });

  it("mantém precisão exata além do limite de ponto flutuante", () => {
    expect(subtractBRL("99999999999999999.99", "0.01")).toBe("99999999999999999.98");
  });

  it("rejeita valores fora do formato decimal com duas casas", () => {
    expect(subtractBRL("240", "50.00")).toBeNull();
    expect(subtractBRL("240.00", "")).toBeNull();
  });
});
