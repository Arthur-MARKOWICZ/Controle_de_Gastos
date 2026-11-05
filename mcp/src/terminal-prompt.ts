import { spawnSync, type SpawnSyncReturns } from "node:child_process";
import { createInterface } from "node:readline/promises";
import { stdin as input, stdout as output } from "node:process";

type SpawnFn = (
  command: string,
  args: readonly string[],
  options: {
    encoding: "utf8";
    stdio: [number, "pipe", number];
  },
) => Pick<SpawnSyncReturns<string>, "status" | "stdout" | "stderr">;

/** Lê senha oculta via bash `read -s` (compatível com terminal integrado). */
export function readSecretWithBash(
  prompt: string,
  spawn: SpawnFn = spawnSync,
): string {
  const result = spawn(
    "bash",
    ["-c", 'IFS= read -r -s -p "$1" pw; printf \'\\n\' >&2; printf \'%s\' "$pw"', "_", prompt],
    {
      encoding: "utf8",
      stdio: [0, "pipe", 2],
    },
  );
  if (result.status !== 0) {
    throw new Error("Não foi possível ler a senha no terminal.");
  }
  return (result.stdout ?? "").replace(/\r?\n$/, "");
}

export async function askVisible(prompt: string): Promise<string> {
  const rl = createInterface({ input, output, terminal: Boolean(input.isTTY) });
  try {
    return await rl.question(prompt);
  } finally {
    rl.close();
  }
}
