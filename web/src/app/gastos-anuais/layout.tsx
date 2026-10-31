import type { Metadata } from "next";
import { PAGE_TITLES } from "../page-titles";

export const metadata: Metadata = {
  title: PAGE_TITLES.gastosAnuais,
};

export default function GastosAnuaisLayout({ children }: { children: React.ReactNode }) {
  return children;
}
