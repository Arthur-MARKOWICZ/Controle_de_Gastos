import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * 8.4 — Ícones Phosphor outline a 20px.
 *
 * O ícone `Envelope` do Phosphor é um envelope de correspondência; usá-lo para
 * "verba" confundiria o usuário. Este teste trava a escolha.
 */

const SRC = join(import.meta.dirname, "..");

function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return sourceFiles(path);
    return /\.tsx?$/.test(entry.name) && !entry.name.includes(".test.")
      ? [path]
      : [];
  });
}

const sources = sourceFiles(SRC).map((path) => ({
  path,
  code: readFileSync(path, "utf8"),
}));

const phosphorImports = sources
  .flatMap(({ code }) =>
    Array.from(code.matchAll(/import\s*\{([^}]+)\}\s*from\s*"@phosphor-icons\/react"/g)),
  )
  .flatMap((match) => match[1].split(",").map((name) => name.trim()))
  .filter((name) => name !== "");

describe("8.4 ícones do primeiro corte", () => {
  it("usa só os cinco ícones do corte", () => {
    expect(new Set(phosphorImports)).toEqual(
      new Set(["House", "Wallet", "Plus", "WarningCircle"]),
    );
  });

  it("não importa o ícone Envelope, que é de e-mail", () => {
    expect(phosphorImports).not.toContain("Envelope");
    for (const { path, code } of sources) {
      // Comentários falam do ícone proibido; a regra é sobre o código.
      const semComentarios = code
        .replace(/\/\*[\s\S]*?\*\//g, "")
        .replace(/\/\/[^\n]*/g, "")
        // Identificadores de domínio que contêm a palavra não são o ícone.
        .replace(/SummaryEnvelope|EnvelopesScreen|EnvelopePurpose|EnvelopeRole/g, "");
      expect(
        /\bEnvelope\b(?![A-Za-z])/.test(semComentarios),
        `${path} parece usar o ícone Envelope`,
      ).toBe(false);
    }
  });

  it("renderiza os ícones a 20px", () => {
    const usos = sources.flatMap(({ code }) => Array.from(code.matchAll(/size=\{(\d+)\}/g)));
    expect(usos.length).toBeGreaterThan(0);
    for (const uso of usos) {
      expect(uso[1]).toBe("20");
    }
  });
});
