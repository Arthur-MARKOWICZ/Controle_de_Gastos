import type { Metadata } from "next";
import { PAGE_TITLES } from "../page-titles";

export const metadata: Metadata = {
  title: PAGE_TITLES.redefinirSenha,
  referrer: "no-referrer",
};

export default function PasswordResetLayout({ children }: { children: React.ReactNode }) {
  return children;
}
