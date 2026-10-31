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
import styles from "../VerbasPage/Verbas.module.css";

const ARCHIVE_MESSAGE =
  "A verba deixa de aparecer nos próximos meses. Os lançamentos já registrados continuam no histórico.";

export function MetasPage({ email, onLogout }: { email: string; onLogout(): void }) {
  const { client } = useAuth();
  const { summary, loading, busy, error, refresh } = useLedgerSummary();
  const [newGoal, setNewGoal] = useState(false);
  const [contribution, setContribution] = useState<EnvelopeDTO | null>(null);
  const [editingTarget, setEditingTarget] = useState<EnvelopeDTO | null>(null);
  const [pendingArchive, setPendingArchive] = useState<EnvelopeDTO | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const goals = useMemo(() => (summary?.envelopes ?? []).filter((envelope) => envelope.purpose === "GOAL"), [summary]);
  const targets = useMemo(() => (summary?.envelopes ?? []).filter((envelope) => envelope.purpose === "SAVINGS_TARGET"), [summary]);

  async function confirmArchive() {
    if (!pendingArchive) return;
    const envelope = pendingArchive;
    setPendingArchive(null);
    await createApiClient(client).archiveEnvelope(envelope.id);
    setFeedback(`Meta "${envelope.name}" encerrada.`);
    void refresh();
  }

  function contributionSaved({ targetJustReached }: ExpenseResult) {
    setContribution(null);
    setFeedback(targetJustReached ? "Parabéns! Você alcançou esta meta pela primeira vez." : "Aporte registrado.");
    void refresh();
  }

  return (
    <AppShell current="goals" email={email} onLogout={onLogout}>
      {feedback && (
        <Notice tone="success" onClose={() => setFeedback(null)}>
          {feedback}
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
            onArchive={(envelope) => {
              setFeedback(null);
              setPendingArchive(envelope);
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
            onArchive={(envelope) => {
              setFeedback(null);
              setPendingArchive(envelope);
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
        title={contribution ? `Aportar em ${contribution.name}` : "Registrar aporte"}
      >
        {contribution && (
          <ExpenseForm
            envelope={contribution}
            kind="CONTRIBUTION"
            onCancel={() => setContribution(null)}
            onSuccess={contributionSaved}
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
  onArchive,
  onEditTarget,
}: {
  title: string;
  rule: string;
  envelopes: EnvelopeDTO[];
  onContribute(envelope: EnvelopeDTO): void;
  onArchive(envelope: EnvelopeDTO): void;
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
              onArchive={onArchive}
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
