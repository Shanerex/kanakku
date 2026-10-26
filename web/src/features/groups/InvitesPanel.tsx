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
import type { Invite, Member } from "./types";

const EXPIRY_CHOICES = [
  { days: 1, label: "1 day" },
  { days: 7, label: "7 days" },
  { days: 30, label: "30 days" },
] as const;

const STATUS_WORDS: Record<Invite["status"], string> = {
  ACTIVE: "Active",
  EXPIRED: "Expired",
  REVOKED: "Revoked",
  USED: "Used",
};

/** The link a guest opens: the token rides in the fragment so no server or log ever sees it. */
export function inviteLink(token: string): string {
  return `${window.location.origin}/invite#${token}`;
}

/** Create, copy and revoke single-use invite links for guests (Owner only). */
export function InvitesPanel({
  groupId,
  guests,
  invites,
  onChanged,
}: {
  groupId: string;
  guests: Member[];
  invites: Invite[];
  onChanged: () => void;
}) {
  const api = useApi();
  const [memberId, setMemberId] = useState("");
  const [days, setDays] = useState<number>(7);
  const [fresh, setFresh] = useState<{ guest: string; link: string }>();
  const [copied, setCopied] = useState<string>();
  const [problem, setProblem] = useState<PlainProblem>();
  const [busy, setBusy] = useState(false);
  const attempt = useRef<{ key: string; signature: string }>(undefined);

  const chosen = memberId || guests[0]?.id || "";

  async function create() {
    if (busy || !chosen) return;
    setProblem(undefined);
    setCopied(undefined);
    const signature = `${chosen}/${days}`;
    if (attempt.current?.signature !== signature) {
      attempt.current = { key: newIdempotencyKey(), signature };
    }
    setBusy(true);
    try {
      const { data, error } = await api.POST(
        "/api/v1/groups/{groupId}/invites",
        {
          params: {
            path: { groupId },
            header: { "Idempotency-Key": attempt.current.key },
          },
          body: { memberId: chosen, expiryDays: days },
        },
      );
      if (data) {
        setFresh({
          guest: data.invite.guestName,
          link: inviteLink(data.token),
        });
        attempt.current = undefined;
        onChanged();
      } else setProblem(describeProblem(asProblem(error)));
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setBusy(false);
    }
  }

  async function copy(link: string) {
    try {
      await navigator.clipboard.writeText(link);
      setCopied("Link copied.");
    } catch {
      setCopied("Select the link and copy it.");
    }
  }

  async function revoke(invite: Invite) {
    setProblem(undefined);
    try {
      const { response, error } = await api.POST(
        "/api/v1/groups/{groupId}/invites/{inviteId}/revoke",
        {
          params: { path: { groupId, inviteId: invite.id } },
          body: { version: invite.version },
        },
      );
      if (response.ok) {
        setFresh(undefined);
        onChanged();
      } else setProblem(describeProblem(asProblem(error)));
    } catch {
      setProblem(describeNetworkError());
    }
  }

  return (
    <section
      aria-labelledby="invites-title"
      className="flex flex-col gap-3 rounded-lg bg-surface p-4 shadow-sm"
    >
      <h2
        id="invites-title"
        className="font-display text-h2 font-bold text-text"
      >
        Invite links
      </h2>
      {problem && <InlineProblem problem={problem} />}
      {guests.length === 0 ? (
        <p className="text-text-muted">
          Add a guest first, then share an invite so they can take their place.
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          <div className="flex flex-col gap-1">
            <label
              htmlFor="invite-guest"
              className="text-body-sm font-semibold text-text"
            >
              Invite for
            </label>
            <select
              id="invite-guest"
              value={chosen}
              onChange={(e) => setMemberId(e.target.value)}
              className="min-h-11 rounded-md border border-border bg-surface-sunken px-3 text-body text-text"
            >
              {guests.map((guest) => (
                <option key={guest.id} value={guest.id}>
                  {guest.displayName}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-1">
            <label
              htmlFor="invite-expiry"
              className="text-body-sm font-semibold text-text"
            >
              Expires after
            </label>
            <select
              id="invite-expiry"
              value={days}
              onChange={(e) => setDays(Number(e.target.value))}
              className="min-h-11 rounded-md border border-border bg-surface-sunken px-3 text-body text-text"
            >
              {EXPIRY_CHOICES.map((choice) => (
                <option key={choice.days} value={choice.days}>
                  {choice.label}
                </option>
              ))}
            </select>
          </div>
          <Button variant="secondary" loading={busy} onClick={create}>
            Create invite link
          </Button>
        </div>
      )}
      {fresh && (
        <div className="flex flex-col gap-2 rounded-md bg-info-soft p-3">
          <p className="text-body-sm text-info-text">
            Link for {fresh.guest}. It works once, and this is the only time it
            is shown.
          </p>
          <input
            aria-label="Invite link"
            readOnly
            value={fresh.link}
            onFocus={(e) => e.currentTarget.select()}
            className="min-h-11 rounded-md border border-border bg-surface px-3 font-mono text-body-sm text-text"
          />
          <Button
            variant="secondary"
            size="sm"
            onClick={() => copy(fresh.link)}
          >
            Copy link
          </Button>
          {copied && (
            <p role="status" className="text-body-sm text-info-text">
              {copied}
            </p>
          )}
        </div>
      )}
      {invites.length > 0 && (
        <ul aria-label="Invites" className="flex flex-col">
          {invites.map((invite) => (
            <li
              key={invite.id}
              className="flex min-h-14 items-center gap-3 border-t border-border first:border-t-0"
            >
              <span className="min-w-0 flex-1">
                <span className="block truncate font-semibold text-text">
                  {invite.guestName}
                </span>
                <span className="block text-body-sm text-text-muted">
                  {STATUS_WORDS[invite.status]}
                  {invite.status === "ACTIVE"
                    ? ` · expires ${new Date(invite.expiresAt).toLocaleDateString("en-IN")}`
                    : ""}
                </span>
              </span>
              {invite.status === "ACTIVE" && (
                <Button
                  variant="ghost"
                  size="sm"
                  aria-label={`Revoke invite for ${invite.guestName}`}
                  onClick={() => revoke(invite)}
                >
                  Revoke
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
