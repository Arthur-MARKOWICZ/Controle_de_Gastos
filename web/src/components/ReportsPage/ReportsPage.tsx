"use client";

import { FormEvent, useMemo, useState } from "react";
import { useAuth } from "../../auth/auth-context";
import { createApiClient, type ReportFormat, type ReportId } from "../../lib/api";
import { currentMonthSaoPaulo } from "../../lib/dates";
import { AppShell } from "../AppShell/AppShell";
import styles from "./ReportsPage.module.css";

type Props = { email: string; onLogout(): void };

const reports: Array<{ id: ReportId; title: string; description: string; monthly: boolean }> = [
  { id: "expenses-by-purpose", title: "Gastos por tipo", description: "Totais por tipo e uma linha para cada verba.", monthly: false },
  { id: "limit-exceeded-months", title: "Limites extrapolados", description: "Meses em que uma verba de limite fechou negativa.", monthly: true },
  { id: "goals-below-target", title: "Metas abaixo do esperado", description: "Metas de aporte com valor realizado abaixo do esperado.", monthly: true },
];

function initialDates() {
  const month = currentMonthSaoPaulo();
  const [year, value] = month.split("-").map(Number);
  return { from: `${month}-01`, to: `${month}-${String(new Date(Date.UTC(year, value, 0)).getUTCDate()).padStart(2, "0")}` };
}

function isWholeMonthRange(from: string, to: string) {
  if (!from || !to || from > to) return false;
  const [, , day] = from.split("-").map(Number);
  const [endYear, endMonth, endDay] = to.split("-").map(Number);
  return day === 1 && endDay === new Date(Date.UTC(endYear, endMonth, 0)).getUTCDate();
}

export function ReportsPage({ email, onLogout }: Props) {
  const { client } = useAuth();
  const initial = useMemo(() => initialDates(), []);
  const [type, setType] = useState<ReportId>("expenses-by-purpose");
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const [format, setFormat] = useState<ReportFormat>("xlsx");
  const [downloading, setDownloading] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);

  const selected = reports.find((report) => report.id === type)!;
  const invalidRange = !from || !to || from > to;
  const partialMonth = selected.monthly && !isWholeMonthRange(from, to);
  const canSubmit = !invalidRange && !partialMonth && !downloading;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!canSubmit) return;
    setDownloading(true); setFeedback(null);
    try {
      const file = await createApiClient(client).downloadReport(type, from, to, format);
      const url = URL.createObjectURL(file.blob);
      const link = document.createElement("a");
      link.href = url; link.download = file.filename; link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 0);
      setFeedback("Relatório gerado. O download deve começar em instantes.");
    } catch {
      setFeedback("Não foi possível gerar o relatório. Revise o período e tente novamente.");
    } finally {
      setDownloading(false);
    }
  }

  return <AppShell current="reports" email={email} onLogout={onLogout}>
    <div className={styles.page}>
      <header className={styles.header}>
        <p className={styles.eyebrow}>Exportação financeira</p>
        <h1>Relatórios</h1>
        <p>Escolha um período e baixe um arquivo CSV ou XLSX com as verbas que você pode visualizar.</p>
      </header>
      <form className={styles.form} onSubmit={submit}>
        <fieldset><legend>Tipo de relatório</legend>
          <label htmlFor="report-type">Tipo de relatório</label>
          <select id="report-type" value={type} onChange={(event) => setType(event.target.value as ReportId)}>
            {reports.map((report) => <option value={report.id} key={report.id}>{report.title}</option>)}
          </select>
          <p>{selected.description}</p>
        </fieldset>
        <div className={styles.dates}>
          <label htmlFor="report-from">Data inicial<input id="report-from" type="date" value={from} onChange={(event) => setFrom(event.target.value)} required /></label>
          <label htmlFor="report-to">Data final<input id="report-to" type="date" value={to} onChange={(event) => setTo(event.target.value)} required /></label>
        </div>
        <label htmlFor="report-format">Formato<select id="report-format" value={format} onChange={(event) => setFormat(event.target.value as ReportFormat)}><option value="xlsx">XLSX</option><option value="csv">CSV</option></select></label>
        {invalidRange && <p className={styles.error} role="alert">A data inicial deve ser anterior ou igual à data final.</p>}
        {partialMonth && <p className={styles.error} role="alert">Relatórios mensais exigem início no primeiro dia e término no último dia do mês.</p>}
        <button type="submit" disabled={!canSubmit}>{downloading ? "Gerando relatório…" : "Gerar relatório"}</button>
        {feedback && <p className={styles.feedback} role="status">{feedback}</p>}
      </form>
    </div>
  </AppShell>;
}
