import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import type { PortfolioSummaryDTO } from "../../lib/api";
import { ThemeProvider } from "../../theme/ThemeProvider";
import { CarteiraPage } from "./CarteiraPage";

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
    this.open = true;
  });
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
    this.open = false;
  });
});

const mockRequest = vi.fn();
const mockClient = { request: mockRequest };

vi.mock("../../auth/auth-context", () => ({
  useAuth: () => ({ client: mockClient }),
}));

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

function renderHub() {
  return render(
    <ThemeProvider>
      <CarteiraPage email="titular@example.com" onLogout={vi.fn()} />
    </ThemeProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("CarteiraPage — hub", () => {
  it("exibe empty state com CTAs para cadastrar ação e CDB quando não há posições", async () => {
    const empty: PortfolioSummaryDTO = {
      complete: true,
      totalCost: { amount: "0.00", currency: "BRL" },
      totalCurrent: { amount: "0.00", currency: "BRL" },
      totalPnl: { amount: "0.00", currency: "BRL" },
      stocks: [],
      cdbs: [],
    };
    mockRequest.mockResolvedValueOnce(json(200, empty));

    renderHub();

    expect(await screen.findByText(/ainda não há posições/i)).toBeDefined();
    expect(screen.getAllByRole("button", { name: "Cadastrar ação" }).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByRole("button", { name: "Cadastrar CDB" }).length).toBeGreaterThanOrEqual(1);
    expect(mockRequest).toHaveBeenCalledWith("/api/v1/investments/summary");
  });

  it("exibe totais, ações agregadas por ticker e CDB rotulado como estimado", async () => {
    const summary: PortfolioSummaryDTO = {
      complete: true,
      totalCost: { amount: "11500.00", currency: "BRL" },
      totalCurrent: { amount: "12900.00", currency: "BRL" },
      totalPnl: { amount: "1400.00", currency: "BRL" },
      stocks: [
        {
          ticker: "PETR4",
          quantity: 100,
          averagePrice: { amount: "15.00", currency: "BRL" },
          acquisitionCost: { amount: "1500.00", currency: "BRL" },
          currentValue: { amount: "1650.00", currency: "BRL" },
          pnl: { amount: "150.00", currency: "BRL" },
          lots: [
            {
              id: "lot-1",
              quantity: 100,
              unitPrice: { amount: "15.00", currency: "BRL" },
              acquisitionCost: { amount: "1500.00", currency: "BRL" },
              purchaseDate: "2026-01-15",
              currentValue: { amount: "1650.00", currency: "BRL" },
              pnl: { amount: "150.00", currency: "BRL" },
            },
          ],
        },
      ],
      cdbs: [
        {
          id: "cdb-1",
          principal: { amount: "10000.00", currency: "BRL" },
          estimatedValue: { amount: "11250.00", currency: "BRL" },
          pnl: { amount: "1250.00", currency: "BRL" },
        },
      ],
    };
    mockRequest.mockResolvedValueOnce(json(200, summary));

    renderHub();

    expect(await screen.findByText(/11\.500,00/)).toBeDefined();
    expect(screen.getByText(/12\.900,00/)).toBeDefined();
    expect(screen.getByText(/1\.400,00/)).toBeDefined();

    const stocksSection = screen.getByRole("region", { name: "Ações" });
    expect(within(stocksSection).getByText("PETR4")).toBeDefined();
    expect(within(stocksSection).getByText(/100 ações/)).toBeDefined();

    const cdbsSection = screen.getByRole("region", { name: "CDBs" });
    expect(within(cdbsSection).getByText(/11\.250,00/)).toBeDefined();
    expect(within(cdbsSection).getAllByText(/estimado/i).length).toBeGreaterThanOrEqual(1);
    expect(within(cdbsSection).queryByText(/líquido de IR/i)).toBeNull();
  });

  it("indica resumo parcial quando complete é false e não inventa valor para ação sem cotação", async () => {
    const partial: PortfolioSummaryDTO = {
      complete: false,
      totalCost: { amount: "1000.00", currency: "BRL" },
      totalCurrent: { amount: "0.00", currency: "BRL" },
      totalPnl: { amount: "0.00", currency: "BRL" },
      stocks: [
        {
          ticker: "VALE3",
          quantity: 10,
          averagePrice: { amount: "100.00", currency: "BRL" },
          acquisitionCost: { amount: "1000.00", currency: "BRL" },
          currentValue: null,
          pnl: null,
          lots: [
            {
              id: "lot-2",
              quantity: 10,
              unitPrice: { amount: "100.00", currency: "BRL" },
              acquisitionCost: { amount: "1000.00", currency: "BRL" },
              purchaseDate: "2026-02-01",
              currentValue: null,
              pnl: null,
            },
          ],
        },
      ],
      cdbs: [],
    };
    mockRequest.mockResolvedValueOnce(json(200, partial));

    renderHub();

    expect(await screen.findByText(/Resumo parcial/i)).toBeDefined();
    const stocksSection = screen.getByRole("region", { name: "Ações" });
    expect(within(stocksSection).getByText("VALE3")).toBeDefined();
    expect(within(stocksSection).getAllByText(/sem cotação/i).length).toBeGreaterThanOrEqual(2);
    expect(within(stocksSection).queryByText(/^R\$\s*0,00$/)).toBeNull();
  });
});

describe("CarteiraPage — cadastro e exclusão", () => {
  const emptySummary: PortfolioSummaryDTO = {
    complete: true,
    totalCost: { amount: "0.00", currency: "BRL" },
    totalCurrent: { amount: "0.00", currency: "BRL" },
    totalPnl: { amount: "0.00", currency: "BRL" },
    stocks: [],
    cdbs: [],
  };

  const summaryWithStock: PortfolioSummaryDTO = {
    complete: true,
    totalCost: { amount: "1000.00", currency: "BRL" },
    totalCurrent: { amount: "1100.00", currency: "BRL" },
    totalPnl: { amount: "100.00", currency: "BRL" },
    stocks: [
      {
        ticker: "PETR4",
        quantity: 100,
        averagePrice: { amount: "10.00", currency: "BRL" },
        acquisitionCost: { amount: "1000.00", currency: "BRL" },
        currentValue: { amount: "1100.00", currency: "BRL" },
        pnl: { amount: "100.00", currency: "BRL" },
        lots: [
          {
            id: "lot-1",
            quantity: 100,
            unitPrice: { amount: "10.00", currency: "BRL" },
            acquisitionCost: { amount: "1000.00", currency: "BRL" },
            purchaseDate: "2026-01-15",
            currentValue: { amount: "1100.00", currency: "BRL" },
            pnl: { amount: "100.00", currency: "BRL" },
          },
        ],
      },
    ],
    cdbs: [],
  };

  const summaryWithCdb: PortfolioSummaryDTO = {
    complete: true,
    totalCost: { amount: "10000.00", currency: "BRL" },
    totalCurrent: { amount: "11250.00", currency: "BRL" },
    totalPnl: { amount: "1250.00", currency: "BRL" },
    stocks: [],
    cdbs: [
      {
        id: "cdb-1",
        principal: { amount: "10000.00", currency: "BRL" },
        estimatedValue: { amount: "11250.00", currency: "BRL" },
        pnl: { amount: "1250.00", currency: "BRL" },
      },
    ],
  };

  it("cadastra ação via Dialog, atualiza o resumo e não aciona endpoints de verbas", async () => {
    mockRequest
      .mockResolvedValueOnce(json(200, emptySummary))
      .mockResolvedValueOnce(
        json(201, {
          id: "lot-1",
          ticker: "PETR4",
          quantity: 100,
          unitPrice: { amount: "10.00", currency: "BRL" },
          acquisitionCost: { amount: "1000.00", currency: "BRL" },
          purchaseDate: "2026-01-15",
          createdAt: "2026-01-15T12:00:00Z",
          currentQuote: null,
          currentValue: null,
        }),
      )
      .mockResolvedValueOnce(json(200, summaryWithStock));

    renderHub();
    await screen.findByText(/ainda não há posições/i);

    fireEvent.click(screen.getAllByRole("button", { name: "Cadastrar ação" })[0]);

    const dialog = screen.getByRole("dialog");
    fireEvent.change(within(dialog).getByLabelText("Ticker B3"), { target: { value: "PETR4" } });
    fireEvent.change(within(dialog).getByLabelText("Quantidade"), { target: { value: "100" } });
    fireEvent.change(within(dialog).getByLabelText("Preço unitário (BRL)"), { target: { value: "10.00" } });
    fireEvent.change(within(dialog).getByLabelText("Data de compra"), { target: { value: "2026-01-15" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Salvar ação" }));

    expect(await screen.findByText("PETR4")).toBeDefined();
    expect(mockRequest).toHaveBeenCalledWith("/api/v1/investments/stocks", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        ticker: "PETR4",
        quantity: 100,
        unitPrice: { amount: "10.00", currency: "BRL" },
        purchaseDate: "2026-01-15",
      }),
    });
    const calledUrls = mockRequest.mock.calls.map(([url]) => String(url));
    expect(calledUrls.some((url) => url.includes("/envelopes") || url.includes("/ledger"))).toBe(false);
  });

  it("mostra erro 400 sem fechar o formulário de ação e mantém resumo anterior", async () => {
    mockRequest
      .mockResolvedValueOnce(json(200, summaryWithStock))
      .mockResolvedValueOnce(
        json(400, { code: "INVALID_REQUEST", detail: "Ticker B3 inválido", title: "Requisição inválida" }),
      );

    renderHub();
    await screen.findByText("PETR4");

    fireEvent.click(screen.getAllByRole("button", { name: "Cadastrar ação" })[0]);
    const dialog = screen.getByRole("dialog");
    fireEvent.change(within(dialog).getByLabelText("Ticker B3"), { target: { value: "INVALIDO" } });
    fireEvent.change(within(dialog).getByLabelText("Quantidade"), { target: { value: "10" } });
    fireEvent.change(within(dialog).getByLabelText("Preço unitário (BRL)"), { target: { value: "10.00" } });
    fireEvent.change(within(dialog).getByLabelText("Data de compra"), { target: { value: "2026-01-15" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Salvar ação" }));

    expect(await within(dialog).findByText("Ticker B3 inválido")).toBeDefined();
    expect(screen.getByText("PETR4")).toBeDefined();
  });

  it("cadastra CDB via Dialog, atualiza o resumo e mantém rótulo estimado", async () => {
    mockRequest
      .mockResolvedValueOnce(json(200, emptySummary))
      .mockResolvedValueOnce(
        json(201, {
          id: "cdb-1",
          principal: { amount: "10000.00", currency: "BRL" },
          annualRate: "0.1250",
          startDate: "2026-01-01",
          maturityDate: "2027-01-01",
          estimatedValue: { amount: "11250.00", currency: "BRL" },
          createdAt: "2026-01-01T12:00:00Z",
        }),
      )
      .mockResolvedValueOnce(json(200, summaryWithCdb));

    renderHub();
    await screen.findByText(/ainda não há posições/i);

    fireEvent.click(screen.getAllByRole("button", { name: "Cadastrar CDB" })[0]);
    const dialog = screen.getByRole("dialog");
    fireEvent.change(within(dialog).getByLabelText("Valor aplicado (BRL)"), { target: { value: "1000000" } });
    fireEvent.change(within(dialog).getByLabelText(/Taxa anual \(% a\.a\.\)/i), { target: { value: "12,50" } });
    fireEvent.change(within(dialog).getByLabelText("Data de início"), { target: { value: "2026-01-01" } });
    fireEvent.change(within(dialog).getByLabelText("Data de vencimento"), { target: { value: "2027-01-01" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Salvar CDB" }));

    const cdbsSection = await screen.findByRole("region", { name: "CDBs" });
    expect(within(cdbsSection).getAllByText(/estimado/i).length).toBeGreaterThanOrEqual(1);
    expect(mockRequest).toHaveBeenCalledWith("/api/v1/investments/cdbs", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        principal: { amount: "10000.00", currency: "BRL" },
        annualRate: "0.1250",
        startDate: "2026-01-01",
        maturityDate: "2027-01-01",
      }),
    });
  });

  it("exclui lote de ação após confirmação de risco e atualiza o resumo", async () => {
    mockRequest
      .mockResolvedValueOnce(json(200, summaryWithStock))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(json(200, emptySummary));

    renderHub();
    await screen.findByText("PETR4");

    fireEvent.click(screen.getByRole("button", { name: /Excluir lote/i }));
    const confirmDialog = screen.getByRole("dialog");
    const confirmButton = within(confirmDialog).getByRole("button", { name: "Excluir" });
    expect(confirmButton.className).toMatch(/danger/);

    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(mockRequest).toHaveBeenCalledWith("/api/v1/investments/stocks/lot-1", { method: "DELETE" });
    });
    expect(await screen.findByText(/ainda não há posições/i)).toBeDefined();
  });

  it("cancela exclusão de CDB sem enviar DELETE e mantém a posição", async () => {
    mockRequest.mockResolvedValueOnce(json(200, summaryWithCdb));

    renderHub();
    await screen.findByRole("region", { name: "CDBs" });

    fireEvent.click(screen.getByRole("button", { name: /Excluir CDB/i }));
    const confirmDialog = screen.getByRole("dialog");
    fireEvent.click(within(confirmDialog).getByRole("button", { name: "Cancelar" }));

    expect(mockRequest).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("region", { name: "CDBs" })).toBeDefined();
  });
});
