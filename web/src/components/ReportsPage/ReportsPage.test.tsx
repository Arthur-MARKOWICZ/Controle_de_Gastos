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
  it("baixa o relatório escolhido com as datas e o formato informados", async () => {
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

  it("usa o cabeçalho compartilhado e o botão compartilhado", () => {
    render(<ReportsPage email="pessoa@example.com" onLogout={vi.fn()} />);

    const h1 = screen.getAllByRole("heading", { level: 1 });
    expect(h1).toHaveLength(1);
    expect(h1[0].textContent).toBe("Relatórios");
    expect(screen.getByRole("button", { name: "Gerar relatório" })).toBeDefined();
  });

  it("bloqueia relatório mensal com mês parcial e mostra a mensagem", () => {
    render(<ReportsPage email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Tipo de relatório"), { target: { value: "limit-exceeded-months" } });
    fireEvent.change(screen.getByLabelText("Data inicial"), { target: { value: "2026-01-02" } });

    expect(screen.getByText("Relatórios mensais exigem início no primeiro dia e término no último dia do mês.")).toBeDefined();
    expect(screen.getByRole("button", { name: "Gerar relatório" }).hasAttribute("disabled")).toBe(true);
  });

  it("relatório mensal aceita fevereiro bissexto inteiro", async () => {
    vi.stubGlobal("URL", { createObjectURL: vi.fn(() => "blob:test"), revokeObjectURL: vi.fn() });
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
    render(<ReportsPage email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Tipo de relatório"), { target: { value: "goals-below-target" } });
    fireEvent.change(screen.getByLabelText("Data inicial"), { target: { value: "2028-02-01" } });
    fireEvent.change(screen.getByLabelText("Data final"), { target: { value: "2028-02-29" } });
    fireEvent.click(screen.getByRole("button", { name: "Gerar relatório" }));

    await waitFor(() => expect(downloadReport).toHaveBeenCalledWith("goals-below-target", "2028-02-01", "2028-02-29", "xlsx"));
    expect(await screen.findByText("Relatório gerado. O download deve começar em instantes.")).toBeDefined();
  });

  it("período invertido mostra a mensagem e não chama a API mesmo enviando o formulário", () => {
    render(<ReportsPage email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Data inicial"), { target: { value: "2026-03-10" } });
    fireEvent.change(screen.getByLabelText("Data final"), { target: { value: "2026-03-01" } });
    fireEvent.submit(screen.getByRole("button", { name: "Gerar relatório" }).closest("form")!);

    expect(screen.getByText("A data inicial deve ser anterior ou igual à data final.")).toBeDefined();
    expect(downloadReport).not.toHaveBeenCalled();
  });

  it("relatório mensal terminando antes do último dia não chama a API", () => {
    render(<ReportsPage email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Tipo de relatório"), { target: { value: "limit-exceeded-months" } });
    fireEvent.change(screen.getByLabelText("Data inicial"), { target: { value: "2026-01-01" } });
    fireEvent.change(screen.getByLabelText("Data final"), { target: { value: "2026-01-30" } });
    fireEvent.submit(screen.getByRole("button", { name: "Gerar relatório" }).closest("form")!);

    expect(downloadReport).not.toHaveBeenCalled();
  });

  it("data final apagada mostra a mensagem de período inválido e desabilita o envio", () => {
    render(<ReportsPage email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Data final"), { target: { value: "" } });

    expect(screen.getByText("A data inicial deve ser anterior ou igual à data final.")).toBeDefined();
    expect(screen.getByRole("button", { name: "Gerar relatório" }).hasAttribute("disabled")).toBe(true);
  });

  it("falha da API mostra a mensagem de erro e não inicia download", async () => {
    const createObjectURL = vi.fn(() => "blob:test");
    vi.stubGlobal("URL", { createObjectURL, revokeObjectURL: vi.fn() });
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
    downloadReport.mockRejectedValueOnce(new Error("500"));
    render(<ReportsPage email="pessoa@example.com" onLogout={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Data inicial"), { target: { value: "2026-01-10" } });
    fireEvent.change(screen.getByLabelText("Data final"), { target: { value: "2026-01-20" } });
    fireEvent.click(screen.getByRole("button", { name: "Gerar relatório" }));

    expect(await screen.findByText("Não foi possível gerar o relatório. Revise o período e tente novamente.")).toBeDefined();
    expect(createObjectURL).not.toHaveBeenCalled();
    expect(click).not.toHaveBeenCalled();
  });
});
