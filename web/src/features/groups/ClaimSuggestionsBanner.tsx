import { UserSwitch } from "@phosphor-icons/react";
import { useEffect, useState } from "react";
import { useApi } from "../../app/api-context";
import { InlineProblem } from "../../app/InlineProblem";
import {
  asProblem,
  describeNetworkError,
  describeProblem,
  type PlainProblem,
} from "../../app/problems";
import { Button } from "../../ui/Button";
import { useToast } from "../../ui/Toast";
import type { ClaimSuggestion } from "./types";

/**
 * "You might be Bala (guest) in Goa Trip": one card per pending suggestion, shown when a number
 * the person verified matches a guest's in one of their groups. Nothing changes until they choose.
 */
export function ClaimSuggestionsBanner() {
  const api = useApi();
  const [suggestions, setSuggestions] = useState<ClaimSuggestion[]>([]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: asked once on arrival
  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const { data } = await api.GET("/api/v1/claim-suggestions");
        if (active && data) setSuggestions(data);
      } catch {
        // The banner is a bonus; the group list below works without it.
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  if (suggestions.length === 0) return null;
  return (
    <section aria-label="Seats you might have" className="flex flex-col gap-3">
      {suggestions.map((suggestion) => (
        <SuggestionCard
          key={suggestion.id}
          suggestion={suggestion}
          onDone={() =>
            setSuggestions((all) => all.filter((s) => s.id !== suggestion.id))
          }
        />
      ))}
    </section>
  );
}

function SuggestionCard({
  suggestion,
  onDone,
}: {
  suggestion: ClaimSuggestion;
  onDone: () => void;
}) {
  const api = useApi();
  const toast = useToast();
  const [busy, setBusy] = useState<"claim" | "decline">();
  const [problem, setProblem] = useState<PlainProblem>();

  async function answer(action: "claim" | "decline") {
    if (busy) return;
    setBusy(action);
    setProblem(undefined);
    try {
      const params = { path: { id: suggestion.id } };
      const body = { version: suggestion.version };
      const { data, error } =
        action === "claim"
          ? await api.POST("/api/v1/claim-suggestions/{id}/confirm", {
              params,
              body,
            })
          : await api.POST("/api/v1/claim-suggestions/{id}/decline", {
              params,
              body,
            });
      if (data) {
        toast.show(
          action === "claim"
            ? `Claimed. ${suggestion.guestName}'s balance is now yours.`
            : "Got it. We won't suggest this seat again.",
        );
        onDone();
        return;
      }
      setProblem(describeProblem(asProblem(error)));
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setBusy(undefined);
    }
  }

  return (
    <article
      aria-label={`You might be ${suggestion.guestName} in ${suggestion.groupName}`}
      className="flex flex-col gap-3 rounded-lg bg-primary-soft p-4"
    >
      <div className="flex items-start gap-3">
        <UserSwitch
          size={24}
          weight="duotone"
          aria-hidden="true"
          className="mt-0.5 shrink-0 text-primary-text"
        />
        <div className="min-w-0">
          <p className="font-semibold text-text">
            You might be {suggestion.guestName} in {suggestion.groupName}.
          </p>
          <p className="text-body-sm text-text-muted">
            Claiming moves their balance onto your own entry. Nothing changes
            until you choose.
          </p>
        </div>
      </div>
      {problem && <InlineProblem problem={problem} />}
      <div className="flex gap-2">
        <Button
          size="sm"
          loading={busy === "claim"}
          onClick={() => answer("claim")}
        >
          Claim
        </Button>
        <Button
          variant="ghost"
          size="sm"
          loading={busy === "decline"}
          onClick={() => answer("decline")}
        >
          Not me
        </Button>
      </div>
    </article>
  );
}
