import type { Metadata } from "next";
import { PAGE_TITLES } from "../page-titles";

export const metadata: Metadata = {
  title: PAGE_TITLES.configuracoes,
};

export default function ConfiguracoesLayout({ children }: { children: React.ReactNode }) {
  return children;
}
