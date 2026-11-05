import type { Metadata } from "next";
import { PAGE_TITLES } from "../page-titles";

export const metadata: Metadata = {
  title: PAGE_TITLES.privacidade,
  description: "Política de privacidade e canal eletrônico do titular.",
};

export default function PrivacidadeLayout({ children }: { children: React.ReactNode }) {
  return children;
}
