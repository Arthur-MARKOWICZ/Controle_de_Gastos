import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EnvelopesScreen } from "./EnvelopesScreen";
import { envelope, summary, summaryPath } from "../lib/fixtures";
import { installFakeIpc, resetIpc } from "../lib/test-ipc";
import type { FakeIpc } from "../lib/test-ipc";
import type { SummaryEnvelope } from "../lib/types";

afterEach(() => {
  cleanup();
  resetIpc();
});

const semEfeito = () => {};

function montar(envelopes: SummaryEnvelope[]): FakeIpc {
  const ipc = installFakeIpc().onApi(summaryPath("2026-11"), {
    status: 200,
    body: summary({ envelopes }),
  });
  render(
    <EnvelopesScreen
      month="2026-11"
      onMonthChange={vi.fn()}
      revision={0}
      onRegistered={semEfeito}
      onAuthRequired={semEfeito}
    />,
  );
  return ipc;
}

function linha(nome: string): HTMLElement {
  return screen.getByText(nome).closest("li") as HTMLElement;
}

describe("8.1 verbas visíveis no mês", () => {
  it("o dono vê a própria verba com o disponível da API", async () => {
    montar([envelope({ name: "Moradia", available: { amount: "1200.00", currency: "BRL" } })]);

    await screen.findByText("Moradia");
    expect(linha("Moradia").querySelector("[data-amount]")).toHaveAttribute(
      "data-amount",
      "1200.00",
    );
    expect(linha("Moradia").textContent).toContain("Compromisso fixo");
  });

  it("o participante vê a verba compartilhada", async () => {
    montar([
      envelope({
        id: "env-mercado",
        name: "Mercado",
        purpose: "LIMIT",
        role: "PARTICIPANT",
        available: { amount: "80.50", currency: "BRL" },
      }),
    ]);

    await screen.findByText("Mercado");
    expect(linha("Mercado").querySelector("[data-amount]")).toHaveAttribute(
      "data-amount",
      "80.50",
    );
    expect(linha("Mercado").textContent).toContain("compartilhada");
  });

  it("verba de terceiro não aparece", async () => {
    // A API só devolve o que o usuário pode ver; o cliente não completa a lista.
    montar([envelope({ name: "Moradia" })]);

    await screen.findByText("Moradia");
    expect(screen.queryByText("Viagem")).toBeNull();
  });

  it("a lista vem do resumo, não de GET /api/v1/envelopes", async () => {
    const ipc = montar([envelope({ name: "Moradia" })]);

    await screen.findByText("Moradia");
    expect(ipc.pathsCalled()).toContain(summaryPath("2026-11"));
    // Naquele contrato `available` espelharia `baseAmount` e `isNegative` seria
    // sempre falso — por isso ele não pode ser consultado aqui.
    expect(ipc.pathsCalled().some((p) => p.startsWith("/api/v1/envelopes"))).toBe(false);
  });
});

describe("8.2 verba negativa", () => {
  it('"Lazer" com -15.00 indica negativo em texto e segue permitindo gasto', async () => {
    montar([
      envelope({
        id: "env-lazer",
        name: "Lazer",
        purpose: "LIMIT",
        available: { amount: "-15.00", currency: "BRL" },
        isNegative: true,
      }),
    ]);

    await screen.findByText("Lazer");
    const item = linha("Lazer");
    expect(item.textContent).toContain("Saldo negativo");
    expect(item.querySelector("[data-amount]")).toHaveAttribute("data-amount", "-15.00");
    // A ação de registrar gasto continua oferecida.
    expect(
      screen.getByRole("button", { name: /Registrar gasto/ }),
    ).toBeTruthy();
  });
});

describe("8.3 mês vazio", () => {
  it("sem verbas mostra estado vazio e nenhum valor inventado", async () => {
    montar([]);

    expect(await screen.findByText(/Nenhuma verba neste mês/)).toBeTruthy();
    expect(screen.queryByRole("list", { name: "Verbas do mês" })).toBeNull();
    expect(document.querySelector("[data-amount]")).toBeNull();
  });
});

describe("9.5 atalho da janela", () => {
  it("Ctrl+N abre a captura quando há verba gastável", async () => {
    const user = userEvent.setup();
    montar([envelope({ name: "Moradia" })]);
    await screen.findByText("Moradia");

    await user.keyboard("{Control>}n{/Control}");

    expect(await screen.findByRole("dialog", { name: "Registrar gasto" })).toBeTruthy();
    // O foco vai para o formulário.
    await waitFor(() => expect(screen.getByLabelText("Valor")).toHaveFocus());
  });

  it("sem verba gastável o atalho não abre nada", async () => {
    const user = userEvent.setup();
    montar([]);
    await screen.findByText(/Nenhuma verba neste mês/);

    await user.keyboard("{Control>}n{/Control}");

    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
