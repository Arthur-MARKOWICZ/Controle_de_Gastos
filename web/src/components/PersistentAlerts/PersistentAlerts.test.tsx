import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ApiClient, InAppAlertDTO } from "../../lib/api";
import { PersistentAlerts } from "./PersistentAlerts";

afterEach(cleanup);

function clientWith(items: InAppAlertDTO[]): ApiClient {
  return {
    listAlerts: vi.fn().mockResolvedValue({ items }),
    dismissAlert: vi.fn().mockResolvedValue(undefined),
  } as unknown as ApiClient;
}

describe("PersistentAlerts", () => {
  it("lista alertas ativos e permite dispensar", async () => {
    const alert: InAppAlertDTO = {
      id: "a1",
      type: "NEGATIVE_BALANCE",
      envelopeId: "e1",
      envelopeName: "Mercado",
      referenceMonth: "2026-11",
      createdAt: "2026-11-05T18:00:00Z",
    };
    const client = clientWith([alert]);
    render(<PersistentAlerts client={client} />);

    expect(await screen.findByText(/Mercado/)).toBeDefined();
    expect(screen.getByText(/2026-11/)).toBeDefined();

    fireEvent.click(screen.getByRole("button", { name: "Fechar aviso" }));

    await waitFor(() => {
      expect(client.dismissAlert).toHaveBeenCalledWith("a1");
      expect(screen.queryByText(/Mercado/)).toBeNull();
    });
  });

  it("descreve o gasto de participante em verba compartilhada sem dizer quem nem quanto", async () => {
    const client = clientWith([{
      id: "a2",
      type: "SHARED_EXPENSE",
      envelopeId: "e2",
      envelopeName: "Casa",
      referenceMonth: "2026-11",
      createdAt: "2026-11-05T18:00:00Z",
    }]);
    render(<PersistentAlerts client={client} />);

    const region = await screen.findByRole("region", { name: "Alertas persistentes" });
    expect(region.textContent).toContain("Novo gasto em verba compartilhada");
    expect(region.textContent).toContain("Casa");
    expect(region.textContent).not.toContain("saldo negativo");
  });

  it("não renderiza nada sem alertas", async () => {
    const client = clientWith([]);
    const { container } = render(<PersistentAlerts client={client} />);
    await waitFor(() => expect(client.listAlerts).toHaveBeenCalled());
    expect(container.textContent).toBe("");
  });

  it("falha ao carregar mostra a mensagem e não lista alerta nenhum", async () => {
    const client = { listAlerts: vi.fn().mockRejectedValue(new Error("500")), dismissAlert: vi.fn() } as unknown as ApiClient;
    render(<PersistentAlerts client={client} />);

    expect(await screen.findByText("Não foi possível carregar os alertas persistentes.")).toBeDefined();
    expect(screen.queryByRole("region", { name: "Alertas persistentes" })).toBeNull();
  });

  it("tentar novamente depois da falha recarrega e mostra os alertas", async () => {
    const client = {
      listAlerts: vi.fn()
        .mockRejectedValueOnce(new Error("rede"))
        .mockResolvedValueOnce({ items: [negative] }),
      dismissAlert: vi.fn(),
    } as unknown as ApiClient;
    render(<PersistentAlerts client={client} />);

    fireEvent.click(await screen.findByRole("button", { name: "Tentar novamente" }));

    expect(await screen.findByText(/Mercado/)).toBeDefined();
    expect(client.listAlerts).toHaveBeenCalledTimes(2);
  });

  it("falha ao dispensar mostra a mensagem e o alerta continua ativo ao recarregar", async () => {
    const client = {
      listAlerts: vi.fn().mockResolvedValue({ items: [negative] }),
      dismissAlert: vi.fn().mockRejectedValue(new Error("409")),
    } as unknown as ApiClient;
    render(<PersistentAlerts client={client} />);

    fireEvent.click(await screen.findByRole("button", { name: "Fechar aviso" }));
    expect(await screen.findByText("Não foi possível dispensar o alerta.")).toBeDefined();

    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(await screen.findByText(/Mercado/)).toBeDefined();
  });

  it("enquanto dispensa, o aviso não oferece fechar de novo", async () => {
    let finish: () => void = () => undefined;
    const client = {
      listAlerts: vi.fn().mockResolvedValue({ items: [negative] }),
      dismissAlert: vi.fn(() => new Promise<void>((resolve) => { finish = resolve; })),
    } as unknown as ApiClient;
    render(<PersistentAlerts client={client} />);

    fireEvent.click(await screen.findByRole("button", { name: "Fechar aviso" }));

    await waitFor(() => expect(screen.queryByRole("button", { name: "Fechar aviso" })).toBeNull());
    expect(client.dismissAlert).toHaveBeenCalledOnce();
    finish();
    await waitFor(() => expect(screen.queryByText(/Mercado/)).toBeNull());
  });

  it("recarrega quando a chave de atualização muda", async () => {
    const client = clientWith([negative]);
    const { rerender } = render(<PersistentAlerts client={client} refreshKey={1} />);
    await waitFor(() => expect(client.listAlerts).toHaveBeenCalledTimes(1));

    rerender(<PersistentAlerts client={client} refreshKey={2} />);

    await waitFor(() => expect(client.listAlerts).toHaveBeenCalledTimes(2));
  });
});

const negative: InAppAlertDTO = {
  id: "a9",
  type: "NEGATIVE_BALANCE",
  envelopeId: "e9",
  envelopeName: "Mercado",
  referenceMonth: "2026-11",
  createdAt: "2026-11-05T18:00:00Z",
};
