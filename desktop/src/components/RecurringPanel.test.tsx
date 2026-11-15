import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RecurringPanel } from "./RecurringPanel";
import { envelope } from "../lib/fixtures";
import { installFakeIpc, resetIpc } from "../lib/test-ipc";
import type { RecurringEntry } from "../lib/types";

afterEach(() => {
  cleanup();
  resetIpc();
});

const PATH = "/api/v1/envelopes/env-1/recurring-entries";
const aluguel: RecurringEntry = {
  id: "rec-1", envelopeId: "env-1", kind: "EXPENSE", amount: { amount: "1800.00", currency: "BRL" },
  dayOfMonth: 5, description: "Aluguel", active: true, firstCompetence: "2026-12", version: 0,
};

describe("recorrências da verba no desktop", () => {
  it("lista recorrências com valor, dia e estado em texto", async () => {
    installFakeIpc().onApi(PATH, { status: 200, body: [aluguel] });
    render(<RecurringPanel envelope={envelope()} onAuthRequired={() => {}} />);

    expect(await screen.findByText("Aluguel")).toBeTruthy();
    expect(screen.getByText(/1\.800,00 · todo dia 5 · Ativa/)).toBeTruthy();
  });

  it("cria recorrência de gasto com valor decimal", async () => {
    const user = userEvent.setup();
    const ipc = installFakeIpc()
      .onApi(PATH, { status: 200, body: [] })
      .onApi(PATH, { status: 201, body: { ...aluguel, id: "rec-2" } });
    render(<RecurringPanel envelope={envelope()} onAuthRequired={() => {}} />);
    await screen.findByText("Nenhuma recorrência ainda.");

    await user.type(screen.getByLabelText("Descrição (opcional)"), "Aluguel");
    await user.type(screen.getByLabelText("Valor sugerido"), "180000");
    await user.type(screen.getByLabelText("Dia do mês"), "5");
    await user.click(screen.getByRole("button", { name: "Adicionar recorrência" }));

    await waitFor(() => expect(ipc.apiCalls().filter((c) => c.args.method === "POST")).toHaveLength(1));
    const post = ipc.apiCalls().find((c) => c.args.method === "POST");
    expect(JSON.parse(String(post?.args.body))).toEqual({
      kind: "EXPENSE", amount: { amount: "1800.00", currency: "BRL" }, dayOfMonth: 5, description: "Aluguel",
    });
    expect(await screen.findByText("Aluguel")).toBeTruthy();
  });

  it("meta recebe recorrência de aporte", async () => {
    const user = userEvent.setup();
    const ipc = installFakeIpc()
      .onApi(PATH, { status: 200, body: [] })
      .onApi(PATH, { status: 201, body: { ...aluguel, kind: "CONTRIBUTION" } });
    render(<RecurringPanel envelope={envelope({ purpose: "GOAL" })} onAuthRequired={() => {}} />);
    await screen.findByText("Nenhuma recorrência ainda.");

    await user.type(screen.getByLabelText("Valor sugerido"), "50000");
    await user.type(screen.getByLabelText("Dia do mês"), "10");
    await user.click(screen.getByRole("button", { name: "Adicionar recorrência" }));

    await waitFor(() => {
      const post = ipc.apiCalls().find((c) => c.args.method === "POST");
      expect(JSON.parse(String(post?.args.body)).kind).toBe("CONTRIBUTION");
    });
  });

  it("dia 31 avisa sobre meses curtos e dia 32 não sai para a API", async () => {
    const user = userEvent.setup();
    const ipc = installFakeIpc().onApi(PATH, { status: 200, body: [] });
    render(<RecurringPanel envelope={envelope()} onAuthRequired={() => {}} />);
    await screen.findByText("Nenhuma recorrência ainda.");

    await user.type(screen.getByLabelText("Valor sugerido"), "1000");
    await user.type(screen.getByLabelText("Dia do mês"), "31");
    expect(screen.getByText(/último dia do mês/)).toBeTruthy();

    await user.clear(screen.getByLabelText("Dia do mês"));
    await user.type(screen.getByLabelText("Dia do mês"), "32");
    await user.click(screen.getByRole("button", { name: "Adicionar recorrência" }));

    expect(await screen.findByText("Escolha um dia entre 1 e 31.")).toBeTruthy();
    expect(ipc.apiCalls().filter((c) => c.args.method === "POST")).toHaveLength(0);
  });

  it("desativa pela API com PUT e mostra o novo estado", async () => {
    const user = userEvent.setup();
    const ipc = installFakeIpc()
      .onApi(PATH, { status: 200, body: [aluguel] })
      .onApi(`${PATH}/rec-1`, { status: 200, body: { ...aluguel, active: false } });
    render(<RecurringPanel envelope={envelope()} onAuthRequired={() => {}} />);
    await screen.findByText("Aluguel");

    await user.click(screen.getByRole("button", { name: "Desativar Aluguel" }));

    expect(await screen.findByText(/todo dia 5 · Inativa/)).toBeTruthy();
    const put = ipc.apiCalls().find((c) => c.args.method === "PUT");
    expect(put?.args.path).toBe(`${PATH}/rec-1`);
    expect(JSON.parse(String(put?.args.body))).toEqual({
      kind: "EXPENSE", amount: { amount: "1800.00", currency: "BRL" }, dayOfMonth: 5, description: "Aluguel", active: false,
    });
  });

  it("edita o valor mantendo o estado ativo", async () => {
    const user = userEvent.setup();
    const ipc = installFakeIpc()
      .onApi(PATH, { status: 200, body: [aluguel] })
      .onApi(`${PATH}/rec-1`, { status: 200, body: { ...aluguel, amount: { amount: "1900.00", currency: "BRL" } } });
    render(<RecurringPanel envelope={envelope()} onAuthRequired={() => {}} />);
    await screen.findByText("Aluguel");

    await user.click(screen.getByRole("button", { name: "Editar Aluguel" }));
    const valor = screen.getByLabelText("Valor sugerido");
    await user.clear(valor);
    await user.type(valor, "190000");
    await user.click(screen.getByRole("button", { name: "Salvar alterações" }));

    expect(await screen.findByText(/1\.900,00 · todo dia 5 · Ativa/)).toBeTruthy();
    const put = ipc.apiCalls().find((c) => c.args.method === "PUT");
    expect(JSON.parse(String(put?.args.body))).toMatchObject({ amount: { amount: "1900.00" }, active: true });
  });

  it("exclui com DELETE só depois de confirmar", async () => {
    const user = userEvent.setup();
    const ipc = installFakeIpc()
      .onApi(PATH, { status: 200, body: [aluguel] })
      .onApi(`${PATH}/rec-1`, { status: 204, body: "" });
    render(<RecurringPanel envelope={envelope()} onAuthRequired={() => {}} />);
    await screen.findByText("Aluguel");

    await user.click(screen.getByRole("button", { name: "Excluir Aluguel" }));
    expect(ipc.apiCalls().filter((c) => c.args.method === "DELETE")).toHaveLength(0);
    await user.click(screen.getByRole("button", { name: "Confirmar exclusão de Aluguel" }));

    await waitFor(() => expect(screen.queryByText("Aluguel")).toBeNull());
    expect(ipc.apiCalls().find((c) => c.args.method === "DELETE")?.args.path).toBe(`${PATH}/rec-1`);
  });
});
