"use client";

import { useMemo, useState } from "react";
import { useLedgerSummary } from "../../hooks/useLedgerSummary";
import { useAuth } from "../../auth/auth-context";
import { createApiClient, type EnvelopeDTO } from "../../lib/api";
import { formatBRLInputMask, maskToPlain } from "../../lib/money";
import { Dialog } from "../ui/Dialog";
import { ConfirmDialog } from "../ui/ConfirmDialog";
import { Notice } from "../ui/Notice";
import { Button } from "../ui/Button";
import { PageHeader } from "../ui/PageHeader";
import { EnvelopeForm } from "../EnvelopeForm/EnvelopeForm";
import { ExpenseForm, type ExpenseResult } from "../ExpenseForm/ExpenseForm";
import { EnvelopeCard } from "../EnvelopeCard/EnvelopeCard";
import { AppShell } from "../AppShell/AppShell";
import { ParticipantsPanel } from "../ParticipantsPanel/ParticipantsPanel";
import styles from "../VerbasPage/Verbas.module.css";

const ARCHIVE_MESSAGE =
  "A verba deixa de aparecer nos próximos meses. Os lançamentos já registrados continuam no histórico.";

export function MetasPage({ email, onLogout }: { email: string; onLogout(): void }) {
  const { client } = useAuth();
  const { summary, loading, busy, error, refresh } = useLedgerSummary();
  const api = useMemo(() => createApiClient(client), [client]);
  const [newGoal, setNewGoal] = useState(false);
  const [contribution, setContribution] = useState<EnvelopeDTO | null>(null);
  const [participantsTarget, setParticipantsTarget] = useState<EnvelopeDTO | null>(null);
  const [editingTarget, setEditingTarget] = useState<EnvelopeDTO | null>(null);
  const [pendingArchive, setPendingArchive] = useState<EnvelopeDTO | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const contributionKind = contribution?.role === "OWNER" ? "CONTRIBUTION" : "EXPENSE";
  const goals = useMemo(() => (summary?.envelopes ?? []).filter((envelope) => envelope.purpose === "GOAL"), [summary]);
  const targets = useMemo(() => (summary?.envelopes ?? []).filter((envelope) => envelope.purpose === "SAVINGS_TARGET"), [summary]);

  async function confirmArchive() {
    if (!pendingArchive) return;
    const envelope = pendingArchive;
    setPendingArchive(null);
    setFailure(null);
    try {
      await createApiClient(client).archiveEnvelope(envelope.id);
    } catch (e) {
      setFailure(e instanceof Error ? e.message : "Não foi possível encerrar a meta");
      return;
    }
    setFeedback(`Meta "${envelope.name}" encerrada.`);
    void refresh();
  }

  async function toggleWealthAllocation(envelope: EnvelopeDTO) {
    try {
      await createApiClient(client).updateEnvelope(envelope.id, {
        version: envelope.version,
        wealthAllocation: !envelope.wealthAllocation,
      });
      setFeedback(
        envelope.wealthAllocation
          ? `"${envelope.name}" voltou a contar como consumo`
          : `"${envelope.name}" marcada como alocação de patrimônio`,
      );
      void refresh();
    } catch {
      setFeedback("Não foi possível atualizar a classificação da meta");
    }
  }

  function contributionSaved({ targetJustReached }: ExpenseResult) {
    setContribution(null);
    setFeedback(
      targetJustReached
        ? "Parabéns! Você alcançou esta meta pela primeira vez."
        : contributionKind === "CONTRIBUTION"
          ? "Aporte registrado."
          : "Gasto registrado.",
    );
    void refresh();
  }

  return (
    <AppShell current="goals" email={email} onLogout={onLogout}>
      {feedback && (
        <Notice tone="success" onClose={() => setFeedback(null)}>
          {feedback}
        </Notice>
      )}
      {failure && (
        <Notice tone="danger" onClose={() => setFailure(null)}>
          {failure}
        </Notice>
      )}
      <PageHeader
        eyebrow="Objetivos financeiros"
        title="Metas"
        description="Metas mensais de aporte e reservas para objetivos específicos, sem misturar suas regras."
        actions={
          <Button
            onClick={() => {
              setFeedback(null);
              setNewGoal(true);
            }}
          >
            Nova meta
          </Button>
        }
      />
      {loading && (
        <div role="status" aria-busy="true" className={styles.loadingState}>
          <div className={styles.skeleton} />
          <div className={styles.skeleton} />
        </div>
      )}
      {error && (
        <Notice tone="danger">
          {error}{" "}
          <Button variant="text" onClick={() => void refresh()}>
            Tentar novamente
          </Button>
        </Notice>
      )}
      {!loading && !error && (
        <div className={styles.purposeGrid} aria-busy={busy || undefined}>
          <GoalSection
            title="Metas de aporte"
            rule="Faça aportes de pelo menos o valor planejado a cada mês, como investimentos."
            envelopes={goals}
            onContribute={(envelope) => {
              setFeedback(null);
              setContribution(envelope);
            }}
            onManageParticipants={(envelope) => {
              setFeedback(null);
              setParticipantsTarget(envelope);
            }}
            onArchive={(envelope) => {
              setFeedback(null);
              setPendingArchive(envelope);
            }}
            onToggleWealthAllocation={(envelope) => {
              setFeedback(null);
              void toggleWealthAllocation(envelope);
            }}
          />
          <GoalSection
            title="Metas de acumulação"
            rule="Junte qualquer valor até alcançar o alvo. O saldo acumulado não reinicia no próximo mês."
            envelopes={targets}
            onContribute={(envelope) => {
              setFeedback(null);
              setContribution(envelope);
            }}
            onManageParticipants={(envelope) => {
              setFeedback(null);
              setParticipantsTarget(envelope);
            }}
            onArchive={(envelope) => {
              setFeedback(null);
              setPendingArchive(envelope);
            }}
            onToggleWealthAllocation={(envelope) => {
              setFeedback(null);
              void toggleWealthAllocation(envelope);
            }}
            onEditTarget={setEditingTarget}
          />
        </div>
      )}
      <Dialog open={newGoal} onClose={() => setNewGoal(false)} title="Nova meta">
        <EnvelopeForm
          initialPurpose="SAVINGS_TARGET"
          allowedPurposes={["GOAL", "SAVINGS_TARGET"]}
          onCancel={() => setNewGoal(false)}
          onSuccess={() => {
            setNewGoal(false);
            void refresh();
          }}
        />
      </Dialog>
      <Dialog
        open={contribution !== null}
        onClose={() => setContribution(null)}
        title={
          contribution
            ? `${contributionKind === "CONTRIBUTION" ? "Aportar" : "Registrar gasto"} em ${contribution.name}`
            : "Registrar lançamento"
        }
      >
        {contribution && (
          <ExpenseForm
            envelope={contribution}
            kind={contributionKind}
            onCancel={() => setContribution(null)}
            onSuccess={contributionSaved}
          />
        )}
      </Dialog>
      <Dialog
        open={!!participantsTarget}
        onClose={() => setParticipantsTarget(null)}
        title={participantsTarget ? `Participantes de ${participantsTarget.name}` : "Participantes"}
      >
        {participantsTarget && (
          <ParticipantsPanel
            client={api}
            envelopeId={participantsTarget.id}
            envelopeName={participantsTarget.name}
            onClose={() => setParticipantsTarget(null)}
          />
        )}
      </Dialog>
      <Dialog
        open={editingTarget !== null}
        onClose={() => setEditingTarget(null)}
        title={editingTarget ? `Editar alvo de ${editingTarget.name}` : "Editar alvo"}
      >
        {editingTarget && (
          <TargetForm
            envelope={editingTarget}
            onCancel={() => setEditingTarget(null)}
            onSuccess={() => {
              setEditingTarget(null);
              setFeedback("Alvo atualizado.");
              void refresh();
            }}
          />
        )}
      </Dialog>
      <ConfirmDialog
        open={!!pendingArchive}
        title={pendingArchive ? `Encerrar ${pendingArchive.name}?` : "Encerrar meta?"}
        message={ARCHIVE_MESSAGE}
        confirmLabel="Encerrar"
        onConfirm={() => void confirmArchive()}
        onCancel={() => setPendingArchive(null)}
      />
    </AppShell>
  );
}

function GoalSection({
  title,
  rule,
  envelopes,
  onContribute,
  onManageParticipants,
  onArchive,
  onToggleWealthAllocation,
  onEditTarget,
}: {
  title: string;
  rule: string;
  envelopes: EnvelopeDTO[];
  onContribute(envelope: EnvelopeDTO): void;
  onManageParticipants(envelope: EnvelopeDTO): void;
  onArchive(envelope: EnvelopeDTO): void;
  onToggleWealthAllocation(envelope: EnvelopeDTO): void;
  onEditTarget?: (envelope: EnvelopeDTO) => void;
}) {
  return (
    <section className={styles.purposeSection} aria-label={title}>
      <div className={styles.sectionHeader}>
        <h2>{title}</h2>
        <p>{rule}</p>
      </div>
      {envelopes.length === 0 ? (
        <p className={styles.emptyState} role="status">
          Nenhuma meta criada ainda.
        </p>
      ) : (
        <ul className={styles.envelopeList}>
          {envelopes.map((envelope) => (
            <EnvelopeCard
              key={envelope.id}
              envelope={envelope}
              onRegisterExpense={onContribute}
              onManageParticipants={onManageParticipants}
              onArchive={onArchive}
              onToggleWealthAllocation={onToggleWealthAllocation}
              onEditTarget={onEditTarget}
              variant="verbas"
            />
          ))}
        </ul>
      )}
    </section>
  );
}

function TargetForm({
  envelope,
  onCancel,
  onSuccess,
}: {
  envelope: EnvelopeDTO;
  onCancel(): void;
  onSuccess(): void;
}) {
  const { client } = useAuth();
  const [amount, setAmount] = useState(formatBRLInputMask(envelope.targetAmount?.amount ?? ""));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const plain = maskToPlain(amount);
    if (!/^\d{1,17}\.\d{2}$/.test(plain) || plain === "0.00") {
      setError("Informe um alvo maior que zero.");
      return;
    }
    setBusy(true);
    try {
      await createApiClient(client).updateEnvelope(envelope.id, {
        version: envelope.version,
        targetAmount: { amount: plain, currency: "BRL" },
      });
      onSuccess();
    } catch {
      setError("Não foi possível atualizar o alvo.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <form className={styles.filtersBar} onSubmit={submit}>
      {error && <Notice tone="danger">{error}</Notice>}
      <label htmlFor="target-amount">Valor total da meta (BRL)</label>
      <input
        id="target-amount"
        inputMode="decimal"
        value={amount}
        onChange={(event) => setAmount(formatBRLInputMask(event.target.value))}
        required
      />
      <div>
        <Button variant="secondary" onClick={onCancel} disabled={busy}>
          Cancelar
        </Button>
        <Button type="submit" busy={busy}>
          {busy ? "Salvando…" : "Salvar alvo"}
        </Button>
      </div>
    </form>
  );
}
