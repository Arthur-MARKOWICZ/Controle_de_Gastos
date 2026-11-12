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
  },
});
