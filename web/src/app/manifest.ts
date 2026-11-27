import type { MetadataRoute } from "next";

/**
 * Torna a web instalável (ADR-0033). No iOS 16.4+ só o app instalado recebe Web Push.
 * Os ícones são os já servidos pelo Next a partir de `app/`.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Controle de gastos",
    short_name: "Gastos",
    description: "Organize sua renda em verbas que acompanham a vida real.",
    lang: "pt-BR",
    start_url: "/",
    scope: "/",
    display: "standalone",
    background_color: "#F6F7F9",
    theme_color: "#1F3A73",
    icons: [
      { src: "/icon.svg", sizes: "any", type: "image/svg+xml" },
      { src: "/apple-icon.png", sizes: "256x256", type: "image/png" },
    ],
  };
}
