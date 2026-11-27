import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const layout = readFileSync(join(process.cwd(), "src/app/layout.tsx"), "utf8");
const globals = readFileSync(join(process.cwd(), "src/app/globals.css"), "utf8");
const fontFiles = [
  "src/app/fonts/dm-sans-latin.woff2",
  "src/app/fonts/newsreader-latin.woff2",
  "src/app/fonts/ibm-plex-mono-400-latin.woff2",
];

describe("tipografia da web", () => {
  it("carrega Newsreader, DM Sans e IBM Plex Mono por next/font/local, sem baixar Google Fonts no build", () => {
    expect(layout).not.toMatch(/next\/font\/google/);
    expect(layout).toMatch(/import localFont from "next\/font\/local"/);
    expect(layout).toMatch(/variable: "--font-dm-sans"/);
    expect(layout).toMatch(/variable: "--font-newsreader"/);
    expect(layout).toMatch(/variable: "--font-ibm-plex-mono"/);
    expect(layout).toMatch(/dmSans\.variable/);
    expect(layout).toMatch(/newsreader\.variable/);
    expect(layout).toMatch(/ibmPlexMono\.variable/);
  });

  it("versiona os arquivos woff2 locais para o Docker não depender de fonts.gstatic.com", () => {
    for (const relPath of fontFiles) {
      const fullPath = join(process.cwd(), relPath);
      expect(existsSync(fullPath)).toBe(true);
      const bytes = readFileSync(fullPath);
      expect(bytes.subarray(0, 4).toString("ascii")).toBe("wOF2");
      expect(bytes.byteLength).toBeGreaterThan(5_000);
    }
  });

  it("liga --font-sans, --font-display e --font-mono às variáveis das fontes carregadas", () => {
    const sans = globals.match(/--font-sans:([^;]+);/)?.[1].trim();
    const display = globals.match(/--font-display:([^;]+);/)?.[1].trim();
    const mono = globals.match(/--font-mono:([^;]+);/)?.[1].trim();

    expect(sans?.startsWith("var(--font-dm-sans)")).toBe(true);
    expect(display?.startsWith("var(--font-newsreader)")).toBe(true);
    expect(mono?.startsWith("var(--font-ibm-plex-mono)")).toBe(true);
  });
});
