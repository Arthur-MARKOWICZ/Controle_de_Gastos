"use client";

import { useCallback, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams, useRouter } from "next/navigation";
import styles from "./Verbas.module.css";
import { useEnvelopes } from "../../hooks/useEnvelopes";
import { useLedgerSummary } from "../../hooks/useLedgerSummary";
import { currentMonthSaoPaulo, formatMonthLabel, parseMonthParam } from "../../lib/dates";
import { formatBRL } from "../../lib/money";
import { BalanceSummary } from "../BalanceSummary/BalanceSummary";
import { Dialog } from "../ui/Dialog";
import { ConfirmDialog } from "../ui/ConfirmDialog";
import { Button } from "../ui/Button";
import { Notice } from "../ui/Notice";
import { PageHeader } from "../ui/PageHeader";
import { EnvelopeForm } from "../EnvelopeForm/EnvelopeForm";
import { ExpenseForm, overdraftNotice, type ExpenseResult } from "../ExpenseForm/ExpenseForm";
import { Glossary } from "../Glossary/Glossary";
import { PurposeSection } from "./PurposeSection";
import { VerbasFilters } from "./VerbasFilters";
import type { EnvelopeDTO } from "../../lib/api";
import { createApiClient } from "../../lib/api";
import { useAuth } from "../../auth/auth-context";
import { AppShell } from "../AppShell/AppShell";

type PurposeFilter = "ALL" | "LIMIT" | "FIXED";
type SortKey = "progress" | "saldo" | "nome";

const ARCHIVE_MESSAGE =
  "A verba deixa de aparecer nos próximos meses. Os lançamentos já registrados continuam no histórico.";

export function VerbasPage({ email, onLogout }: { email: string; onLogout(): void }) {
  const searchParams = useSearchParams();
  const router = useRouter();
  const { client } = useAuth();

  const rawMonth = parseMonthParam(searchParams.get("month"));
  const month = rawMonth ?? currentMonthSaoPaulo();
  const monthLabel = formatMonthLabel(month);

  const {
    envelopes,
    loading: envelopesLoading,
    busy: envelopesBusy,
    error: envelopesError,
    refresh: refreshEnvelopes,
  } = useEnvelopes(month);
  const {
    summary,
    loading: summaryLoading,
    busy: summaryBusy,
    error: summaryError,
    refresh: refreshSummary,
  } = useLedgerSummary(month);

  const [purposeFilter, setPurposeFilter] = useState<PurposeFilter>("ALL");
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortKey>("progress");
  const [showNewEnvelope, setShowNewEnvelope] = useState<null | "LIMIT" | "FIXED">(null);
  const [showGlossary, setShowGlossary] = useState(false);
  const [expenseTarget, setExpenseTarget] = useState<EnvelopeDTO | null>(null);
  const [pendingArchive, setPendingArchive] = useState<EnvelopeDTO | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  const loading = envelopesLoading || summaryLoading;
  const busy = envelopesBusy || summaryBusy;
  const error = envelopesError || summaryError;

  const source = (summary?.envelopes ?? envelopes).filter((envelope) => envelope.purpose === "LIMIT" || envelope.purpose === "FIXED");

  const counts = useMemo(() => {
    const c: Record<PurposeFilter, number> = { ALL: source.length, LIMIT: 0, FIXED: 0 };
    for (const e of source) c[e.purpose as PurposeFilter] = (c[e.purpose as PurposeFilter] ?? 0) + 1;
    return c;
  }, [source]);

  const negatives = useMemo(() => source.filter((e) => e.isNegative), [source]);

  const filteredSorted = useCallback((list: EnvelopeDTO[]) => {
    let out = [...list];
    if (query.trim()) {
      const q = query.trim().toLowerCase();
      out = out.filter((e) => e.name.toLowerCase().includes(q));
    }
    if (sort === "nome") out.sort((a, b) => a.name.localeCompare(b.name, "pt-BR"));
    else if (sort === "saldo") out.sort((a, b) => Number(b.available.amount) - Number(a.available.amount));
    else {
      out.sort((a, b) => {
        const pa = Number(a.baseAmount.amount) > 0 ? Number(a.available.amount) / Number(a.baseAmount.amount) : 1;
        const pb = Number(b.baseAmount.amount) > 0 ? Number(b.available.amount) / Number(b.baseAmount.amount) : 1;
        return pa - pb;
      });
    }
    return out;
  }, [query, sort]);

  const byPurpose = useMemo(() => {
    const map: Record<string, EnvelopeDTO[]> = { LIMIT: [], FIXED: [] };
    for (const e of filteredSorted(source)) map[e.purpose]?.push(e);
    return map as Record<"LIMIT" | "FIXED", EnvelopeDTO[]>;
  }, [source, filteredSorted]);

  const visiblePurposes: ("LIMIT" | "FIXED")[] =
    purposeFilter === "ALL" ? ["LIMIT", "FIXED"] : [purposeFilter];

  const distribution = useMemo(() => {
    const totals: Record<string, number> = { LIMIT: 0, FIXED: 0 };
    for (const e of source) totals[e.purpose] += Number(e.baseAmount.amount);
    const total = totals.LIMIT + totals.FIXED;
    return { totals, total };
  }, [source]);

  function onMonthChange(e: React.ChangeEvent<HTMLInputElement>) {
    const v = e.target.value;
    const url = v ? `/verbas?month=${v}` : "/verbas";
    router.push(url);
  }

  async function confirmArchive() {
    if (!pendingArchive) return;
    const envelope = pendingArchive;
    setPendingArchive(null);
    try {
      await createApiClient(client).archiveEnvelope(envelope.id);
      setFeedback(`Verba "${envelope.name}" encerrada`);
      void refreshEnvelopes();
      void refreshSummary();
    } catch {
      setFeedback("Não foi possível encerrar a verba");
    }
  }

  function handleCreateSuccess() {
    setShowNewEnvelope(null);
    setFeedback("Verba criada com sucesso");
    void refreshEnvelopes();
    void refreshSummary();
  }

  function handleExpenseSuccess(result: ExpenseResult) {
    const name = expenseTarget?.name ?? "verba";
    setExpenseTarget(null);
    setFeedback(`Gasto registrado em "${name}"${overdraftNotice(result)}`);
    void refreshEnvelopes();
    void refreshSummary();
  }

  return (
    <AppShell current="envelopes" email={email} onLogout={onLogout}>
        {feedback && (
          <Notice tone="success" onClose={() => setFeedback(null)}>{feedback}</Notice>
        )}

        <PageHeader
          eyebrow={monthLabel}
          title="Verbas"
          description="Organize limites e compromissos mensais. Metas de aporte e acumulação ficam na área Metas."
          actions={<Button onClick={() => { setFeedback(null); setShowNewEnvelope("LIMIT"); }}>Nova verba</Button>}
        >
          <div className={styles.monthControl}>
            <label htmlFor="month-picker">Mês</label>
            <input id="month-picker" type="month" value={month} onChange={onMonthChange} />
            <span>
              {summary?.income ? `Renda ${formatBRL(summary.income.amount)}` : "Renda não configurada"}
            </span>
          </div>
        </PageHeader>

        {loading && (
          <div role="status" aria-busy="true" className={styles.loadingState}>
            <div className={styles.skeleton} />
            <div className={styles.skeleton} />
          </div>
        )}
        {error && (
          <Notice tone="danger">
            {error}{" "}
            <Button
              variant="text"
              onClick={() => {
                void refreshEnvelopes();
                void refreshSummary();
              }}
            >
              Tentar novamente
            </Button>
          </Notice>
        )}

        {!loading && !error && (
          <div aria-busy={busy || undefined}>
            <BalanceSummary summary={summary} monthLabel={monthLabel} />

            {negatives.length > 0 && (
              <div className={styles.alertStrip} role="alert" aria-live="assertive">
                <span>
                  <strong>{negatives.length} verba(s) negativas:</strong> {negatives.map((n) => n.name).join(", ")} — saldo negativo gera alerta, não bloqueio. Registre um aporte ou ajuste o gasto.
                </span>
              </div>
            )}

            <VerbasFilters
              active={purposeFilter}
              onChangePurpose={setPurposeFilter}
              counts={counts}
              query={query}
              onQuery={setQuery}
              sort={sort}
              onSort={setSort}
            />

            <div className={styles.distribution} aria-label="Distribuição por natureza">
              {(["LIMIT", "FIXED"] as const).map((p) => {
                const val = distribution.totals[p];
                const pct = distribution.total > 0 ? Math.round((val / distribution.total) * 100) : 0;
                const label = p === "LIMIT" ? "Limite de gasto" : "Compromisso fixo";
                return (
                  <div key={p} className={styles.distributionCard}>
                    <h3>{label}</h3>
                    <strong>{val === 0 ? "—" : formatBRL(val.toFixed(2))}</strong> <span>{pct}% da alocação</span>
                    <div className={styles.distributionBar} aria-hidden="true">
                      <i data-purpose={p} style={{ width: `${pct}%` }} />
                    </div>
                    <span>{counts[p]} verba(s)</span>
                  </div>
                );
              })}
            </div>

            <div className={styles.purposeGrid}>
              {visiblePurposes.map((p) => (
                <PurposeSection
                  key={p}
                  purpose={p}
                  envelopes={byPurpose[p]}
                  onRegisterExpense={(target) => { setFeedback(null); setExpenseTarget(target); }}
                  onArchive={(target) => { setFeedback(null); setPendingArchive(target); }}
                  onCreate={(purpose) => { setFeedback(null); setShowNewEnvelope(purpose); }}
                />
              ))}
            </div>

            {summary && (
              <div className={styles.unallocatedCard} data-negative={Number(summary.unallocated.amount) < 0 ? "true" : "false"}>
                <div>
                  <strong>Não alocado: {formatBRL(summary.unallocated.amount)}</strong>
                  <p>
                    Dinheiro da renda que ainda não foi para nenhuma verba. Acumula para o próximo mês se não usado.
                  </p>
                </div>
                <span>
                  Uso {Math.round(summary.usagePct)}% · Alocado {formatBRL(summary.allocated.amount)}
                </span>
              </div>
            )}

            <div className={styles.footerBar}>
              <span>
                <strong>Dica:</strong> toque em <em>Registrar gasto</em> no card da verba — saldo negativo não impede o lançamento, apenas alerta.
              </span>
              <Link href="/">
                Voltar à visão geral
              </Link>
            </div>
          </div>
        )}

        <Dialog open={!!showNewEnvelope} onClose={() => setShowNewEnvelope(null)} title="Nova verba">
          <EnvelopeForm
            initialPurpose={showNewEnvelope ?? "LIMIT"}
            allowedPurposes={["LIMIT", "FIXED"]}
            onOpenGlossary={() => {
              setShowNewEnvelope(null);
              setShowGlossary(true);
            }}
            onSuccess={handleCreateSuccess}
            onCancel={() => setShowNewEnvelope(null)}
          />
        </Dialog>

        <Dialog open={showGlossary} onClose={() => setShowGlossary(false)} title="Glossário de verbas">
          <Glossary />
        </Dialog>

        <Dialog open={!!expenseTarget} onClose={() => setExpenseTarget(null)} title={expenseTarget ? `Registrar gasto em ${expenseTarget.name}` : "Registrar gasto"}>
          {expenseTarget && <ExpenseForm envelope={expenseTarget} onSuccess={handleExpenseSuccess} onCancel={() => setExpenseTarget(null)} />}
        </Dialog>

        <ConfirmDialog
          open={!!pendingArchive}
          title={pendingArchive ? `Encerrar ${pendingArchive.name}?` : "Encerrar verba?"}
          message={ARCHIVE_MESSAGE}
          confirmLabel="Encerrar"
          onConfirm={() => void confirmArchive()}
          onCancel={() => setPendingArchive(null)}
        />
    </AppShell>
  );
}
