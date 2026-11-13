import { afterEach, describe, expect, it } from "vitest";
import { focusField } from "./form-errors";

afterEach(() => {
  document.body.innerHTML = "";
});

describe("focusField", () => {
  it("move o foco para o campo inválido pelo id", () => {
    document.body.innerHTML = '<input id="valor" /><input id="data" />';

    focusField("data");

    expect(document.activeElement?.id).toBe("data");
  });

  it("não quebra quando o campo não existe", () => {
    document.body.innerHTML = '<input id="valor" />';

    expect(() => focusField("inexistente")).not.toThrow();
    expect(document.activeElement).toBe(document.body);
  });

  it("não move o foco para o campo errado com id parecido", () => {
    document.body.innerHTML = '<input id="valor-total" /><input id="valor" />';

    focusField("valor");

    expect(document.activeElement?.id).toBe("valor");
  });

  it("ignora id vazio sem lançar erro", () => {
    expect(() => focusField("")).not.toThrow();
  });

  it("aceita elementos focáveis que não são input", () => {
    document.body.innerHTML = '<select id="tipo"><option>a</option></select>';

    focusField("tipo");

    expect(document.activeElement?.id).toBe("tipo");
  });

  it("id com caracteres especiais não é interpretado como seletor", () => {
    document.body.innerHTML = '<input id="a.b" />';

    expect(() => focusField("a.b")).not.toThrow();
    expect(document.activeElement?.id).toBe("a.b");
  });

  it("sem document (renderização no servidor) não faz nada", () => {
    const original = globalThis.document;
    // @ts-expect-error simula ambiente sem DOM
    delete globalThis.document;
    try {
      expect(() => focusField("x")).not.toThrow();
    } finally {
      globalThis.document = original;
    }
  });
});
