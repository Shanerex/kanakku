import { WarningCircle } from "@phosphor-icons/react";
import { useEffect, useRef, useState } from "react";
import { newIdempotencyKey } from "../../api/client";
import { useApi } from "../../app/api-context";
import { InlineProblem } from "../../app/InlineProblem";
import {
  asProblem,
  describeNetworkError,
  describeProblem,
  type PlainProblem,
} from "../../app/problems";
import { Avatar } from "../../ui/Avatar";
import { Button } from "../../ui/Button";
import { TextField } from "../../ui/TextField";
import { useToast } from "../../ui/Toast";
import { inviteLink } from "./InvitesPanel";
import type { UserMatch } from "./types";

const MIN_CHARS = 3;
const MIN_DIGITS = 4;
const DEBOUNCE_MS = 400;

/** Digits, spaces, dashes, dots, brackets and a leading plus: a phone search, not a name. */
function digitCount(text: string): number | undefined {
  return /^\+?[\d\s().-]*$/.test(text) && /\d/.test(text)
    ? text.replace(/\D/g, "").length
    : undefined;
}

/** Whether the query is long enough to search: 4 digits for a number, 3 characters otherwise. */
export function longEnough(text: string): boolean {
  const digits = digitCount(text);
  return digits !== undefined
    ? digits >= MIN_DIGITS
    : Array.from(text).length >= MIN_CHARS;
}

type Invited = { name: string; link: string };

/**
 * Find a registered user by name, email or phone number and add them directly (Owner only).
 * Someone who chose invite-only gets an Invite button instead: it makes a guest and a single-use
 * link that only they can open. A phone match whose number is unverified is marked so, because
 * anyone could have typed that number; it never stops the add.
 */
export function MemberPicker({
  groupId,
  onAdded,
}: {
  groupId: string;
  onAdded: () => void;
}) {
  const api = useApi();
  const toast = useToast();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserMatch[]>([]);
  const [searched, setSearched] = useState(false);
  const [problem, setProblem] = useState<PlainProblem>();
  const [working, setWorking] = useState<string>();
  const [invited, setInvited] = useState<Record<string, Invited>>({});
  const latest = useRef(0);
  const keys = useRef(new Map<string, string>());

  const text = query.trim();
  const ready = longEnough(text);
  // biome-ignore lint/correctness/useExhaustiveDependencies: `text` is the trigger; the rest are stable
  useEffect(() => {
    const call = ++latest.current;
    if (!ready) {
      setResults([]);
      setSearched(false);
      return;
    }
    const timer = setTimeout(async () => {
      try {
        const { data, error } = await api.POST(
          "/api/v1/groups/{groupId}/user-search",
          { params: { path: { groupId } }, body: { q: text } },
        );
        if (call !== latest.current) return;
        if (data) {
          setResults(data);
          setSearched(true);
          setProblem(undefined);
        } else setProblem(describeProblem(asProblem(error)));
      } catch {
        if (call === latest.current) setProblem(describeNetworkError());
      }
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [text]);

  function keyFor(action: string, id: string): string {
    const name = `${action}:${id}`;
    let key = keys.current.get(name);
    if (!key) {
      key = newIdempotencyKey();
      keys.current.set(name, key);
    }
    return key;
  }

  async function add(match: UserMatch) {
    if (working) return;
    setWorking(match.id);
    setProblem(undefined);
    try {
      const { response, error } = await api.POST(
        "/api/v1/groups/{groupId}/members",
        {
          params: {
            path: { groupId },
            header: { "Idempotency-Key": keyFor("add", match.id) },
          },
          body: { userId: match.id },
        },
      );
      if (response.ok) {
        keys.current.delete(`add:${match.id}`);
        setResults((all) => all.filter((m) => m.id !== match.id));
        onAdded();
        return;
      }
      const raw = asProblem(error);
      if (raw?.type?.split(":").pop() === "invite-only") {
        setResults((all) =>
          all.map((m) => (m.id === match.id ? { ...m, directAdd: false } : m)),
        );
        setProblem(describeProblem(raw));
      } else setProblem(describeProblem(raw));
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setWorking(undefined);
    }
  }

  async function invite(match: UserMatch) {
    if (working) return;
    setWorking(match.id);
    setProblem(undefined);
    try {
      const { data, error } = await api.POST(
        "/api/v1/groups/{groupId}/user-invites",
        {
          params: {
            path: { groupId },
            header: { "Idempotency-Key": keyFor("invite", match.id) },
          },
          body: { userId: match.id },
        },
      );
      if (data) {
        keys.current.delete(`invite:${match.id}`);
        setInvited((all) => ({
          ...all,
          [match.id]: { name: match.displayName, link: inviteLink(data.token) },
        }));
        onAdded();
      } else setProblem(describeProblem(asProblem(error)));
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setWorking(undefined);
    }
  }

  async function copy(link: string) {
    try {
      await navigator.clipboard.writeText(link);
      toast.show("Link copied");
    } catch {
      toast.show("Couldn't copy. Select the link and copy it.", {
        tone: "error",
      });
    }
  }

  return (
    <section
      aria-labelledby="picker-title"
      className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-5 shadow-sm"
    >
      <h2
        id="picker-title"
        className="font-display text-h2 font-bold text-text"
      >
        Add someone with an account
      </h2>
      <TextField
        label="Find people"
        name="find"
        type="search"
        autoComplete="off"
        value={query}
        helper="Search by name, email or phone number. A part of a number works."
        onChange={(e) => setQuery(e.target.value)}
      />
      {problem && <InlineProblem problem={problem} />}
      {results.length > 0 && (
        <ul aria-label="Search results" className="flex flex-col">
          {results.map((match) => {
            const sent = invited[match.id];
            return (
              <li
                key={match.id}
                className="flex min-h-16 flex-wrap items-center gap-3 border-t border-border py-2 first:border-t-0"
              >
                <Avatar id={match.id} name={match.displayName} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-semibold text-text">
                    {match.displayName}
                  </span>
                  <span className="flex min-w-0 flex-wrap items-center gap-2">
                    <span className="truncate text-body-sm tabular-nums text-text-muted">
                      {match.matchedBy === "PHONE"
                        ? match.maskedPhone
                        : match.maskedEmail}
                    </span>
                    {match.phoneVerified === false && (
                      <span className="inline-flex items-center gap-1 rounded-pill bg-info-soft px-2 py-0.5 text-caption font-bold text-info-text">
                        <WarningCircle
                          size={14}
                          weight="fill"
                          aria-hidden="true"
                        />
                        Unverified
                      </span>
                    )}
                  </span>
                </span>
                {!sent && (
                  <Button
                    variant="secondary"
                    size="sm"
                    loading={working === match.id}
                    aria-label={`${match.directAdd ? "Add" : "Invite"} ${match.displayName}`}
                    onClick={() =>
                      match.directAdd ? add(match) : invite(match)
                    }
                  >
                    {match.directAdd ? "Add" : "Invite"}
                  </Button>
                )}
                {sent && (
                  <div className="flex w-full flex-col gap-2">
                    <p
                      role="status"
                      className="text-body-sm font-semibold text-positive-text"
                    >
                      Invite ready for {sent.name}
                    </p>
                    <div className="flex items-center gap-2">
                      <input
                        readOnly
                        aria-label={`Invite link for ${sent.name}`}
                        value={sent.link}
                        onFocus={(e) => e.currentTarget.select()}
                        className="min-h-11 min-w-0 flex-1 rounded-md border border-border bg-surface-sunken px-3 font-mono text-body-sm text-text"
                      />
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => copy(sent.link)}
                      >
                        Copy
                      </Button>
                    </div>
                    <p className="text-body-sm text-text-muted">
                      They join when they open this link while signed in.
                    </p>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
      {ready && searched && results.length === 0 && !problem && (
        <p className="text-body-sm text-text-muted">
          No one found for that search. Add them as a guest instead.
        </p>
      )}
    </section>
  );
}
