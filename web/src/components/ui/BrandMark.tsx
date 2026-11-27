export function BrandMark({ size = 28 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 36 36"
      fill="none"
      aria-hidden="true"
    >
      <path d="M7 8L18 28L22.5 19.5L15 8H7Z" fill="currentColor" />
      <path d="M29 8L18 28L14.5 21.5L21.8 8H29Z" fill="var(--brand-mark-accent)" />
    </svg>
  );
}
