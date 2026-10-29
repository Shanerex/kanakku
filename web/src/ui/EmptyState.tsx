import type { ReactNode } from "react";

/** Illustration, h2 headline, one line of body and at most one action (patterns.md, Empty states). */
export function EmptyState({
  illustration,
  title,
  body,
  action,
}: {
  illustration: ReactNode;
  title: string;
  body: string;
  action?: ReactNode;
}) {
  return (
    <section className="flex flex-col items-center gap-3 rounded-lg border border-border bg-surface px-6 py-10 text-center shadow-sm">
      <div aria-hidden="true" className="h-36 w-36">
        {illustration}
      </div>
      <h2 className="font-display text-h2 font-bold text-text">{title}</h2>
      <p className="max-w-[36ch] text-text-muted">{body}</p>
      {action}
    </section>
  );
}
