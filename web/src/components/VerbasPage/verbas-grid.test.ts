import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const css = readFileSync(join(process.cwd(), "src/components/VerbasPage/Verbas.module.css"), "utf8");

function rule(selector: string) {
  const start = css.indexOf(`${selector} {`);
  return start === -1 ? "" : css.slice(start, css.indexOf("}", start));
}

describe("grades de Verbas", () => {
  it("não força três colunas quando há menos grupos", () => {
    for (const selector of [".purposeGrid", ".distribution"]) {
      const declaration = rule(selector);
      expect(declaration).not.toBe("");
      expect(declaration).toMatch(/grid-template-columns:\s*repeat\(auto-fit,\s*minmax\(/);
      expect(declaration).not.toMatch(/repeat\(3/);
    }
  });
});
