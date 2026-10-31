import type { Metadata } from "next";
import { PAGE_TITLES } from "../page-titles";

export const metadata: Metadata = {
  title: PAGE_TITLES.gastos,
};

export default function GastosLayout({ children }: { children: React.ReactNode }) {
  return children;
}
