import type { ReactNode } from "react";
import styles from "./Notice.module.css";

type Tone = "info" | "success" | "warning" | "danger";

type Props = {
  tone?: Tone;
  children: ReactNode;
  onClose?: () => void;
  className?: string;
};

export function Notice({ tone = "info", children, onClose, className }: Props) {
  const role = tone === "danger" || tone === "warning" ? "alert" : "status";
  const classes = [styles.notice, styles[tone], className].filter(Boolean).join(" ");

  return (
    <div className={classes} role={role}>
      <div className={styles.body}>{children}</div>
      {onClose && (
        <button type="button" className={styles.close} aria-label="Fechar aviso" onClick={onClose}>
          Fechar
        </button>
      )}
    </div>
  );
}
