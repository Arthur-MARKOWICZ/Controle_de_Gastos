import type { Metadata } from "next";
import localFont from "next/font/local";
import "./globals.css";
import { ThemeProvider } from "../theme/ThemeProvider";
import { ToastProvider } from "../components/ui/ToastProvider";
import { themeBootstrapScript } from "../theme/theme";
import { PAGE_TITLES } from "./page-titles";

/** Fontes versionadas em `fonts/`: o build Docker não consulta Google Fonts. */
const dmSans = localFont({
  src: [
    { path: "./fonts/dm-sans-latin.woff2", weight: "400 700", style: "normal" },
    { path: "./fonts/dm-sans-latin-ext.woff2", weight: "400 700", style: "normal" },
  ],
  display: "swap",
  variable: "--font-dm-sans",
});

const newsreader = localFont({
  src: [
    { path: "./fonts/newsreader-latin.woff2", weight: "500 700", style: "normal" },
    { path: "./fonts/newsreader-latin-ext.woff2", weight: "500 700", style: "normal" },
  ],
  display: "swap",
  variable: "--font-newsreader",
});

const ibmPlexMono = localFont({
  src: [
    { path: "./fonts/ibm-plex-mono-400-latin.woff2", weight: "400", style: "normal" },
    { path: "./fonts/ibm-plex-mono-500-latin.woff2", weight: "500", style: "normal" },
    { path: "./fonts/ibm-plex-mono-600-latin.woff2", weight: "600", style: "normal" },
  ],
  display: "swap",
  variable: "--font-ibm-plex-mono",
});

export const metadata: Metadata = {
  title: {
    default: `${PAGE_TITLES.home} · Verbas`,
    template: "%s · Verbas",
  },
  description: "Organize sua renda em verbas que acompanham a vida real.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="pt-BR"
      className={`${dmSans.variable} ${newsreader.variable} ${ibmPlexMono.variable}`}
      suppressHydrationWarning
    >
      <head><script dangerouslySetInnerHTML={{ __html: themeBootstrapScript }} /></head>
      <body><ThemeProvider><ToastProvider>{children}</ToastProvider></ThemeProvider></body>
    </html>
  );
}
