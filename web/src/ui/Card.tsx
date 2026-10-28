import type { ElementType, HTMLAttributes } from "react";

type Props = HTMLAttributes<HTMLElement> & {
  as?: ElementType;
  /** No padding, for lists whose rows reach the card's edge. */
  flush?: boolean;
};

/** The Card's classes, for elements Card can't render as (a `form`). */
export function cardClass(flush = false) {
  return `rounded-lg border border-border bg-surface shadow-sm ${flush ? "overflow-hidden" : "p-5"}`;
}

/** `surface`, `radius.lg`, `shadow.sm`, with an edge; 20 px padding unless flush. */
export function Card({
  as: Tag = "div",
  flush = false,
  className = "",
  ...rest
}: Props) {
  return <Tag {...rest} className={`${cardClass(flush)} ${className}`} />;
}
