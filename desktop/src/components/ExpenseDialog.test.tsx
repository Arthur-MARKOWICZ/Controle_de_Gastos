import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ExpenseDialog } from "./ExpenseDialog";
import { envelope } from "../lib/fixtures";
import { installFakeIpc, resetIpc } from "../lib/test-ipc";
import type { FakeIpc } from "../lib/test-ipc";
import type { SummaryEnvelope } from "../lib/types";

afterEach(() => {
  cleanup();
  resetIpc();
});

const HOJE = "2026-11-12";

function entriesPath(id: string) {
  return `/api/v1/envelopes/${id}/entries`;
}

function montar(
  options: {
    verba?: SummaryEnvelope;
    resposta?: { status: number; body: unknown };
    onClose?: () => void;
    onRegistered?: () => void;
  } = {},
) {
  const verba = options.verba ?? envelope({ id: "env-combustivel", name: "Combustível" });
  const ipc: FakeIpc = installFakeIpc().onApi(
    entriesPath(verba.id),
    options.resposta ?? { status: 201, body: { id: "entrada-1" } },
  );
  const onClose = options.onClose ?? vi.fn();
  const onRegistered = options.onRegistered ?? vi.fn();
  render(
    <ExpenseDialog
      envelope={verba}
      onClose={onClose}
      onRegistered={onRegistered}
      today={HOJE}
    />,
  );
  return { ipc, onClose, onRegistered, verba };
}

function corpoEnviado(ipc: FakeIpc, id: string) {
  const chamada = ipc
    .apiCalls()
    .find((call) => String(call.args.path) === entriesPath(id));
  return chamada ? JSON.parse(String(chamada.args.body)) : null;
}

describe("9.1 valor exato em BRL e data de hoje", () => {
  it('"45,90" digitado vira amount "45.90" com moeda BRL', async () => {
    const user = userEvent.setup();
    const { ipc, onRegistered, verba } = montar();

    await user.type(screen.getByLabelText("Valor"), "4590");
    await user.click(screen.getByRole("button", { name: "Registrar gasto" }));

    await waitFor(() => expect(onRegistered).toHaveBeenCalled());
    expect(corpoEnviado(ipc, verba.id)).toEqual({
      kind: "EXPENSE",
      amount: { amount: "45.90", currency: "BRL" },
      occurredAt: HOJE,
      description: null,
    });
  });

  it("a máscara mostra o valor em pt-BR enquanto o usuário digita", async () => {
    const user = userEvent.setup();
    montar();

    await user.type(screen.getByLabelText("Valor"), "123456");

    expect(screen.getByLabelText("Valor")).toHaveValue("1.234,56");
  });

  it("valor sem duas casas não chama a API", async () => {
    const user = userEvent.setup();
    const { ipc, onRegistered, verba } = montar();

    // A máscara nunca deixa menos de duas casas, então o caso real é o vazio.
    await user.click(screen.getByRole("button", { name: "Registrar gasto" }));

    expect(await screen.findByTestId("resumo-de-erro")).toBeTruthy();
    expect(ipc.countPath(entriesPath(verba.id))).toBe(0);
    expect(onRegistered).not.toHaveBeenCalled();
  });

  it("zero não é gasto válido e não chega à API", async () => {
    const user = userEvent.setup();
    const { ipc, verba } = montar();

    await user.type(screen.getByLabelText("Valor"), "000");
    await user.click(screen.getByRole("button", { name: "Registrar gasto" }));

    expect(await screen.findByTestId("resumo-de-erro")).toBeTruthy();
    expect(ipc.countPath(entriesPath(verba.id))).toBe(0);
  });

  it("a descrição vai junto e é limitada a 140 caracteres", async () => {
    const user = userEvent.setup();
    const { ipc, verba } = montar();
    const descricao = "a".repeat(200);

    await user.type(screen.getByLabelText("Valor"), "4590");
    await user.type(screen.getByLabelText("Descrição (opcional)"), descricao);
    await user.click(screen.getByRole("button", { name: "Registrar gasto" }));

    await waitFor(() => expect(ipc.countPath(entriesPath(verba.id))).toBe(1));
    expect(corpoEnviado(ipc, verba.id).description).toHaveLength(140);
    expect(screen.getByLabelText("Descrição (opcional)")).toHaveAttribute(
      "maxlength",
      "140",
    );
  });

  it("a data enviada é a de hoje no calendário de São Paulo", async () => {
    const user = userEvent.setup();
    const { ipc, verba } = montar();

    await user.type(screen.getByLabelText("Valor"), "4590");
    await user.click(screen.getByRole("button", { name: "Registrar gasto" }));

    await waitFor(() => expect(ipc.countPath(entriesPath(verba.id))).toBe(1));
    expect(corpoEnviado(ipc, verba.id).occurredAt).toBe(HOJE);
  });
});

describe("9.2 autorização para gastar", () => {
  it('participante registra "20.00" em "Mercado"', async () => {
    const user = userEvent.setup();
    const verba = envelope({
      id: "env-mercado",
      name: "Mercado",
      role: "PARTICIPANT",
      available: { amount: "80.50", currency: "BRL" },
    });
    const { ipc, onRegistered } = montar({ verba });

    await user.type(screen.getByLabelText("Valor"), "2000");
    await user.click(screen.getByRole("button", { name: "Registrar gasto" }));

    await waitFor(() => expect(onRegistered).toHaveBeenCalled());
    expect(corpoEnviado(ipc, verba.id).amount).toEqual({
      amount: "20.00",
      currency: "BRL",
    });
  });

  it("recusa da API para terceiro mostra erro e não afirma sucesso", async () => {
    const user = userEvent.setup();
    const verba = envelope({ id: "env-viagem", name: "Viagem" });
    const { onRegistered, onClose } = montar({
      verba,
      resposta: { status: 403, body: { message: "Acesso negado à verba." } },
    });

    await user.type(screen.getByLabelText("Valor"), "2000");
    await user.click(screen.getByRole("button", { name: "Registrar gasto" }));

    const erro = await screen.findByTestId("resumo-de-erro");
    expect(erro).toHaveTextContent("Acesso negado à verba.");
    expect(onRegistered).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
    // Nada no diálogo diz que o gasto entrou.
    expect(document.body.textContent).not.toMatch(/registrado com sucesso|gasto lançado/i);
  });
});

describe("9.3 saldo negativo não bloqueia", () => {
  it('"10.00" menos "25.00" projeta "-15.00" em texto, sem impedir o envio', async () => {
    const user = userEvent.setup();
    const verba = envelope({
      id: "env-lazer",
      name: "Lazer",
      available: { amount: "10.00", currency: "BRL" },
    });
    const { ipc, onRegistered } = montar({ verba });

    await user.type(screen.getByLabelText("Valor"), "2500");

    const projecao = screen.getByTestId("saldo-projetado");
    expect(projecao.querySelector("[data-amount]")).toHaveAttribute(
      "data-amount",
      "-15.00",
    );
    expect(projecao.textContent).toContain("a verba fica negativa");
    expect(projecao.textContent).toContain("O registro é permitido");

    // O botão continua habilitado e o envio acontece.
    const enviar = screen.getByRole("button", { name: "Registrar gasto" });
    expect(enviar).not.toBeDisabled();
    await user.click(enviar);

    await waitFor(() => expect(onRegistered).toHaveBeenCalled());
    expect(corpoEnviado(ipc, verba.id).amount.amount).toBe("25.00");
  });

  it("saldo que continua positivo não mostra aviso de negativo", async () => {
    const user = userEvent.setup();
    montar({
      verba: envelope({ available: { amount: "1200.00", currency: "BRL" } }),
    });

    await user.type(screen.getByLabelText("Valor"), "4590");

    const projecao = screen.getByTestId("saldo-projetado");
    expect(projecao.querySelector("[data-amount]")).toHaveAttribute(
      "data-amount",
      "1154.10",
    );
    expect(projecao.textContent).not.toContain("negativa");
  });
});

describe("9.4 acessibilidade do formulário", () => {
  it("submit inválido foca o resumo de erro e mantém o erro inline", async () => {
    const user = userEvent.setup();
    montar();

    await user.click(screen.getByRole("button", { name: "Registrar gasto" }));

    const resumo = await screen.findByTestId("resumo-de-erro");
    await waitFor(() => expect(resumo).toHaveFocus());
    // O erro inline continua associado ao campo valor.
    const valor = screen.getByLabelText("Valor");
    expect(valor).toHaveAttribute("aria-invalid", "true");
    expect(valor.getAttribute("aria-describedby")).toContain("valor-erro");
    expect(document.getElementById("valor-erro")).toHaveTextContent(/duas casas decimais/);
  });

  it("todos os campos têm rótulo associado", () => {
    montar();

    for (const rotulo of ["Valor", "Data", "Descrição (opcional)"]) {
      expect(screen.getByLabelText(rotulo)).toBeTruthy();
    }
  });

  it("Escape fecha o diálogo", async () => {
    const user = userEvent.setup();
    const { onClose } = montar();

    await user.keyboard("{Escape}");

    expect(onClose).toHaveBeenCalled();
  });

  it("cancelar fecha o diálogo e devolve o foco ao gatilho", async () => {
    const user = userEvent.setup();
    const gatilho = document.createElement("button");
    gatilho.textContent = "Registrar gasto na verba";
    document.body.appendChild(gatilho);
    gatilho.focus();

    const verba = envelope();
    const { unmount } = render(
      <ExpenseDialog
        envelope={verba}
        onClose={() => unmount()}
        onRegistered={vi.fn()}
        today={HOJE}
      />,
    );
    installFakeIpc();

    await user.click(screen.getByRole("button", { name: "Cancelar" }));

    await waitFor(() => expect(gatilho).toHaveFocus());
    gatilho.remove();
  });
});
