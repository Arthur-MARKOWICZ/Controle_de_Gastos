import type { Metadata } from "next";
import { PAGE_TITLES } from "../../page-titles";

export const metadata: Metadata = {
  title: PAGE_TITLES.oauthCallback,
};

export default function OAuthCallbackLayout({ children }: { children: React.ReactNode }) {
  return children;
}
