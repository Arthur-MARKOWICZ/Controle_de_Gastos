import styles from "./ProgressRing.module.css";

type Props = {
  value: number;
  max?: number;
  purpose?: string;
  negative?: boolean;
  size?: number;
};

export function ProgressRing({
  value,
  max = 100,
  purpose,
  negative = false,
  size = 38,
}: Props) {
  const radius = 15;
  const circumference = 2 * Math.PI * radius;
  const clamped = negative ? 1 : max > 0 ? Math.max(0, Math.min(1, value / max)) : 0;
  const dashOffset = circumference * (1 - clamped);

  return (
    <svg
      className={styles.ring}
      width={size}
      height={size}
      viewBox="0 0 38 38"
      data-purpose={purpose}
      data-negative={negative ? "true" : "false"}
      aria-hidden="true"
    >
      <circle
        className={styles.track}
        cx="19"
        cy="19"
        r={radius}
        fill="none"
        strokeWidth="3"
      />
      <circle
        className={styles.indicator}
        cx="19"
        cy="19"
        r={radius}
        fill="none"
        strokeWidth="3"
        strokeDasharray={circumference}
        strokeDashoffset={dashOffset}
        strokeLinecap="round"
        transform="rotate(-90 19 19)"
      />
    </svg>
  );
}
