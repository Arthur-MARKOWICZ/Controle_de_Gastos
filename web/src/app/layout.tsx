import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Controle de gastos",
  description: "Organize sua renda em verbas que acompanham a vida real.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
