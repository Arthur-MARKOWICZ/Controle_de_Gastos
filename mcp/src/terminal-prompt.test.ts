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

  it("preserva espaços internos e finais da senha", () => {
    const spawn = vi.fn().mockReturnValue({ status: 0, stdout: " frase com espaço \n", stderr: "" });

    expect(readSecretWithBash("Senha: ", spawn)).toBe(" frase com espaço ");
  });

  it("aceita quebra de linha do Windows no fim", () => {
    const spawn = vi.fn().mockReturnValue({ status: 0, stdout: "s3nha\r\n", stderr: "" });

    expect(readSecretWithBash("Senha: ", spawn)).toBe("s3nha");
  });

  it("passa o prompt como argumento e não dentro do script", () => {
    const spawn = vi.fn().mockReturnValue({ status: 0, stdout: "x", stderr: "" });

    readSecretWithBash('Senha"; rm -rf ~; echo "', spawn);

    const args = spawn.mock.calls[0]?.[1] as string[];
    expect(args[1]).not.toContain("rm -rf");
    expect(args.at(-1)).toBe('Senha"; rm -rf ~; echo "');
  });

  it("bash interrompido (status nulo) falha com a mensagem de senha", () => {
    const spawn = vi.fn().mockReturnValue({ status: null, stdout: "", stderr: "" });

    expect(() => readSecretWithBash("Senha: ", spawn)).toThrow("Não foi possível ler a senha no terminal.");
  });

  it("senha vazia é devolvida vazia para o fluxo recusar", () => {
    const spawn = vi.fn().mockReturnValue({ status: 0, stdout: "\n", stderr: "" });

    expect(readSecretWithBash("Senha: ", spawn)).toBe("");
  });
});

