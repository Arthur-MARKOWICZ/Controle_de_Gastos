import type { Metadata } from "next";
import { PAGE_TITLES } from "../page-titles";

export const metadata: Metadata = {
  title: PAGE_TITLES.carteira,
};

export default function CarteiraLayout({ children }: { children: React.ReactNode }) {
  return children;
}
