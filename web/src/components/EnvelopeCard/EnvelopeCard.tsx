import { formatBRL } from "../../lib/money";
import type { EnvelopeDTO, LimitSpendingPaceRowDTO } from "../../lib/api";
import { spendingPaceLabel } from "../../lib/spendingPace";
import { ActionMenu } from "../ui/ActionMenu";
import type { ActionMenuItem } from "../ui/ActionMenu";
import { Button } from "../ui/Button";
import { ProgressBar } from "../ui/ProgressBar";
import { ProgressRing } from "../ui/ProgressRing";
import styles from "./EnvelopeCard.module.css";

type Props = {
  envelope: EnvelopeDTO;
  pace?: LimitSpendingPaceRowDTO | null;
  onRegisterExpense?: (envelope: EnvelopeDTO) => void;
  onManageParticipants?: (envelope: EnvelopeDTO) => void;
  onManageRecurring?: (envelope: EnvelopeDTO) => void;
  onArchive?: (envelope: EnvelopeDTO) => void;
  onEditTarget?: (envelope: EnvelopeDTO) => void;
  onEditAnnual?: (envelope: EnvelopeDTO) => void;
  onToggleWealthAllocation?: (envelope: EnvelopeDTO) => void;
  /** Mantido por compatibilidade; o estilo do card é único. */
  variant?: "dashboard" | "verbas";
};

export function isContributionPurpose(purpose: string) {
  return purpose === "GOAL" || purpose === "SAVINGS_TARGET";
}

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

export function EnvelopeCard({
  envelope,
  pace,
  onRegisterExpense,
  onManageParticipants,
  onManageRecurring,
  onArchive,
  onEditTarget,
  onEditAnnual,
  onToggleWealthAllocation,
}: Props) {
  const goalProgress = envelope.purpose === "GOAL" ? envelope.goalProgress : null;
  const progressAmount = goalProgress?.plannedAmount ?? (envelope.purpose === "SAVINGS_TARGET" ? envelope.targetAmount ?? envelope.baseAmount
    : envelope.purpose === "ANNUAL_EXPENSE" ? envelope.annualExpense?.annualAmount ?? envelope.baseAmount : envelope.baseAmount);
  const base = formatBRL(progressAmount.amount);
  const financialAvailable = formatBRL(envelope.available.amount);
  const available = formatBRL(goalProgress?.remainingAmount.amount ?? envelope.available.amount);
  const baseNum = Number(progressAmount.amount);
  const availNum = Number(goalProgress?.remainingAmount.amount ?? envelope.available.amount);
  const rawPct = baseNum > 0 ? (availNum / baseNum) * 100 : 0;
  const pct = goalProgress?.percent ?? (baseNum > 0 ? Math.max(0, Math.min(100, Math.round(rawPct))) : 0);
  const spent = baseNum > 0 ? Math.max(0, Math.min(100, Math.round(((baseNum - availNum) / baseNum) * 100))) : 0;
  /** LIMIT e FIXED enchem conforme o mês é consumido; as demais naturezas, conforme o alvo avança. */
  const consumesPeriod = envelope.purpose === "LIMIT" || envelope.purpose === "FIXED";
  const progressValue = consumesPeriod ? spent : pct;
  const ringValue = envelope.isNegative ? 100 : Math.max(0, Math.min(100, progressValue));

  let status: string;
  if (envelope.isNegative) {
    status = `Saldo negativo ${financialAvailable}`;
  } else if (envelope.purpose === "LIMIT") {
    status = availNum === baseNum ? `${available} disponíveis` : `${available} restantes · ${spent}% usado`;
    if (pace) {
      status = `${status} · ${spendingPaceLabel(pace)}`;
    }
  } else if (envelope.purpose === "GOAL") {
    const contributed = goalProgress ? formatBRL(goalProgress.contributedAmount.amount) : null;
    status = envelope.role === "PARTICIPANT" ? `Compartilhada · ${pct}%` : availNum > 0
      ? `Faltam ${available} · ${pct}% concluído${contributed ? ` · ${contributed} aportados` : ""}`
      : `${pct}% · Meta atingida`;
  } else if (envelope.purpose === "FIXED") {
    status = envelope.isNegative ? `Descoberto ${available}` : `${available} a pagar · ${spent}% pago`;
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

  const registerActionLabel =
    envelope.role === "OWNER" && isContributionPurpose(envelope.purpose) ? "Aportar" : "Registrar gasto";

  const isOwner = envelope.role === "OWNER";
  const menuItems: ActionMenuItem[] = [];
  if (onManageParticipants && isOwner) {
    menuItems.push({
      key: "participants",
      label: "Participantes",
      ariaLabel: `Participantes de ${envelope.name}`,
      onClick: () => onManageParticipants(envelope),
    });
  }
  if (onManageRecurring && isOwner) {
    menuItems.push({
      key: "recurring",
      label: "Recorrências",
      ariaLabel: `Recorrências de ${envelope.name}`,
      onClick: () => onManageRecurring(envelope),
    });
  }
  if (onEditTarget && envelope.purpose === "SAVINGS_TARGET" && isOwner) {
    menuItems.push({
      key: "target",
      label: "Editar alvo",
      ariaLabel: `Editar alvo de ${envelope.name}`,
      onClick: () => onEditTarget(envelope),
    });
  }
  if (onEditAnnual && envelope.purpose === "ANNUAL_EXPENSE" && isOwner) {
    menuItems.push({
      key: "annual",
      label: "Editar",
      ariaLabel: `Editar gasto anual ${envelope.name}`,
      onClick: () => onEditAnnual(envelope),
    });
  }
  if (onToggleWealthAllocation && isOwner) {
    menuItems.push({
      key: "wealth",
      label: envelope.wealthAllocation ? "Tratar como consumo" : "Marcar como patrimônio",
      ariaLabel: envelope.wealthAllocation
        ? `Tratar ${envelope.name} como consumo`
        : `Marcar ${envelope.name} como alocação de patrimônio`,
      onClick: () => onToggleWealthAllocation(envelope),
    });
  }
  if (onArchive && isOwner) {
    menuItems.push({
      key: "archive",
      label: "Encerrar",
      ariaLabel: `Encerrar ${envelope.name}`,
      danger: true,
      onClick: () => onArchive(envelope),
    });
  }

  return (
    <li className={styles.card} data-negative={envelope.isNegative ? "true" : undefined}>
      <div className={styles.cardTop}>
        <div
          className={styles.ringWrap}
          data-negative={envelope.isNegative ? "true" : undefined}
          aria-hidden="true"
        >
          <ProgressRing
            value={ringValue}
            purpose={envelope.purpose}
            negative={envelope.isNegative}
            size={36}
          />
          <span className={styles.ringLabel}>{envelope.isNegative ? "!" : `${ringValue}%`}</span>
        </div>

        <div className={styles.envelopeHeading}>
          <div className={styles.titleRow}>
            <h3>{envelope.name}</h3>
            <span className={styles.purposePill} data-purpose={envelope.purpose}>
              {purposeLabel(envelope.purpose)}
            </span>
          </div>
          <div className={styles.envelopeValue}>
            <strong data-negative={envelope.isNegative ? "true" : undefined}>
              {envelope.purpose === "GOAL" && goalProgress ? `Faltam ${available}` : available}
            </strong>
            <span>{envelope.purpose === "GOAL" && goalProgress ? `meta acumulada ${base}` : `de ${base}`}</span>
          </div>
        </div>
      </div>

      <ProgressBar
        label={`Progresso de ${envelope.name}`}
        value={progressValue}
        purpose={envelope.purpose}
        negative={envelope.isNegative}
        valueText={envelope.isNegative ? status : undefined}
      />

      <p
        className={styles.statusText}
        data-negative={envelope.isNegative ? "true" : undefined}
        role={envelope.isNegative ? "alert" : undefined}
      >
        {status}
      </p>

      <div className={styles.cardActions}>
        {envelope.role === "PARTICIPANT" && (
          <span className={`${styles.badge} ${styles.badgeShared}`}>Compartilhada</span>
        )}
        {onRegisterExpense && (
          <Button
            onClick={() => onRegisterExpense(envelope)}
            aria-label={`${registerActionLabel} em ${envelope.name}`}
          >
            {registerActionLabel}
          </Button>
        )}
        {menuItems.length > 0 && (
          <ActionMenu
            ariaLabel={`Mais ações de ${envelope.name}`}
            items={menuItems}
          />
        )}
      </div>
    </li>
  );
}
