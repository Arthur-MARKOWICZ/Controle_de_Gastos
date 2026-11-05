import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../../lib/api";
import { ParticipantsPanel } from "./ParticipantsPanel";

afterEach(cleanup);

describe("ParticipantsPanel", () => {
  it("lista participantes e convida por e-mail", async () => {
    const client = {
      listParticipants: vi.fn().mockResolvedValue({
        items: [{ userId: "u1", email: "ja@example.com", addedAt: "2026-11-01T00:00:00Z" }],
      }),
      inviteParticipant: vi.fn().mockResolvedValue({
        userId: "u2",
        email: "novo@example.com",
        addedAt: "2026-11-05T18:00:00Z",
      }),
      removeParticipant: vi.fn().mockResolvedValue(undefined),
    } as unknown as ApiClient;

    render(
      <ParticipantsPanel
        client={client}
        envelopeId="e1"
        envelopeName="Mercado"
        onClose={vi.fn()}
      />,
    );

    expect(await screen.findByText("ja@example.com")).toBeDefined();

    fireEvent.change(screen.getByLabelText(/E-mail da pessoa/), {
      target: { value: "novo@example.com" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Convidar" }));

    await waitFor(() => {
      expect(client.inviteParticipant).toHaveBeenCalledWith("e1", "novo@example.com");
      expect(screen.getByText("novo@example.com")).toBeDefined();
    });
  });
});
