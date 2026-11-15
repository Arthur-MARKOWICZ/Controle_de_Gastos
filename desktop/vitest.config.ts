import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// Os testes não passam pelo vite.config.ts: a URL da API é injetada aqui com um
// valor fixo de teste, para a suíte não depender de um desktop/.env local.
export default defineConfig({
  plugins: [react()],
  define: { __API_BASE_URL__: JSON.stringify("http://localhost:8080") },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test-setup.ts"],
    include: ["src/**/*.test.ts", "src/**/*.test.tsx", "*.test.ts"],
    // Cobertura (ADR-0030): main.tsx só monta a árvore React.
    coverage: {
      provider: "v8",
      reporter: ["text-summary", "html", "lcov"],
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/**/*.test.{ts,tsx}", "src/main.tsx", "src/test-setup.ts", "src/env.d.ts"],
      thresholds: { lines: 0, branches: 0 },
    },
  },
});
