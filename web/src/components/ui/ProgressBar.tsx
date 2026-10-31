import styles from "./ProgressBar.module.css";

type Props = {
  label: string;
  value: number;
  max?: number;
  purpose?: string;
  negative?: boolean;
  valueText?: string;
};

export function ProgressBar({
  label,
  value,
  max = 100,
  purpose,
  negative = false,
  valueText,
}: Props) {
  return (
    <progress
      className={styles.progressTrack}
      data-purpose={purpose}
      data-negative={negative ? "true" : "false"}
      aria-label={label}
      aria-valuetext={valueText}
      value={value}
      max={max}
    />
  );
}
