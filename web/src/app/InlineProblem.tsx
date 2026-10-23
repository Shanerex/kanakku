import { WarningCircle } from "@phosphor-icons/react";
import type { PlainProblem } from "./problems";

/** An error banner above a form. Announced to screen readers; never shows codes or URIs. */
export function InlineProblem({ problem }: { problem: PlainProblem }) {
  return (
    <div
      role="alert"
      className="flex items-start gap-2 rounded-md bg-danger-soft p-3 text-body-sm text-danger-text"
    >
      <WarningCircle
        size={20}
        weight="bold"
        aria-hidden="true"
        className="mt-0.5 shrink-0"
      />
      <p>
        {problem.title && (
          <strong className="block font-bold">{problem.title}</strong>
        )}
        {problem.body}
      </p>
    </div>
  );
}
