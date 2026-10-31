import type { Metadata } from "next";
import "./globals.css";
import { ThemeProvider } from "../theme/ThemeProvider";
import { themeBootstrapScript } from "../theme/theme";
import { PAGE_TITLES } from "./page-titles";

export const metadata: Metadata = {
  title: {
    default: `${PAGE_TITLES.home} · Controle de gastos`,
    template: "%s · Controle de gastos",
  },
  description: "Organize sua renda em verbas que acompanham a vida real.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR" suppressHydrationWarning>
      <head><script dangerouslySetInnerHTML={{ __html: themeBootstrapScript }} /></head>
      <body><ThemeProvider>{children}</ThemeProvider></body>
    </html>
  );
}
