import { readFileSync } from "node:fs";
import { join } from "node:path";
import { cleanup, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EnvelopeCard } from "./EnvelopeCard";

const cardCss = readFileSync(join(process.cwd(), "src/components/EnvelopeCard/EnvelopeCard.module.css"), "utf8");

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

  it("explica que limite de gasto reinicia a cada mês", () => {
    render(<ul><EnvelopeCard variant="verbas" envelope={{
      id: "limit", ownerId: "owner", name: "Mercado", purpose: "LIMIT",
      baseAmount: { amount: "100.00", currency: "BRL" }, targetAmount: null, targetReachedAt: null,
      available: { amount: "100.00", currency: "BRL" }, isNegative: false, role: "OWNER",
      wealthAllocation: false, createdAt: "2026-10-01T00:00:00Z", archivedAt: null, version: 0,
    }} /></ul>);

    expect(screen.getByText("Limite de gasto")).toBeDefined();
    expect(screen.getByText(/saldo reinicia no mês seguinte/)).toBeDefined();
  });

  it("explica a regra e mostra o progresso de uma meta de acumulação", () => {
    render(<ul><EnvelopeCard variant="verbas" envelope={{
      id: "target", ownerId: "owner", name: "Notebook", purpose: "SAVINGS_TARGET",
      baseAmount: { amount: "0.00", currency: "BRL" }, targetAmount: { amount: "1000.00", currency: "BRL" }, targetReachedAt: null,
      available: { amount: "100.00", currency: "BRL" }, isNegative: false, role: "OWNER", wealthAllocation: false, createdAt: "2026-09-01T00:00:00Z", archivedAt: null, version: 0,
    }} /></ul>);

    expect(screen.getByText("Meta de acumulação")).toBeDefined();
    expect(screen.getByText(/saldo não reinicia/)).toBeDefined();
    expect(screen.getByText(/10% concluído/)).toBeDefined();
    expect(screen.getByRole("progressbar", { name: "Progresso de Notebook" }).getAttribute("value")).toBe("10");
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
    expect(screen.getByRole("button", { name: "Participantes de Mercado" })).toBeDefined();
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
    expect(screen.queryByRole("button", { name: "Participantes de Mercado" })).toBeNull();
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

  it("dá uma ação primária ao dono e guarda Encerrar em Mais; participante não vê nenhuma das duas", () => {
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
    expect(screen.getByText("Mais")).toBeDefined();
    expect(screen.getByRole("button", { name: "Encerrar Combustível" })).toBeDefined();
    expect(screen.queryByRole("button", { name: "Arquivar Combustível" })).toBeNull();

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
    expect(screen.queryByRole("button", { name: "Encerrar Combustível" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Participantes de Combustível" })).toBeNull();
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

  it("abre o menu Mais abaixo do botão e para cima no último card, para não esconder as opções", () => {
    expect(cardCss).toMatch(/\.moreActions\s*\{[^}]*top:\s*100%/);
    expect(cardCss).toMatch(/\.card:last-child\s+\.moreActions\s*\{[^}]*bottom:\s*100%/);
    expect(cardCss).toMatch(/\.more\[open\]\s*\{[^}]*z-index:\s*[1-9]/);
  });
});
