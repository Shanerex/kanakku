/** The "kanakku." wordmark: Baloo 2 800 in `primary`, the full stop in mango. */
export function Wordmark({ className = "" }: { className?: string }) {
  return (
    <span
      className={`font-display font-extrabold leading-tight tracking-[-0.01em] text-primary ${className}`}
    >
      kanakku<span className="text-[var(--id-mango-solid)]">.</span>
    </span>
  );
}
