import { readFileSync } from "node:fs";
import { join } from "node:path";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EnvelopeCard } from "./EnvelopeCard";

const cardCss = readFileSync(join(process.cwd(), "src/components/EnvelopeCard/EnvelopeCard.module.css"), "utf8");
const cardSource = readFileSync(join(process.cwd(), "src/components/EnvelopeCard/EnvelopeCard.tsx"), "utf8");

describe("EnvelopeCard", () => {
  it("mostra o restante e aumenta a barra de uma meta de aporte acumulada", () => {
    const envelope = {
      id: "goal", ownerId: "owner", name: "Investimentos", purpose: "GOAL" as const,
      baseAmount: { amount: "100.00", currency: "BRL" as const }, targetAmount: null, targetReachedAt: null,
      available: { amount: "100.00", currency: "BRL" as const }, isNegative: false, role: "OWNER" as const,
      createdAt: "2026-09-01T00:00:00Z", archivedAt: null, version: 0,
      goalProgress: { plannedAmount: { amount: "100.00", currency: "BRL" as const }, contributedAmount: { amount: "0.00", currency: "BRL" as const }, remainingAmount: { amount: "100.00", currency: "BRL" as const }, percent: 0 },
    };
    const { rerender, unmount } = render(<ul><EnvelopeCard variant="verbas" envelope={envelope} /></ul>);

    expect(screen.getAllByText(/Faltam R\$\s*100,00/)).toHaveLength(2);
    expect(screen.getByRole("progressbar", { name: "Progresso de Investimentos" }).getAttribute("value")).toBe("0");

    rerender(<ul><EnvelopeCard variant="verbas" envelope={{ ...envelope, goalProgress: { plannedAmount: { amount: "200.00", currency: "BRL" }, contributedAmount: { amount: "20.00", currency: "BRL" }, remainingAmount: { amount: "180.00", currency: "BRL" }, percent: 10 } }} /></ul>);

    expect(screen.getAllByText(/Faltam R\$\s*180,00/)).toHaveLength(2);
    expect(screen.getByText(/meta acumulada R\$\s*200,00/)).toBeDefined();
    expect(screen.getByRole("progressbar", { name: "Progresso de Investimentos" }).getAttribute("value")).toBe("10");
    unmount();
  });

  it("anuncia o saldo negativo por texto, sem depender só da cor", () => {
    render(<ul><EnvelopeCard envelope={{
      id: "limit", ownerId: "owner", name: "Mercado", purpose: "LIMIT",
      baseAmount: { amount: "500.00", currency: "BRL" }, targetAmount: null, targetReachedAt: null,
      available: { amount: "-35.10", currency: "BRL" }, isNegative: true, role: "OWNER", wealthAllocation: false, createdAt: "2026-09-01T00:00:00Z", archivedAt: null, version: 0,
    }} /></ul>);

    expect(screen.getByRole("alert").textContent).toMatch(/Saldo negativo -R\$\s*35,10/);
    cleanup();
  });

  it("mostra só o pill de natureza, sem purposeRule repetida", () => {
    render(<ul><EnvelopeCard variant="verbas" envelope={{
      id: "limit", ownerId: "owner", name: "Mercado", purpose: "LIMIT",
      baseAmount: { amount: "100.00", currency: "BRL" }, targetAmount: null, targetReachedAt: null,
      available: { amount: "100.00", currency: "BRL" }, isNegative: false, role: "OWNER",
      wealthAllocation: false, createdAt: "2026-10-01T00:00:00Z", archivedAt: null, version: 0,
    }} /></ul>);

    expect(screen.getByText("Limite de gasto")).toBeDefined();
    expect(screen.queryByText(/saldo reinicia/i)).toBeNull();
    expect(screen.queryByText(/não reinicia/i)).toBeNull();
    expect(screen.queryByText(/aporte mensal/i)).toBeNull();
    expect(screen.queryByText(/reserva contínua/i)).toBeNull();
  });

  it("mostra ritmo da API só em LIMIT e omite em GOAL", () => {
    const pace = {
      envelopeId: "limit", name: "Mercado", capacity: "400.00", spent: "200.00",
      projectedMonthSpend: "620.00", dailyRemainingAllowance: "9.09",
      projectedOverrunDay: "2026-10-20", month: "2026-10",
    };
    const { rerender, unmount } = render(<ul><EnvelopeCard envelope={{
      id: "limit", ownerId: "owner", name: "Mercado", purpose: "LIMIT",
      baseAmount: { amount: "400.00", currency: "BRL" }, targetAmount: null, targetReachedAt: null,
      available: { amount: "200.00", currency: "BRL" }, isNegative: false, role: "OWNER",
      wealthAllocation: false, createdAt: "2026-10-01T00:00:00Z", archivedAt: null, version: 0,
    }} pace={pace} /></ul>);

    expect(screen.getByText(/Estouro projetado em 20\/10\/2026/)).toBeDefined();

    rerender(<ul><EnvelopeCard envelope={{
      id: "goal", ownerId: "owner", name: "Viagem", purpose: "GOAL",
      baseAmount: { amount: "100.00", currency: "BRL" }, targetAmount: null, targetReachedAt: null,
      available: { amount: "100.00", currency: "BRL" }, isNegative: false, role: "OWNER",
      wealthAllocation: false, createdAt: "2026-10-01T00:00:00Z", archivedAt: null, version: 0,
      goalProgress: {
        plannedAmount: { amount: "100.00", currency: "BRL" },
        contributedAmount: { amount: "0.00", currency: "BRL" },
        remainingAmount: { amount: "100.00", currency: "BRL" },
        percent: 0,
      },
    }} pace={pace} /></ul>);

    expect(screen.queryByText(/Estouro projetado/)).toBeNull();
    unmount();
  });

  it("mostra o progresso de uma meta de acumulação sem repetir a regra", () => {
    render(<ul><EnvelopeCard variant="verbas" envelope={{
      id: "target", ownerId: "owner", name: "Notebook", purpose: "SAVINGS_TARGET",
      baseAmount: { amount: "0.00", currency: "BRL" }, targetAmount: { amount: "1000.00", currency: "BRL" }, targetReachedAt: null,
      available: { amount: "100.00", currency: "BRL" }, isNegative: false, role: "OWNER", wealthAllocation: false, createdAt: "2026-09-01T00:00:00Z", archivedAt: null, version: 0,
    }} /></ul>);

    expect(screen.getByText("Meta de acumulação")).toBeDefined();
    expect(screen.queryByText(/saldo não reinicia/)).toBeNull();
    expect(screen.getByText(/10% concluído/)).toBeDefined();
    expect(screen.getByRole("progressbar", { name: "Progresso de Notebook" }).getAttribute("value")).toBe("10");
  });

  it("usa ProgressRing compacto e ProgressBar hairline visível", () => {
    expect(cardSource).toMatch(/ProgressRing/);
    expect(cardSource).not.toMatch(/className=\{styles\.srOnly\}/);
    expect(cardCss).toMatch(/\.ringWrap\s*\{[^}]*width:\s*2\.25rem/);
    expect(cardCss).toMatch(/\.ringWrap\s*\{[^}]*height:\s*2\.25rem/);
    expect(cardCss).not.toMatch(/3\.25rem/);
    expect(cardCss).not.toMatch(/\.srOnly\s*\{/);
  });

  it("mostra Participantes só para o owner e Registrar gasto para participante", () => {
    const owner = {
      id: "limit", ownerId: "owner", name: "Mercado", purpose: "LIMIT" as const,
      baseAmount: { amount: "100.00", currency: "BRL" as const }, targetAmount: null, targetReachedAt: null,
      available: { amount: "100.00", currency: "BRL" as const }, isNegative: false, role: "OWNER" as const,
      createdAt: "2026-10-01T00:00:00Z", archivedAt: null, version: 0,
    };
    const { rerender, unmount } = render(
      <ul>
        <EnvelopeCard
          envelope={owner}
          onRegisterExpense={() => undefined}
          onManageParticipants={() => undefined}
        />
      </ul>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Mais ações de Mercado" }));
    expect(screen.getByRole("menuitem", { name: "Participantes de Mercado" })).toBeDefined();
    expect(screen.getByRole("button", { name: "Registrar gasto em Mercado" })).toBeDefined();

    rerender(
      <ul>
        <EnvelopeCard
          envelope={{ ...owner, role: "PARTICIPANT" }}
          onRegisterExpense={() => undefined}
          onManageParticipants={() => undefined}
        />
      </ul>,
    );
    expect(screen.queryByRole("button", { name: "Mais ações de Mercado" })).toBeNull();
    expect(screen.getByRole("button", { name: "Registrar gasto em Mercado" })).toBeDefined();
    unmount();
  });

  it("enche a barra de um limite conforme o mês é gasto, e diz o restante em texto", () => {
    render(<ul><EnvelopeCard variant="verbas" envelope={{
      id: "limit", ownerId: "owner", name: "Combustível", purpose: "LIMIT",
      baseAmount: { amount: "400.00", currency: "BRL" }, targetAmount: null, targetReachedAt: null,
      available: { amount: "200.00", currency: "BRL" }, isNegative: false, role: "OWNER",
      wealthAllocation: false, createdAt: "2026-10-01T00:00:00Z", archivedAt: null, version: 0,
    }} /></ul>);

    expect(screen.getByRole("progressbar", { name: "Progresso de Combustível" }).getAttribute("value")).toBe("50");
    expect(screen.getByText(/R\$\s*200,00 restantes · 50% usado/)).toBeDefined();
    cleanup();

    // caso assimétrico: a barra acompanha o gasto, não o que sobra
    render(<ul><EnvelopeCard variant="verbas" envelope={{
      id: "limit", ownerId: "owner", name: "Combustível", purpose: "LIMIT",
      baseAmount: { amount: "400.00", currency: "BRL" }, targetAmount: null, targetReachedAt: null,
      available: { amount: "100.00", currency: "BRL" }, isNegative: false, role: "OWNER",
      wealthAllocation: false, createdAt: "2026-10-01T00:00:00Z", archivedAt: null, version: 0,
    }} /></ul>);

    expect(screen.getByRole("progressbar", { name: "Progresso de Combustível" }).getAttribute("value")).toBe("75");
    expect(screen.getByText(/R\$\s*100,00 restantes · 75% usado/)).toBeDefined();
    cleanup();
  });

  it("descreve um compromisso fixo na mesma direção da barra", () => {
    render(<ul><EnvelopeCard variant="verbas" envelope={{
      id: "fixed", ownerId: "owner", name: "Aluguel", purpose: "FIXED",
      baseAmount: { amount: "1200.00", currency: "BRL" }, targetAmount: null, targetReachedAt: null,
      available: { amount: "300.00", currency: "BRL" }, isNegative: false, role: "OWNER",
      wealthAllocation: false, createdAt: "2026-10-01T00:00:00Z", archivedAt: null, version: 0,
    }} /></ul>);

    expect(screen.getByRole("progressbar", { name: "Progresso de Aluguel" }).getAttribute("value")).toBe("75");
    expect(screen.getByText(/R\$\s*300,00 a pagar · 75% pago/)).toBeDefined();
    expect(screen.queryByText(/25%/)).toBeNull();
    cleanup();
  });

  it("dá uma ação primária ao dono e guarda Encerrar no ActionMenu; participante não vê nenhuma das duas", () => {
    const owner = {
      id: "limit", ownerId: "owner", name: "Combustível", purpose: "LIMIT" as const,
      baseAmount: { amount: "400.00", currency: "BRL" as const }, targetAmount: null, targetReachedAt: null,
      available: { amount: "200.00", currency: "BRL" as const }, isNegative: false, role: "OWNER" as const,
      wealthAllocation: false, createdAt: "2026-10-01T00:00:00Z", archivedAt: null, version: 0,
    };
    const { rerender, unmount } = render(
      <ul>
        <EnvelopeCard
          envelope={owner}
          onRegisterExpense={() => undefined}
          onManageParticipants={() => undefined}
          onArchive={() => undefined}
        />
      </ul>,
    );

    const primary = screen.getByRole("button", { name: "Registrar gasto em Combustível" });
    expect(primary.className).not.toMatch(/text/);
    expect(screen.getByRole("button", { name: "Mais ações de Combustível" })).toBeDefined();
    expect(screen.queryByRole("menu")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Mais ações de Combustível" }));
    expect(screen.getByRole("menu")).toBeDefined();
    expect(screen.getByRole("menuitem", { name: "Encerrar Combustível" })).toBeDefined();
    expect(screen.queryByRole("button", { name: "Arquivar Combustível" })).toBeNull();
    expect(cardSource).toMatch(/ActionMenu/);
    expect(cardSource).not.toMatch(/<details/);

    rerender(
      <ul>
        <EnvelopeCard
          envelope={{ ...owner, role: "PARTICIPANT" }}
          onRegisterExpense={() => undefined}
          onManageParticipants={() => undefined}
          onArchive={() => undefined}
        />
      </ul>,
    );

    expect(screen.getByRole("button", { name: "Registrar gasto em Combustível" })).toBeDefined();
    expect(screen.queryByRole("button", { name: "Mais ações de Combustível" })).toBeNull();
    expect(screen.queryByRole("menuitem", { name: "Encerrar Combustível" })).toBeNull();
    expect(screen.queryByRole("menuitem", { name: "Participantes de Combustível" })).toBeNull();
    unmount();
  });

  it("oferece a ação primária de aportar numa meta do dono", () => {
    render(<ul><EnvelopeCard envelope={{
      id: "goal", ownerId: "owner", name: "Viagem", purpose: "GOAL",
      baseAmount: { amount: "1000.00", currency: "BRL" }, targetAmount: null, targetReachedAt: null,
      available: { amount: "500.00", currency: "BRL" }, isNegative: false, role: "OWNER",
      wealthAllocation: false, createdAt: "2026-10-01T00:00:00Z", archivedAt: null, version: 0,
      goalProgress: { plannedAmount: { amount: "1000.00", currency: "BRL" }, contributedAmount: { amount: "500.00", currency: "BRL" }, remainingAmount: { amount: "500.00", currency: "BRL" }, percent: 50 },
    }} onRegisterExpense={() => undefined} /></ul>);

    const primary = screen.getByRole("button", { name: "Aportar em Viagem" });
    expect(primary.className).not.toMatch(/text/);
    expect(screen.getByRole("progressbar", { name: "Progresso de Viagem" }).getAttribute("value")).toBe("50");
    cleanup();
  });

  it("não usa shadow-lg decorativo no card; menu vem do ActionMenu com hairline", () => {
    expect(cardCss).not.toMatch(/shadow-lg/);
    expect(cardCss).not.toMatch(/\.moreActions/);
    expect(cardSource).toMatch(/ActionMenu/);
  });
});
