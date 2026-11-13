import { describe, expect, it } from "vitest";
import {
  formatBRL,
  isPlainAmount,
  maskPtBR,
  subtractBRL,
  toPlainAmount,
} from "./money";

describe("9.1 máscara pt-BR do campo de valor", () => {
  it("monta o valor a partir dos dígitos digitados", () => {
    expect(maskPtBR("4")).toBe("0,04");
    expect(maskPtBR("45")).toBe("0,45");
    expect(maskPtBR("459")).toBe("4,59");
    expect(maskPtBR("4590")).toBe("45,90");
  });

  it("agrupa milhar com ponto", () => {
    expect(maskPtBR("123456")).toBe("1.234,56");
    expect(maskPtBR("800000")).toBe("8.000,00");
  });

  it("ignora o que não é dígito", () => {
    expect(maskPtBR("R$ 45,90")).toBe("45,90");
    expect(maskPtBR("45a9b0")).toBe("45,90");
  });

  it("campo vazio continua vazio", () => {
    expect(maskPtBR("")).toBe("");
    expect(maskPtBR("abc")).toBe("");
  });
});

describe("9.1 serialização para a API", () => {
  it('"45,90" digitado vira "45.90" enviado', () => {
    expect(toPlainAmount("45,90")).toBe("45.90");
  });

  it("desfaz o agrupamento de milhar", () => {
    expect(toPlainAmount("1.234,56")).toBe("1234.56");
    expect(toPlainAmount("8.000,00")).toBe("8000.00");
  });

  it("valor sem duas casas não produz string de envio", () => {
    expect(toPlainAmount("45")).toBeNull();
    expect(toPlainAmount("45,9")).toBeNull();
    expect(toPlainAmount("45,")).toBeNull();
    expect(toPlainAmount("")).toBeNull();
    expect(toPlainAmount("abc")).toBeNull();
  });

  it("não aceita valor zero nem negativo como gasto", () => {
    expect(toPlainAmount("0,00")).toBeNull();
    expect(toPlainAmount("-5,00")).toBeNull();
  });

  it("o que sai da serialização é sempre aceito pelo contrato da API", () => {
    for (const digitado of ["45,90", "1.234,56", "0,01", "8.000,00"]) {
      const plain = toPlainAmount(digitado);
      expect(plain).not.toBeNull();
      expect(isPlainAmount(plain as string)).toBe(true);
    }
  });
});

describe("9.3 saldo projetado", () => {
  it('"10.00" menos "25.00" dá "-15.00"', () => {
    expect(subtractBRL("10.00", "25.00")).toBe("-15.00");
  });

  it("não arredonda os centavos", () => {
    expect(subtractBRL("10.05", "10.06")).toBe("-0.01");
    expect(subtractBRL("1200.00", "45.90")).toBe("1154.10");
    expect(subtractBRL("0.10", "0.03")).toBe("0.07");
  });

  it("parte de saldo já negativo", () => {
    expect(subtractBRL("-15.00", "5.00")).toBe("-20.00");
  });

  it("subtração exata chega a zero sem sinal", () => {
    expect(subtractBRL("25.00", "25.00")).toBe("0.00");
  });

  it("valor fora do formato não produz projeção", () => {
    expect(subtractBRL("10", "5.00")).toBeNull();
    expect(subtractBRL("10.00", "5")).toBeNull();
  });
});

describe("apresentação do valor", () => {
  /** `Intl` separa o símbolo com espaço inseparável; o teste é sobre o valor. */
  const normalize = (value: string) => value.replace(/\u00a0/g, " ");

  it("formata em BRL para leitura", () => {
    expect(normalize(formatBRL("8000.00"))).toBe("R$ 8.000,00");
    expect(normalize(formatBRL("-15.00"))).toBe("-R$ 15,00");
  });

  it("valor fora do formato é devolvido como está", () => {
    expect(formatBRL("abc")).toBe("abc");
  });
});
