import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import { resolveApiBaseUrl } from "./api-env";

export default defineConfig(({ mode }) => {
  // Prefixo vazio: as variáveis do canal não usam `VITE_` (ADR-0029).
  const fileEnv = loadEnv(mode, process.cwd(), "");
  const apiBaseUrl = resolveApiBaseUrl({
    API_ENV: process.env.API_ENV ?? fileEnv.API_ENV,
    API_BASE_URL_LOCAL: process.env.API_BASE_URL_LOCAL ?? fileEnv.API_BASE_URL_LOCAL,
    API_BASE_URL_PROD: process.env.API_BASE_URL_PROD ?? fileEnv.API_BASE_URL_PROD,
  });

  return {
    plugins: [react()],
    // O Tauri serve o bundle local; nada é publicado em subcaminho.
    base: "./",
    clearScreen: false,
    server: { port: 1420, strictPort: true },
    build: { outDir: "dist", emptyOutDir: true, target: "es2022" },
    define: { __API_BASE_URL__: JSON.stringify(apiBaseUrl) },
  };
});
