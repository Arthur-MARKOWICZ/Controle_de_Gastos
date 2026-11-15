import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { OverviewScreen } from "./OverviewScreen";
import {
  ALERTS_PATH,
  NO_PENDING,
  PENDING_PATH,
  alert,
  envelope,
  pendingOccurrence,
  summary,
  summaryPath,
} from "../lib/fixtures";
import { installFakeIpc, resetIpc } from "../lib/test-ipc";
import type { FakeIpc } from "../lib/test-ipc";
import type { LedgerSummary } from "../lib/types";

afterEach(() => {
  cleanup();
  resetIpc();
});

const semEfeito = () => {};

function montar(ipc: FakeIpc, month = "2026-11", onMonthChange = vi.fn()) {
  void ipc;
  render(
    <OverviewScreen
      month={month}
      onMonthChange={onMonthChange}
      revision={0}
      onRegistered={semEfeito}
      onAuthRequired={semEfeito}
    />,
  );
}

function ipcCom(resumo: LedgerSummary, alertas = { items: [] as ReturnType<typeof alert>[] }) {
  return installFakeIpc()
    .onApi(summaryPath("2026-11"), { status: 200, body: resumo })
    .onApi(PENDING_PATH, { status: 200, body: NO_PENDING })
    .onApi(ALERTS_PATH, { status: 200, body: alertas });
}

describe("7.1 resumo do mês", () => {
  it("mostra renda e não alocado como vieram da API", async () => {
    montar(ipcCom(summary()));

    const renda = await screen.findByText("Renda do mês");
    const cardRenda = renda.parentElement as HTMLElement;
    // `data-amount` guarda a string decimal exata, sem passar pela formatação.
    expect(cardRenda.querySelector("[data-amount]")).toHaveAttribute(
      "data-amount",
      "8000.00",
    );

    const naoAlocado = screen.getByText("Não alocado").parentElement as HTMLElement;
    expect(naoAlocado.querySelector("[data-amount]")).toHaveAttribute(
      "data-amount",
      "1550.00",
    );
    expect(cardRenda.textContent).toContain("8.000,00");
  });

  it("mês sem renda configurada não inventa 0.00", async () => {
    montar(ipcCom(summary({ income: null, allocated: { amount: "0.00", currency: "BRL" } })));

    expect(
      await screen.findByText(/Renda não configurada neste mês/),
    ).toBeTruthy();
    const cardRenda = screen.getByText("Renda do mês").parentElement as HTMLElement;
    expect(cardRenda.querySelector("[data-amount]")).toBeNull();
    expect(cardRenda.textContent).not.toContain("0,00");
  });

  it("trocar o mês pede o resumo do mês escolhido", async () => {
    const user = userEvent.setup();
    const onMonthChange = vi.fn();
    const ipc = ipcCom(summary());
    montar(ipc, "2026-11", onMonthChange);
    await screen.findByText("Renda do mês");

    await user.click(screen.getByRole("button", { name: "Mês anterior" }));

    expect(onMonthChange).toHaveBeenCalledWith("2026-10");
  });

  it("o resumo do mês seguinte é buscado para o mês pedido", async () => {
    const ipc = installFakeIpc()
      .onApi(summaryPath("2026-10"), {
        status: 200,
        body: summary({ unallocated: { amount: "90.00", currency: "BRL" } }),
      })
      .onApi(PENDING_PATH, { status: 200, body: NO_PENDING })
      .onApi(ALERTS_PATH, { status: 200, body: { items: [] } });
    montar(ipc, "2026-10");

    await waitFor(() =>
      expect(ipc.pathsCalled()).toContain(summaryPath("2026-10")),
    );
    const naoAlocado = screen.getByText("Não alocado").parentElement as HTMLElement;
    expect(naoAlocado.querySelector("[data-amount]")).toHaveAttribute(
      "data-amount",
      "90.00",
    );
  });
});

describe("7.2 alertas de saldo negativo", () => {
  it('o alerta de "Combustível" aparece em texto, não só por cor', async () => {
    montar(ipcCom(summary(), { items: [alert()] }));

    const item = await screen.findByText(/A verba Combustível está com saldo negativo/);
    expect(item.textContent).toContain("Combustível");
  });

  it("não mostra alerta de verba de outro titular", async () => {
    // A API escopa os alertas; o cliente não deduz alerta da lista de verbas.
    montar(
      ipcCom(
        summary({
          envelopes: [
            envelope({
              id: "env-lazer",
              name: "Lazer",
              available: { amount: "-15.00", currency: "BRL" },
              isNegative: true,
            }),
          ],
        }),
        { items: [] },
      ),
    );

    expect(await screen.findByText("Nenhum alerta ativo.")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Dispensar" })).toBeNull();
  });
});

describe("7.3 dispensar alerta", () => {
  it("o alerta dispensado sai da visão geral", async () => {
    const user = userEvent.setup();
    const ipc = installFakeIpc()
      .onApi(summaryPath("2026-11"), { status: 200, body: summary() })
      .onApi(PENDING_PATH, { status: 200, body: NO_PENDING })
      .onApi(ALERTS_PATH, { status: 200, body: { items: [alert()] } })
      .onApi(PENDING_PATH, { status: 200, body: NO_PENDING })
      .onApi(ALERTS_PATH, { status: 200, body: { items: [] } })
      .onApi("/api/v1/alerts/alerta-1/dismiss", { status: 204, body: "" });
    montar(ipc);
    await screen.findByText(/A verba Combustível está com saldo negativo/);

    await user.click(screen.getByRole("button", { name: "Dispensar" }));

    await waitFor(() =>
      expect(
        screen.queryByText(/A verba Combustível está com saldo negativo/),
      ).toBeNull(),
    );
    expect(ipc.countPath("/api/v1/alerts/alerta-1/dismiss")).toBe(1);
    expect(await screen.findByText("Nenhum alerta ativo.")).toBeTruthy();
  });

  it("recusa da API mantém o alerta visível e mostra erro", async () => {
    const user = userEvent.setup();
    const ipc = installFakeIpc()
      .onApi(summaryPath("2026-11"), { status: 200, body: summary() })
      .onApi(PENDING_PATH, { status: 200, body: NO_PENDING })
      .onApi(ALERTS_PATH, { status: 200, body: { items: [alert()] } })
      .onApi("/api/v1/alerts/alerta-1/dismiss", {
        status: 409,
        body: { message: "Alerta já resolvido." },
      });
    montar(ipc);
    await screen.findByText(/A verba Combustível está com saldo negativo/);

    await user.click(screen.getByRole("button", { name: "Dispensar" }));

    const erro = await screen.findByRole("alert");
    expect(erro).toHaveTextContent("O alerta não foi dispensado");
    // O alerta continua lá: nada de sumir com ele sem confirmação da API.
    expect(screen.getByText(/A verba Combustível está com saldo negativo/)).toBeTruthy();
  });
});

describe("recorrências pendentes na visão geral", () => {
  it("confirmar uma pendência pede a recarga do resumo do mês", async () => {
    const user = userEvent.setup();
    const onRegistered = vi.fn();
    installFakeIpc()
      .onApi(summaryPath("2026-11"), { status: 200, body: summary() })
      .onApi(PENDING_PATH, { status: 200, body: { current: [pendingOccurrence()], overdue: [] } })
      .onApi(ALERTS_PATH, { status: 200, body: { items: [] } })
      .onApi("/api/v1/recurring-occurrences/oc-1/confirm", {
        status: 200,
        body: { id: "oc-1", status: "CONFIRMED", ledgerEntryId: "l-1" },
      });
    render(
      <OverviewScreen
        month="2026-11"
        onMonthChange={vi.fn()}
        revision={0}
        onRegistered={onRegistered}
        onAuthRequired={semEfeito}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "Confirmar Aluguel" }));

    await waitFor(() => expect(onRegistered).toHaveBeenCalledTimes(1));
  });
});
