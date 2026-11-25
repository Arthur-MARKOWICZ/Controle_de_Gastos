import { describe, expect, it } from "vitest";
import manifest from "./manifest";

describe("manifest", () => {
  it("permite instalar o app em modo standalone, inclusive no iOS", () => {
    const value = manifest();

    expect(value.display).toBe("standalone");
    expect(value.start_url).toBe("/");
    expect(value.lang).toBe("pt-BR");
    expect(value.icons?.map((icon) => icon.src)).toEqual(["/icon.svg", "/apple-icon.png"]);
  });
});
