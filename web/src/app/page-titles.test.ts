import { describe, expect, it } from "vitest";
import { PAGE_TITLES } from "./page-titles";

describe("PAGE_TITLES", () => {
  it("define um título distinto para cada rota autenticada e de autenticação", () => {
    const titles = Object.values(PAGE_TITLES);
    expect(new Set(titles).size).toBe(titles.length);
    expect(PAGE_TITLES.home).toBe("Visão geral");
    expect(PAGE_TITLES.verbas).toBe("Verbas");
    expect(PAGE_TITLES.metas).toBe("Metas");
    expect(PAGE_TITLES.gastos).toBe("Gastos");
    expect(PAGE_TITLES.gastosAnuais).toBe("Gastos anuais");
    expect(PAGE_TITLES.relatorios).toBe("Relatórios");
    expect(PAGE_TITLES.configuracoes).toBe("Configurações");
    expect(PAGE_TITLES.seguranca).toBe("Segurança");
    expect(PAGE_TITLES.recuperarSenha).toBe("Recuperar senha");
    expect(PAGE_TITLES.redefinirSenha).toBe("Redefinir senha");
    expect(PAGE_TITLES.oauthCallback).toBe("Entrando…");
  });
});
