import { useRef, useState } from "react";
import { newIdempotencyKey } from "../../api/client";
import { useApi } from "../../app/api-context";
import { InlineProblem } from "../../app/InlineProblem";
import {
  asProblem,
  describeNetworkError,
  describeProblem,
  type PlainProblem,
} from "../../app/problems";
import { Button } from "../../ui/Button";
import { Dialog } from "./Dialog";
import { formatMoney } from "./money";
import type { Member } from "./types";

/** Writes a member's balance off onto the members the Owner picks, all preselected. */
export function WriteOffDialog({
  groupId,
  target,
  members,
  onDone,
  onClose,
}: {
  groupId: string;
  target: Member;
  members: Member[];
  onDone: () => void;
  onClose: () => void;
}) {
  const api = useApi();
  const others = members.filter((m) => m.id !== target.id);
  const [picked, setPicked] = useState(() => new Set(others.map((m) => m.id)));
  const [problem, setProblem] = useState<PlainProblem>();
  const [busy, setBusy] = useState(false);
  const key = useRef(newIdempotencyKey());

  const amounts = target.balances
    .map((b) => formatMoney(Math.abs(b.amountMinor), b.currency))
    .join(" and ");

  function toggle(id: string) {
    // A different body is a different request, so it gets its own key; a retry of the same body
    // keeps this one and the server replays the write-off instead of doing it twice.
    key.current = newIdempotencyKey();
    setPicked((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function confirm() {
    if (busy || picked.size === 0) return;
    setBusy(true);
    setProblem(undefined);
    try {
      const { response, error } = await api.POST(
        "/api/v1/groups/{groupId}/members/{memberId}/write-offs",
        {
          params: {
            path: { groupId, memberId: target.id },
            header: { "Idempotency-Key": key.current },
          },
          body: {
            absorberMemberIds: others
              .filter((m) => picked.has(m.id))
              .map((m) => m.id),
          },
        },
      );
      if (response.ok) onDone();
      else setProblem(describeProblem(asProblem(error)));
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog title={`Write off ${amounts}?`} onClose={onClose}>
      <p className="text-text-muted">
        {target.displayName}'s balance moves onto{" "}
        {picked.size === 1 ? "1 member" : `${picked.size} members`}, split
        equally. It shows in the group's history.
      </p>
      {problem && <InlineProblem problem={problem} />}
      <fieldset className="flex flex-col gap-1">
        <legend className="text-body-sm font-semibold text-text">
          Who absorbs it
        </legend>
        {others.map((member) => (
          <label
            key={member.id}
            className="flex min-h-11 cursor-pointer items-center gap-3"
          >
            <input
              type="checkbox"
              checked={picked.has(member.id)}
              onChange={() => toggle(member.id)}
              className="size-5"
            />
            <span className="text-text">{member.displayName}</span>
          </label>
        ))}
      </fieldset>
      <div className="flex gap-2">
        <Button
          variant="danger"
          loading={busy}
          disabled={picked.size === 0}
          onClick={confirm}
        >
          Write off
        </Button>
        <Button variant="ghost" onClick={onClose}>
          Cancel
        </Button>
      </div>
    </Dialog>
  );
}
