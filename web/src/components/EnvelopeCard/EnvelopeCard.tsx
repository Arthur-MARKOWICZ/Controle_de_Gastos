import { formatBRL } from "../../lib/money";
import dashboardStyles from "../Dashboard/Dashboard.module.css";
import verbasStyles from "../VerbasPage/Verbas.module.css";
import type { EnvelopeDTO } from "../../lib/api";

type Props = {
  envelope: EnvelopeDTO;
  onRegisterExpense?: (envelope: EnvelopeDTO) => void;
  onArchive?: (envelope: EnvelopeDTO) => void;
  variant?: "dashboard" | "verbas";
};

function purposeLabel(purpose: string) {
  switch (purpose) {
    case "LIMIT": return "Limite de gasto";
    case "GOAL": return "Meta de aporte";
    case "FIXED": return "Compromisso fixo";
    default: return purpose;
  }
}

export function EnvelopeCard({ envelope, onRegisterExpense, onArchive, variant = "dashboard" }: Props) {
  const base = formatBRL(envelope.baseAmount.amount);
  const available = formatBRL(envelope.available.amount);
  const baseNum = Number(envelope.baseAmount.amount);
  const availNum = Number(envelope.available.amount);
  // pct = remaining % (available/base). Clamped 0-100, negative forces 0 for bar but status shows alert
  const rawPct = baseNum > 0 ? (availNum / baseNum) * 100 : 0;
  const pct = baseNum > 0 ? Math.max(0, Math.min(100, Math.round(rawPct))) : 0;
  const spent = baseNum > 0 ? Math.max(0, Math.round(((baseNum - availNum) / baseNum) * 100)) : 0;

  let status: string;
  if (envelope.isNegative) {
    status = `⚠ Saldo negativo ${available}`;
  } else if (envelope.purpose === "LIMIT") {
    status = availNum === baseNum ? `${available} disponíveis` : `${available} restantes · ${spent}% usado`;
  } else if (envelope.purpose === "GOAL") {
    const falta = baseNum - availNum;
    const faltaFmt = falta > 0 ? formatBRL(falta.toFixed(2)) : "Meta atingida";
    status = envelope.role === "PARTICIPANT" ? `Compartilhada · ${pct}%` : falta > 0 ? `Falta ${faltaFmt} · ${pct}%` : `${pct}% · Meta atingida`;
  } else if (envelope.purpose === "FIXED") {
    status = envelope.isNegative ? `⚠ Descoberto ${available}` : `${available} reservados · ${pct}%`;
    if (envelope.role === "PARTICIPANT") status = `Compartilhada · ${status}`;
  } else {
    status = envelope.role === "PARTICIPANT" ? "Compartilhada · Participante" : `${available} disponíveis`;
  }

  const s = variant === "verbas" ? verbasStyles : dashboardStyles;

  return (
    <li>
      <div className={s.envelopeHeading}>
        <div>
          <h3>{envelope.name}</h3>
          <span>{purposeLabel(envelope.purpose)}{envelope.role === "PARTICIPANT" ? " · Participante" : ""}</span>
        </div>
        <div className={s.envelopeValue}>
          <strong>{available}</strong>
          <span>de {base}</span>
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
      <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.75rem", flexWrap: "wrap", alignItems: "center" }}>
        {envelope.role === "PARTICIPANT" && <span className={verbasStyles.badge + " " + verbasStyles.badgeShared}>Compartilhada</span>}
        {onRegisterExpense && (
          <button type="button" onClick={() => onRegisterExpense(envelope)} aria-label={`Registrar gasto em ${envelope.name}`} style={{ fontSize: "0.8125rem", color: "var(--accent)", fontWeight: 700, border: 0, background: "transparent", cursor: "pointer" }}>
            Registrar gasto
          </button>
        )}
        {onArchive && envelope.role === "OWNER" && (
          <button type="button" onClick={() => onArchive(envelope)} aria-label={`Arquivar ${envelope.name}`} style={{ fontSize: "0.8125rem", color: "var(--foreground-muted)", fontWeight: 600, border: 0, background: "transparent", cursor: "pointer" }}>
            Arquivar
          </button>
        )}
      </div>
    </li>
  );
}
