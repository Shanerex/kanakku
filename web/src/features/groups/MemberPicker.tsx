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
import type { UserMatch } from "./types";

const MIN_CHARS = 3;
const DEBOUNCE_MS = 250;

/** Search registered users by name or email and add one to the group directly (Owner only). */
export function MemberPicker({
  groupId,
  onAdded,
}: {
  groupId: string;
  onAdded: () => void;
}) {
  const api = useApi();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserMatch[]>([]);
  const [problem, setProblem] = useState<PlainProblem>();
  const [adding, setAdding] = useState<string>();
  const latest = useRef(0);
  const keys = useRef(new Map<string, string>());

  const text = query.trim();
  // biome-ignore lint/correctness/useExhaustiveDependencies: `text` is the trigger; the rest are stable
  useEffect(() => {
    const call = ++latest.current;
    if (Array.from(text).length < MIN_CHARS) {
      setResults([]);
      return;
    }
    const timer = setTimeout(async () => {
      try {
        const { data, error } = await api.GET(
          "/api/v1/groups/{groupId}/user-search",
          { params: { path: { groupId }, query: { q: text } } },
        );
        if (call !== latest.current) return;
        if (data) {
          setResults(data);
          setProblem(undefined);
        } else setProblem(describeProblem(asProblem(error)));
      } catch {
        if (call === latest.current) setProblem(describeNetworkError());
      }
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [text]);

  async function add(match: UserMatch) {
    if (adding) return;
    setAdding(match.id);
    setProblem(undefined);
    let key = keys.current.get(match.id);
    if (!key) {
      key = newIdempotencyKey();
      keys.current.set(match.id, key);
    }
    try {
      const { response, error } = await api.POST(
        "/api/v1/groups/{groupId}/members",
        {
          params: { path: { groupId }, header: { "Idempotency-Key": key } },
          body: { userId: match.id },
        },
      );
      if (response.ok) {
        keys.current.delete(match.id);
        setResults((all) => all.filter((m) => m.id !== match.id));
        onAdded();
      } else setProblem(describeProblem(asProblem(error)));
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setAdding(undefined);
    }
  }

  return (
    <section
      aria-labelledby="picker-title"
      className="flex flex-col gap-3 rounded-lg bg-surface p-4 shadow-sm"
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
        helper="Search by name or email, at least 3 characters. People who prefer invites don't show up."
        onChange={(e) => setQuery(e.target.value)}
      />
      {problem && <InlineProblem problem={problem} />}
      {results.length > 0 && (
        <ul aria-label="Search results" className="flex flex-col">
          {results.map((match) => (
            <li
              key={match.id}
              className="flex min-h-16 items-center gap-3 border-t border-border first:border-t-0"
            >
              <Avatar id={match.id} name={match.displayName} />
              <span className="min-w-0 flex-1">
                <span className="block truncate font-semibold text-text">
                  {match.displayName}
                </span>
                <span className="block truncate text-body-sm text-text-muted">
                  {match.maskedEmail}
                </span>
              </span>
              <Button
                variant="secondary"
                size="sm"
                loading={adding === match.id}
                aria-label={`Add ${match.displayName}`}
                onClick={() => add(match)}
              >
                Add
              </Button>
            </li>
          ))}
        </ul>
      )}
      {Array.from(text).length >= MIN_CHARS &&
        results.length === 0 &&
        !problem && (
          <p className="text-body-sm text-text-muted">
            No one to add for that search.
          </p>
        )}
    </section>
  );
}
