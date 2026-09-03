import { formatBRL } from "../../lib/money";
import dashboardStyles from "../Dashboard/Dashboard.module.css";
import verbasStyles from "../VerbasPage/Verbas.module.css";
import type { EnvelopeDTO } from "../../lib/api";

type Props = {
  envelope: EnvelopeDTO;
  onRegisterExpense?: (envelope: EnvelopeDTO) => void;
  onArchive?: (envelope: EnvelopeDTO) => void;
  onEditTarget?: (envelope: EnvelopeDTO) => void;
  onEditAnnual?: (envelope: EnvelopeDTO) => void;
  variant?: "dashboard" | "verbas";
};

function purposeLabel(purpose: string) {
  switch (purpose) {
    case "LIMIT": return "Limite de gasto";
    case "GOAL": return "Meta de aporte";
    case "FIXED": return "Compromisso fixo";
    case "SAVINGS_TARGET": return "Meta de acumulação";
    case "ANNUAL_EXPENSE": return "Gasto anual";
    default: return purpose;
  }
}

function purposeRule(purpose: string) {
  switch (purpose) {
    case "LIMIT": return "Planeje até este valor por mês; o saldo continua disponível.";
    case "FIXED": return "Reserve este valor mensal e registre o pagamento.";
    case "GOAL": return "Faça aportes mensais para avançar nesta meta.";
    case "SAVINGS_TARGET": return "Junte qualquer valor; o saldo não reinicia no próximo mês.";
    case "ANNUAL_EXPENSE": return "Reserve até o vencimento anual e registre o pagamento real.";
    default: return "";
  }
}

export function EnvelopeCard({ envelope, onRegisterExpense, onArchive, onEditTarget, onEditAnnual, variant = "dashboard" }: Props) {
  const goalProgress = envelope.purpose === "GOAL" ? envelope.goalProgress : null;
  const progressAmount = goalProgress?.plannedAmount ?? (envelope.purpose === "SAVINGS_TARGET" ? envelope.targetAmount ?? envelope.baseAmount
    : envelope.purpose === "ANNUAL_EXPENSE" ? envelope.annualExpense?.annualAmount ?? envelope.baseAmount : envelope.baseAmount);
  const base = formatBRL(progressAmount.amount);
  const financialAvailable = formatBRL(envelope.available.amount);
  const available = formatBRL(goalProgress?.remainingAmount.amount ?? envelope.available.amount);
  const baseNum = Number(progressAmount.amount);
  const availNum = Number(goalProgress?.remainingAmount.amount ?? envelope.available.amount);
  // pct = remaining % (available/base). Clamped 0-100, negative forces 0 for bar but status shows alert
  const rawPct = baseNum > 0 ? (availNum / baseNum) * 100 : 0;
  const pct = goalProgress?.percent ?? (baseNum > 0 ? Math.max(0, Math.min(100, Math.round(rawPct))) : 0);
  const spent = baseNum > 0 ? Math.max(0, Math.round(((baseNum - availNum) / baseNum) * 100)) : 0;

  let status: string;
  if (envelope.isNegative) {
    status = `⚠ Saldo negativo ${financialAvailable}`;
  } else if (envelope.purpose === "LIMIT") {
    status = availNum === baseNum ? `${available} disponíveis` : `${available} restantes · ${spent}% usado`;
  } else if (envelope.purpose === "GOAL") {
    const contributed = goalProgress ? formatBRL(goalProgress.contributedAmount.amount) : null;
    status = envelope.role === "PARTICIPANT" ? `Compartilhada · ${pct}%` : availNum > 0
      ? `Faltam ${available} · ${pct}% concluído${contributed ? ` · ${contributed} aportados` : ""}`
      : `${pct}% · Meta atingida`;
  } else if (envelope.purpose === "FIXED") {
    status = envelope.isNegative ? `⚠ Descoberto ${available}` : `${available} reservados · ${pct}%`;
    if (envelope.role === "PARTICIPANT") status = `Compartilhada · ${status}`;
  } else if (envelope.purpose === "SAVINGS_TARGET") {
    const remaining = Math.max(0, baseNum - availNum);
    status = availNum >= baseNum
      ? "Meta alcançada · continue aportando ou encerre quando quiser"
      : `${pct}% concluído · faltam ${formatBRL(remaining.toFixed(2))}`;
  } else if (envelope.purpose === "ANNUAL_EXPENSE") {
    const annual = envelope.annualExpense;
    const due = annual ? `${String(annual.dueDay).padStart(2, "0")}/${String(annual.dueMonth).padStart(2, "0")}` : "—";
    status = annual?.fundingMode === "ONE_TIME"
      ? `Pagamento único em ${due} · registre o gasto quando ocorrer`
      : `${pct}% reservado · vencimento em ${due}`;
  } else {
    status = envelope.role === "PARTICIPANT" ? "Compartilhada · Participante" : `${available} disponíveis`;
  }

  const s = variant === "verbas" ? verbasStyles : dashboardStyles;
  const registerActionLabel = envelope.purpose === "SAVINGS_TARGET" || envelope.purpose === "GOAL"
    ? "Aportar"
    : "Registrar gasto";

  return (
    <li>
      <div className={s.envelopeHeading}>
        <div>
          <h3>{envelope.name}</h3>
          <span>{purposeLabel(envelope.purpose)}{envelope.role === "PARTICIPANT" ? " · Participante" : ""}</span>
          <span>{purposeRule(envelope.purpose)}</span>
        </div>
        <div className={s.envelopeValue}>
          <strong>{envelope.purpose === "GOAL" && goalProgress ? `Faltam ${available}` : available}</strong>
          <span>{envelope.purpose === "GOAL" && goalProgress ? `meta acumulada ${base}` : `de ${base}`}</span>
        </div>
      </div>
      <progress
        className={s.progressTrack}
        data-purpose={envelope.purpose}
        data-negative={envelope.isNegative ? "true" : "false"}
        aria-label={`Progresso de ${envelope.name}`}
        value={pct}
        max={100}
      />
      <p className={s.statusText} role={envelope.isNegative ? "alert" : undefined}>{status}</p>
      <div className={s.cardActions}>
        {envelope.role === "PARTICIPANT" && <span className={verbasStyles.badge + " " + verbasStyles.badgeShared}>Compartilhada</span>}
        {onRegisterExpense && (
          <button type="button" onClick={() => onRegisterExpense(envelope)} aria-label={`${registerActionLabel} em ${envelope.name}`}>
            {registerActionLabel}
          </button>
        )}
        {onArchive && envelope.role === "OWNER" && (
          <button type="button" onClick={() => onArchive(envelope)} aria-label={`Arquivar ${envelope.name}`}>
            Arquivar
          </button>
        )}
        {onEditTarget && envelope.purpose === "SAVINGS_TARGET" && envelope.role === "OWNER" && (
          <button type="button" onClick={() => onEditTarget(envelope)} aria-label={`Editar alvo de ${envelope.name}`}>
            Editar alvo
          </button>
        )}
        {onEditAnnual && envelope.purpose === "ANNUAL_EXPENSE" && envelope.role === "OWNER" && (
          <button type="button" onClick={() => onEditAnnual(envelope)} aria-label={`Editar gasto anual ${envelope.name}`}>Editar</button>
        )}
      </div>
    </li>
  );
}
