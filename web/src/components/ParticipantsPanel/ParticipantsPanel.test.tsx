import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, type ApiClient } from "../../lib/api";
import { ParticipantsPanel } from "./ParticipantsPanel";

afterEach(cleanup);

const existing = { userId: "u1", email: "ja@example.com", addedAt: "2026-11-01T00:00:00Z" };
const invited = { userId: "u2", email: "novo@example.com", addedAt: "2026-11-05T18:00:00Z" };

function clientWith(overrides: Partial<Record<keyof ApiClient, unknown>> = {}) {
  return {
    listParticipants: vi.fn().mockResolvedValue({ items: [existing] }),
    inviteParticipant: vi.fn().mockResolvedValue(invited),
    removeParticipant: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  } as unknown as ApiClient;
}

function renderPanel(client: ApiClient) {
  render(<ParticipantsPanel client={client} envelopeId="e1" envelopeName="Mercado" onClose={vi.fn()} />);
}

function typeEmail(value: string) {
  fireEvent.change(screen.getByLabelText(/E-mail da pessoa/), { target: { value } });
}

describe("ParticipantsPanel", () => {
  it("lista participantes e convida por e-mail", async () => {
    const client = clientWith();
    renderPanel(client);

    expect(await screen.findByText("ja@example.com")).toBeDefined();

    typeEmail("novo@example.com");
    fireEvent.click(screen.getByRole("button", { name: "Convidar" }));

    await waitFor(() => {
      expect(client.inviteParticipant).toHaveBeenCalledWith("e1", "novo@example.com");
      expect(screen.getByText("novo@example.com")).toBeDefined();
    });
  });

  it("remove o participante da lista depois da confirmação da API", async () => {
    const client = clientWith();
    renderPanel(client);
    await screen.findByText("ja@example.com");

    fireEvent.click(screen.getByRole("button", { name: "Remover" }));

    await waitFor(() => expect(screen.queryByText("ja@example.com")).toBeNull());
    expect(client.removeParticipant).toHaveBeenCalledWith("e1", "u1");
    expect(screen.getByText("Nenhum participante ainda.")).toBeDefined();
  });

  it("convite repetido para quem já participa não duplica a linha", async () => {
    const client = clientWith({ inviteParticipant: vi.fn().mockResolvedValue(existing) });
    renderPanel(client);
    await screen.findByText("ja@example.com");

    typeEmail("ja@example.com");
    fireEvent.click(screen.getByRole("button", { name: "Convidar" }));

    await waitFor(() => expect(client.inviteParticipant).toHaveBeenCalled());
    expect(screen.getAllByText("ja@example.com")).toHaveLength(1);
  });

  it("e-mail só com espaços mantém o convite desabilitado e não chama a API", async () => {
    const client = clientWith();
    renderPanel(client);
    await screen.findByText("ja@example.com");

    typeEmail("   ");
    const button = screen.getByRole("button", { name: "Convidar" }) as HTMLButtonElement;
    fireEvent.click(button);

    expect(button.disabled).toBe(true);
    expect(client.inviteParticipant).not.toHaveBeenCalled();
  });

  it("falha ao remover mantém o participante na lista", async () => {
    const client = clientWith({ removeParticipant: vi.fn().mockRejectedValue(new Error("rede")) });
    renderPanel(client);
    await screen.findByText("ja@example.com");

    fireEvent.click(screen.getByRole("button", { name: "Remover" }));

    expect(await screen.findByText("Não foi possível remover o participante.")).toBeDefined();
    expect(screen.getByText("ja@example.com")).toBeDefined();
  });

  it("falha ao carregar mostra a mensagem de erro e nenhuma pessoa", async () => {
    const client = clientWith({ listParticipants: vi.fn().mockRejectedValue(new Error("rede")) });
    renderPanel(client);

    expect(await screen.findByText("Não foi possível carregar os participantes.")).toBeDefined();
    expect(screen.queryByText("ja@example.com")).toBeNull();
  });

  it("convite recusado pela API mostra a mensagem do problem e preserva o e-mail digitado", async () => {
    const client = clientWith({
      inviteParticipant: vi.fn().mockRejectedValue(
        new ApiError(403, "ENVELOPE_OWNER_ONLY", "Só o dono pode convidar.", "Proibido"),
      ),
    });
    renderPanel(client);
    await screen.findByText("ja@example.com");

    typeEmail("novo@example.com");
    fireEvent.click(screen.getByRole("button", { name: "Convidar" }));

    expect(await screen.findByText("Só o dono pode convidar.")).toBeDefined();
    expect((screen.getByLabelText(/E-mail da pessoa/) as HTMLInputElement).value).toBe("novo@example.com");
    expect(screen.queryByText("novo@example.com", { selector: "span" })).toBeNull();
  });

  it("falha desconhecida no convite mostra a mensagem genérica", async () => {
    const client = clientWith({ inviteParticipant: vi.fn().mockRejectedValue("falha") });
    renderPanel(client);
    await screen.findByText("ja@example.com");

    typeEmail("novo@example.com");
    fireEvent.click(screen.getByRole("button", { name: "Convidar" }));

    expect(await screen.findByText("Não foi possível convidar.")).toBeDefined();
  });
});
