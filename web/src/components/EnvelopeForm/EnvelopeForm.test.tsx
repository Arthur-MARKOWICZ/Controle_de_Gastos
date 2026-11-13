import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { EnvelopeForm } from "./EnvelopeForm";

const request = vi.hoisted(() => vi.fn());

vi.mock("../../auth/auth-context", () => ({
  useAuth: () => ({ client: { request } }),
}));

afterEach(() => {
  cleanup();
  request.mockReset();
});

describe("EnvelopeForm", () => {
  it("pré-seleciona a natureza informada e restringe as opções ao contexto da página", () => {
    render(
      <EnvelopeForm
        initialPurpose="FIXED"
        allowedPurposes={["LIMIT", "FIXED"]}
        onSuccess={vi.fn()}
        onCancel={vi.fn()}
      />,
    );

    const select = screen.getByLabelText("Natureza") as HTMLSelectElement;
    expect(select.value).toBe("FIXED");
    expect(screen.getByRole("option", { name: "Compromisso fixo" })).toBeDefined();
    expect(screen.getByRole("option", { name: "Limite de gasto" })).toBeDefined();
    expect(screen.queryByRole("option", { name: "Meta de aporte" })).toBeNull();
    expect(screen.queryByRole("option", { name: "Meta de acumulação" })).toBeNull();
  });

  it("oferece link para o glossário quando o callback é informado", () => {
    const onOpenGlossary = vi.fn();
    render(
      <EnvelopeForm
        onSuccess={vi.fn()}
        onCancel={vi.fn()}
        onOpenGlossary={onOpenGlossary}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Ver glossário" }));
    expect(onOpenGlossary).toHaveBeenCalled();
  });

  it("não menciona jargão de API no texto de ajuda", () => {
    render(<EnvelopeForm onSuccess={vi.fn()} onCancel={vi.fn()} />);

    expect(screen.queryByText(/enviado como/i)).toBeNull();
    expect(screen.queryByText(/America\/Sao_Paulo/i)).toBeNull();
    expect(screen.getByText(/vírgula para centavos/i)).toBeDefined();
  });

  it("explica o 400 allocation.exceeds_income e o excesso quando a API envia", async () => {
    request.mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({
        type: "about:blank",
        title: "Alocação excede a renda",
        status: 400,
        detail: "A soma das verbas-base excede a renda mensal",
        code: "allocation.exceeds_income",
        excess: "10.00",
        currency: "BRL",
      }),
    });
    const onSuccess = vi.fn();
    render(<EnvelopeForm onSuccess={onSuccess} onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Nome da verba"), { target: { value: "Lazer" } });
    fireEvent.change(screen.getByLabelText("Valor-base (BRL)"), { target: { value: "1000" } });
    fireEvent.click(screen.getByRole("button", { name: "Criar verba" }));

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toMatch(/excede a renda/i);
    expect(alert.textContent).toMatch(/10,00/);
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it("liga o nome vazio ao campo de nome e foca nele", () => {
    const onSuccess = vi.fn();
    render(<EnvelopeForm onSuccess={onSuccess} onCancel={vi.fn()} />);

    // só espaços passa pelo `required` nativo e chega à nossa validação
    fireEvent.change(screen.getByLabelText("Nome da verba"), { target: { value: "   " } });
    fireEvent.change(screen.getByLabelText("Valor-base (BRL)"), { target: { value: "40000" } });
    fireEvent.click(screen.getByRole("button", { name: "Criar verba" }));

    const name = screen.getByLabelText("Nome da verba");
    expect(name.getAttribute("aria-invalid")).toBe("true");
    expect(name.getAttribute("aria-describedby")).toBeTruthy();
    expect(document.activeElement).toBe(name);
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it("liga allocation.exceeds_income ao campo de valor, não só ao topo", async () => {
    request.mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({
        type: "about:blank", title: "Alocação excede a renda", status: 400,
        detail: "A soma das verbas-base excede a renda mensal",
        code: "allocation.exceeds_income", excess: "10.00", currency: "BRL",
      }),
    });
    render(<EnvelopeForm onSuccess={vi.fn()} onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Nome da verba"), { target: { value: "Lazer" } });
    fireEvent.change(screen.getByLabelText("Valor-base (BRL)"), { target: { value: "1000" } });
    fireEvent.click(screen.getByRole("button", { name: "Criar verba" }));

    const alert = await screen.findByRole("alert");
    const amount = screen.getByLabelText("Valor-base (BRL)");
    expect(amount.getAttribute("aria-invalid")).toBe("true");
    expect(amount.getAttribute("aria-describedby")?.split(" ")).toContain(alert.id);
    expect(alert.textContent).toMatch(/10,00/);
  });

  it("deixa o Notice do topo para erro inesperado", async () => {
    request.mockRejectedValue(new Error("boom"));
    render(<EnvelopeForm onSuccess={vi.fn()} onCancel={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Nome da verba"), { target: { value: "Lazer" } });
    fireEvent.change(screen.getByLabelText("Valor-base (BRL)"), { target: { value: "1000" } });
    fireEvent.click(screen.getByRole("button", { name: "Criar verba" }));

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toMatch(/inesperado/i);
    expect(screen.getByLabelText("Valor-base (BRL)").getAttribute("aria-invalid")).toBeNull();
  });
});
