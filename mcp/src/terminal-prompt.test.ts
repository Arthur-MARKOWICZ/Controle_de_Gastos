import { describe, expect, it, vi } from "vitest";
import { readSecretWithBash } from "./terminal-prompt.js";

describe("readSecretWithBash", () => {
  it("devolve a senha do stdout sem quebra de linha final", () => {
    const spawn = vi.fn().mockReturnValue({
      status: 0,
      stdout: "s3nha-secreta\n",
      stderr: "",
    });

    const value = readSecretWithBash("Senha: ", spawn);

    expect(value).toBe("s3nha-secreta");
    expect(spawn).toHaveBeenCalledOnce();
    const args = spawn.mock.calls[0]?.[1] as string[];
    expect(args.some((part) => part.includes("read -r -s"))).toBe(true);
  });

  it("falha quando o bash não lê a senha", () => {
    const spawn = vi.fn().mockReturnValue({ status: 1, stdout: "", stderr: "" });

    expect(() => readSecretWithBash("Senha: ", spawn)).toThrow(/senha/i);
  });
});
