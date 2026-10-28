import type { ReactNode } from "react";

const TONES = {
  neutral: "bg-surface-sunken text-text",
  primary: "bg-primary-soft text-primary-text",
  positive: "bg-positive-soft text-positive-text",
  negative: "bg-negative-soft text-negative-text",
  info: "bg-info-soft text-info-text",
  /** On a tinted header band, where a sunken chip would vanish. */
  onTint: "bg-surface text-text",
} as const;

/** A static `info` chip (components.md Chip): pill, caption type, one tone. */
export function Chip({
  tone = "neutral",
  small = false,
  children,
}: {
  tone?: keyof typeof TONES;
  small?: boolean;
  children: ReactNode;
}) {
  return (
    <span
      className={`inline-flex items-center gap-1 whitespace-nowrap rounded-pill font-bold ${TONES[tone]} ${small ? "h-5 px-2 text-caption" : "h-6 px-2.5 text-caption"}`}
    >
      {children}
    </span>
  );
}
