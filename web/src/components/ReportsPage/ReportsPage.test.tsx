import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ReportsPage } from "./ReportsPage";

const downloadReport = vi.fn().mockResolvedValue({ blob: new Blob(["relatorio"]), filename: "gastos.csv" });

vi.mock("../../auth/auth-context", () => ({ useAuth: () => ({ client: {} }) }));
vi.mock("../../lib/api", () => ({ createApiClient: () => ({ downloadReport }) }));
vi.mock("../AppShell/AppShell", () => ({ AppShell: ({ children }: { children: React.ReactNode }) => <main>{children}</main> }));

afterEach(() => {
  cleanup();
  downloadReport.mockClear();
  vi.restoreAllMocks();
});

describe("ReportsPage", () => {
  it("downloads the selected report with its required dates and format", async () => {
    vi.stubGlobal("URL", { createObjectURL: vi.fn(() => "blob:test"), revokeObjectURL: vi.fn() });
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
    render(<ReportsPage email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Tipo de relatório"), { target: { value: "expenses-by-purpose" } });
    fireEvent.change(screen.getByLabelText("Data inicial"), { target: { value: "2026-01-10" } });
    fireEvent.change(screen.getByLabelText("Data final"), { target: { value: "2026-01-20" } });
    fireEvent.change(screen.getByLabelText("Formato"), { target: { value: "csv" } });
    fireEvent.click(screen.getByRole("button", { name: "Gerar relatório" }));

    await waitFor(() => expect(downloadReport).toHaveBeenCalledWith("expenses-by-purpose", "2026-01-10", "2026-01-20", "csv"));
  });

  it("blocks monthly reports with a partial month", () => {
    render(<ReportsPage email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Tipo de relatório"), { target: { value: "limit-exceeded-months" } });
    fireEvent.change(screen.getByLabelText("Data inicial"), { target: { value: "2026-01-02" } });

    expect(screen.getByText("Relatórios mensais exigem início no primeiro dia e término no último dia do mês.")).toBeDefined();
    expect(screen.getByRole("button", { name: "Gerar relatório" }).hasAttribute("disabled")).toBe(true);
  });
});
