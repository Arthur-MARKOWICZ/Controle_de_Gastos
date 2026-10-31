import type { Metadata } from "next";
import { PAGE_TITLES } from "../page-titles";

export const metadata: Metadata = {
  title: PAGE_TITLES.verbas,
};

export default function VerbasLayout({ children }: { children: React.ReactNode }) {
  return children;
}
