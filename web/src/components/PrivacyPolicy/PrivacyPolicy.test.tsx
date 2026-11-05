import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { TITULAR_CONTACT_EMAIL } from "../../privacy/titular-contact";
import { PrivacyPolicy } from "./PrivacyPolicy";

afterEach(cleanup);

describe("PrivacyPolicy", () => {
  it("explica finalidades, direitos e marca a versão como operacional", () => {
    render(<PrivacyPolicy />);

    expect(screen.getByRole("heading", { level: 1, name: /Política de privacidade/i })).toBeDefined();
    expect(screen.getByText(/versão operacional/i)).toBeDefined();
    expect(screen.getByText(/cadastro, autenticação e recuperação de acesso/i)).toBeDefined();
    expect(screen.getByText(/renda, verbas e movimentações/i)).toBeDefined();
    expect(screen.getByRole("heading", { name: /Seus direitos/i })).toBeDefined();
    expect(screen.getByText(/exportar|excluir|corrigir/i)).toBeDefined();
  });

  it("publica o canal eletrônico do titular sem exigir login", () => {
    render(<PrivacyPolicy />);

    const contact = screen.getByRole("link", { name: TITULAR_CONTACT_EMAIL });
    expect(contact).toHaveProperty("href", `mailto:${TITULAR_CONTACT_EMAIL}`);
  });
});
