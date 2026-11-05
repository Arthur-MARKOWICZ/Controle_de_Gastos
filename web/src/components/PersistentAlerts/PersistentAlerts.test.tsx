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

  it("não renderiza nada sem alertas", async () => {
    const client = clientWith([]);
    const { container } = render(<PersistentAlerts client={client} />);
    await waitFor(() => expect(client.listAlerts).toHaveBeenCalled());
    expect(container.textContent).toBe("");
  });
});
