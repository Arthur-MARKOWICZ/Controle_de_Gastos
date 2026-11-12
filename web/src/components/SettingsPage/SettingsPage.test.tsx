import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SettingsPage } from "./SettingsPage";

vi.mock("../AppShell/AppShell", () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

afterEach(cleanup);

describe("SettingsPage", () => {
  it("oferece link para a política de privacidade", () => {
    render(<SettingsPage email="pessoa@example.com" onLogout={vi.fn()} />);

    const link = screen.getByRole("link", { name: /Política de privacidade/i });
    expect(link).toHaveProperty("href", "http://localhost:3000/privacidade");
  });

  it("usa o cabeçalho compartilhado com um único h1", () => {
    render(<SettingsPage email="pessoa@example.com" onLogout={vi.fn()} />);

    const h1 = screen.getAllByRole("heading", { level: 1 });
    expect(h1).toHaveLength(1);
    expect(h1[0].textContent).toBe("Configurações");
  });
});
