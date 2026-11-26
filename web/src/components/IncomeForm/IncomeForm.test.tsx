import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../lib/api";
import { IncomeForm } from "./IncomeForm";

const putIncome = vi.fn();

vi.mock("../../auth/auth-context", () => ({ useAuth: () => ({ client: {} }) }));
vi.mock("../../lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../lib/api")>()),
  createApiClient: () => ({ putIncome }),
}));

afterEach(() => {
  cleanup();
  putIncome.mockReset();
});

function typeAmount(value: string) {
  fireEvent.change(screen.getByLabelText(/Valor da renda/), { target: { value } });
}

function save() {
  fireEvent.click(screen.getByRole("button", { name: "Salvar renda" }));
}

describe("IncomeForm", () => {
  it("salva a renda com duas casas e avisa o sucesso", async () => {
    putIncome.mockResolvedValue(undefined);
    const onSuccess = vi.fn();
    render(<IncomeForm onSuccess={onSuccess} onCancel={vi.fn()} />);

    typeAmount("5000,00");
    save();

    await waitFor(() => expect(onSuccess).toHaveBeenCalledOnce());
    expect(putIncome).toHaveBeenCalledWith("5000.00");
  });

  it("formata a máscara enquanto digita", () => {
    render(<IncomeForm onSuccess={vi.fn()} onCancel={vi.fn()} />);

    typeAmount("5000,00");

    expect((screen.getByLabelText(/Valor da renda/) as HTMLInputElement).value).toBe("5.000,00");
  });

  it("valor vazio mostra a mensagem de formato e não chama a API", async () => {
    const onSuccess = vi.fn();
    render(<IncomeForm onSuccess={onSuccess} onCancel={vi.fn()} />);

    save();

    expect((await screen.findByRole("alert")).textContent).toBe("Valor deve ter duas casas decimais, ex: 5.000,00");
    expect(putIncome).not.toHaveBeenCalled();
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it("liga o erro ao campo de renda e move o foco para ele", async () => {
    render(<IncomeForm onSuccess={vi.fn()} onCancel={vi.fn()} />);

    save();

    const amount = screen.getByLabelText(/Valor da renda/);
    const alert = await screen.findByRole("alert");
    expect(amount.getAttribute("aria-invalid")).toBe("true");
    expect(amount.getAttribute("aria-describedby")?.split(" ")).toContain(alert.id);
    expect(document.activeElement).toBe(amount);
  });

  it("renda abaixo das verbas-base mostra o mínimo e quanto falta, sem fechar o formulário", async () => {
    putIncome.mockRejectedValue(new ApiError(422, "INCOME_BELOW_BASE_ALLOCATIONS", "", "Renda insuficiente", {
      requiredMinimum: "3000.00",
      shortfall: "500.00",
    }));
    const onSuccess = vi.fn();
    render(<IncomeForm onSuccess={onSuccess} onCancel={vi.fn()} />);

    typeAmount("2500,00");
    save();

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toMatch(/Renda menor que verbas-base\. Mínimo R\$\s*3\.000,00 \(faltam R\$\s*500,00\)\./);
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it("400 da API pede o formato com duas casas", async () => {
    putIncome.mockRejectedValue(new ApiError(400, "INVALID_REQUEST", "", "Requisição inválida"));
    render(<IncomeForm onSuccess={vi.fn()} onCancel={vi.fn()} />);

    typeAmount("10,50");
    save();

    expect((await screen.findByRole("alert")).textContent)
      .toBe("Valor inválido. Use o formato 5.000,00 com exatamente duas casas decimais.");
  });

  it("falha que não é da API mostra erro inesperado e não confirma sucesso", async () => {
    putIncome.mockRejectedValue(new TypeError("Failed to fetch"));
    const onSuccess = vi.fn();
    render(<IncomeForm onSuccess={onSuccess} onCancel={vi.fn()} />);

    typeAmount("5000,00");
    save();

    expect((await screen.findByRole("alert")).textContent).toBe("Erro inesperado");
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it("enquanto salva, cancelar fica desabilitado", async () => {
    putIncome.mockReturnValue(new Promise(() => undefined));
    const onCancel = vi.fn();
    render(<IncomeForm onSuccess={vi.fn()} onCancel={onCancel} />);

    typeAmount("5000,00");
    save();
    fireEvent.click(await screen.findByRole("button", { name: "Cancelar" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "Cancelar" }).hasAttribute("disabled")).toBe(true));
    expect(onCancel).not.toHaveBeenCalled();
  });
});
