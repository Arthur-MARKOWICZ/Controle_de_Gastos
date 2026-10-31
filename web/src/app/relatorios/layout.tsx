import type { Metadata } from "next";
import { PAGE_TITLES } from "../page-titles";

export const metadata: Metadata = {
  title: PAGE_TITLES.relatorios,
};

export default function RelatoriosLayout({ children }: { children: React.ReactNode }) {
  return children;
}
