import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const layout = readFileSync(join(process.cwd(), "src/app/layout.tsx"), "utf8");
const globals = readFileSync(join(process.cwd(), "src/app/globals.css"), "utf8");
const interLatin = join(process.cwd(), "src/app/fonts/Inter-latin-wght-normal.woff2");

describe("tipografia da web", () => {
  it("carrega Inter por next/font/local, sem baixar Google Fonts no build", () => {
    expect(layout).not.toMatch(/next\/font\/google/);
    expect(layout).toMatch(/import localFont from "next\/font\/local"/);
    expect(layout).toMatch(/src:\s*["']\.\/fonts\/Inter-latin-wght-normal\.woff2["']/);
    expect(layout).toMatch(/variable: "--font-inter"/);
    expect(layout).toMatch(/className=\{inter\.variable\}/);
  });

  it("versiona o woff2 latino da Inter para o Docker não depender de fonts.gstatic.com", () => {
    expect(existsSync(interLatin)).toBe(true);
    const bytes = readFileSync(interLatin);
    expect(bytes.subarray(0, 4).toString("ascii")).toBe("wOF2");
    expect(bytes.byteLength).toBeGreaterThan(10_000);
  });

  it("liga --font-sans à variável da fonte carregada", () => {
    const token = globals.match(/--font-sans:([^;]+);/)?.[1].trim();
    expect(token).toBeDefined();
    expect(token!.startsWith("var(--font-inter)")).toBe(true);
    expect(token).toContain("Inter");
  });
});
