import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { EnvelopeDTO } from "../../lib/api";
import { MetasPage } from "./MetasPage";

const state = vi.hoisted(() => ({
  request: vi.fn(),
  refresh: vi.fn(),
  summary: null as unknown,
  loading: false,
  error: null as string | null,
}));

vi.mock("../../auth/auth-context", () => ({ useAuth: () => ({ client: { request: state.request } }) }));
vi.mock("../../hooks/useLedgerSummary", () => ({
  useLedgerSummary: () => ({ summary: state.summary, loading: state.loading, busy: false, error: state.error, refresh: state.refresh }),
}));
vi.mock("../AppShell/AppShell", () => ({ AppShell: ({ children }: { children: ReactNode }) => <main>{children}</main> }));
vi.mock("../ui/Dialog", () => ({
  Dialog: ({ open, title, children }: { open: boolean; title: string; children: ReactNode }) =>
    open ? <div role="dialog" aria-label={title}>{children}</div> : null,
}));
vi.mock("../EnvelopeForm/EnvelopeForm", () => ({ EnvelopeForm: () => <form aria-label="Formulário de meta" /> }));
vi.mock("../ExpenseForm/ExpenseForm", () => ({ ExpenseForm: () => <form aria-label="Formulário de aporte" /> }));
vi.mock("../ParticipantsPanel/ParticipantsPanel", () => ({ ParticipantsPanel: () => <div /> }));
vi.mock("../EnvelopeCard/EnvelopeCard", () => ({
  EnvelopeCard: ({ envelope, onArchive, onToggleWealthAllocation, onRegisterExpense }: {
    envelope: EnvelopeDTO; onArchive(e: EnvelopeDTO): void; onToggleWealthAllocation(e: EnvelopeDTO): void; onRegisterExpense(e: EnvelopeDTO): void;
  }) => (
    <li>
      {envelope.name}
      <button onClick={() => onArchive(envelope)}>Encerrar {envelope.name}</button>
      <button onClick={() => onToggleWealthAllocation(envelope)}>Patrimônio {envelope.name}</button>
      <button onClick={() => onRegisterExpense(envelope)}>Aportar {envelope.name}</button>
    </li>
  ),
}));

const brl = (amount: string) => ({ amount, currency: "BRL" as const });

function envelope(id: string, purpose: EnvelopeDTO["purpose"], role: EnvelopeDTO["role"] = "OWNER"): EnvelopeDTO {
  return {
    id, ownerId: "o", name: `Meta ${id}`, purpose, baseAmount: brl("100.00"), targetAmount: null, targetReachedAt: null,
    available: brl("0.00"), isNegative: false, role, wealthAllocation: false, createdAt: "2026-09-01T00:00:00Z", archivedAt: null, version: 3,
  };
}

function withEnvelopes(...envelopes: EnvelopeDTO[]) {
  state.summary = { income: null, allocated: brl("0.00"), unallocated: brl("0.00"), usagePct: 0, envelopes };
}

function problem(status: number, detail: string) {
  return new Response(JSON.stringify({ status, code: "X", detail, title: "x" }), { status, headers: { "Content-Type": "application/json" } });
}

beforeEach(() => {
  state.request.mockReset();
  state.refresh.mockReset();
  state.loading = false;
  state.error = null;
  withEnvelopes();
});
afterEach(cleanup);

describe("MetasPage", () => {
  it("separa metas de aporte e de acumulação, ignorando outras verbas", () => {
    withEnvelopes(envelope("aporte", "GOAL"), envelope("alvo", "SAVINGS_TARGET"), envelope("mercado", "LIMIT"));
    render(<MetasPage email="p@example.com" onLogout={vi.fn()} />);

    expect(screen.getByRole("region", { name: "Metas de aporte" }).textContent).toContain("Meta aporte");
    expect(screen.getByRole("region", { name: "Metas de acumulação" }).textContent).toContain("Meta alvo");
    expect(screen.queryByText("Meta mercado")).toBeNull();
  });

  it("encerrar uma meta pede confirmação, arquiva e recarrega", async () => {
    withEnvelopes(envelope("aporte", "GOAL"));
    state.request.mockResolvedValueOnce(new Response(null, { status: 204 }));
    render(<MetasPage email="p@example.com" onLogout={vi.fn()} />);

    fireEvent.click(screen.getByText("Encerrar Meta aporte"));
    fireEvent.click(screen.getByRole("button", { name: "Encerrar" }));

    await waitFor(() => expect(screen.getByText('Meta "Meta aporte" encerrada.')).toBeDefined());
    expect(state.request).toHaveBeenCalledWith("/api/v1/envelopes/aporte/archive", { method: "POST" });
    expect(state.refresh).toHaveBeenCalled();
  });

  it("participante abre o formulário como gasto, não como aporte", () => {
    withEnvelopes(envelope("compartilhada", "GOAL", "PARTICIPANT"));
    render(<MetasPage email="p@example.com" onLogout={vi.fn()} />);

    fireEvent.click(screen.getByText("Aportar Meta compartilhada"));

    expect(screen.getByRole("dialog", { name: "Registrar gasto em Meta compartilhada" })).toBeDefined();
  });

  it("sem metas mostra o estado vazio nas duas seções", () => {
    render(<MetasPage email="p@example.com" onLogout={vi.fn()} />);

    expect(screen.getAllByText("Nenhuma meta criada ainda.")).toHaveLength(2);
  });

  it("erro ao carregar mostra a mensagem e permite tentar de novo", () => {
    state.error = "Sessão expirada";
    render(<MetasPage email="p@example.com" onLogout={vi.fn()} />);

    expect(screen.getByText(/Sessão expirada/)).toBeDefined();
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(state.refresh).toHaveBeenCalled();
  });

  it("falha ao encerrar a meta mostra o erro da API e não anuncia sucesso", async () => {
    withEnvelopes(envelope("aporte", "GOAL"));
    state.request.mockResolvedValueOnce(problem(403, "Somente o proprietário pode arquivar a verba"));
    render(<MetasPage email="p@example.com" onLogout={vi.fn()} />);

    fireEvent.click(screen.getByText("Encerrar Meta aporte"));
    fireEvent.click(screen.getByRole("button", { name: "Encerrar" }));

    await waitFor(() => expect(screen.getByText("Somente o proprietário pode arquivar a verba")).toBeDefined());
    expect(screen.queryByText(/encerrada\./)).toBeNull();
  });

  it("conflito ao marcar patrimônio mostra mensagem de falha", async () => {
    withEnvelopes(envelope("aporte", "GOAL"));
    state.request.mockResolvedValueOnce(problem(409, "A verba foi alterada em outra requisição"));
    render(<MetasPage email="p@example.com" onLogout={vi.fn()} />);

    fireEvent.click(screen.getByText("Patrimônio Meta aporte"));

    await waitFor(() => expect(screen.getByText("Não foi possível atualizar a classificação da meta")).toBeDefined());
    expect(JSON.parse(state.request.mock.calls[0][1].body)).toEqual({ version: 3, wealthAllocation: true });
  });

  it("falha de rede ao encerrar não deixa a confirmação aberta nem quebra a página", async () => {
    withEnvelopes(envelope("aporte", "GOAL"));
    state.request.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    render(<MetasPage email="p@example.com" onLogout={vi.fn()} />);

    fireEvent.click(screen.getByText("Encerrar Meta aporte"));
    fireEvent.click(screen.getByRole("button", { name: "Encerrar" }));

    await waitFor(() => expect(screen.getByText("Failed to fetch")).toBeDefined());
    expect(screen.queryByRole("button", { name: "Encerrar" })).toBeNull();
  });
});
