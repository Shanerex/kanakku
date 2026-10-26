import { CaretLeft, CaretRight, Plus } from "@phosphor-icons/react";
import {
  type FormEvent,
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";
import { Link, useNavigate } from "react-router";
import { newIdempotencyKey } from "../../api/client";
import { useApi } from "../../app/api-context";
import { InlineProblem } from "../../app/InlineProblem";
import {
  asProblem,
  describeNetworkError,
  describeProblem,
  fieldMessages,
  type PlainProblem,
} from "../../app/problems";
import { useSession } from "../../app/session";
import { Button } from "../../ui/Button";
import { CurrencySelect } from "../../ui/CurrencySelect";
import { TextField } from "../../ui/TextField";
import { ClaimSuggestionsBanner } from "./ClaimSuggestionsBanner";
import {
  COVER_COLORS,
  COVER_EMOJIS,
  type Cover,
  coverValue,
  GROUP_TYPES,
} from "./cover";
import { GroupBadge } from "./GroupBadge";
import type { Group } from "./types";

type Errors = { name?: string; currency?: string; cover?: string };

/** The groups I belong to, and a form to start a new one. */
export default function GroupsPage() {
  const api = useApi();
  const { state } = useSession();
  const [groups, setGroups] = useState<Group[]>();
  const [problem, setProblem] = useState<PlainProblem>();
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    try {
      const { data, error } = await api.GET("/api/v1/groups");
      if (data) setGroups(data);
      else setProblem(describeProblem(asProblem(error)));
    } catch {
      setProblem(describeNetworkError());
    }
  }, [api]);

  useEffect(() => {
    load();
  }, [load]);

  if (state.status !== "signed-in") return null;

  return (
    <main className="mx-auto flex max-w-[560px] flex-col gap-4 px-4 py-6">
      <Link
        to="/me"
        className="inline-flex items-center gap-1 text-primary-text"
      >
        <CaretLeft size={16} aria-hidden="true" />
        Me
      </Link>
      <div className="flex items-center justify-between gap-3">
        <h1 className="font-display text-h1 font-bold text-text">Groups</h1>
        {!creating && (
          <Button variant="secondary" onClick={() => setCreating(true)}>
            <Plus size={16} aria-hidden="true" />
            New group
          </Button>
        )}
      </div>
      <ClaimSuggestionsBanner />
      {problem && <InlineProblem problem={problem} />}
      {creating && (
        <NewGroupForm
          defaultCurrency={state.profile.defaultCurrency}
          onCancel={() => setCreating(false)}
        />
      )}
      {groups?.length === 0 && !creating && (
        <section className="rounded-lg bg-surface p-6 text-center shadow-sm">
          <h2 className="font-display text-h2 font-bold text-text">
            No groups yet
          </h2>
          <p className="text-text-muted">
            Start one for a trip, a flat or a couple, then add people.
          </p>
        </section>
      )}
      {groups && groups.length > 0 && (
        <ul
          aria-label="Your groups"
          className="flex flex-col rounded-lg bg-surface shadow-sm"
        >
          {groups.map((group) => (
            <li key={group.id}>
              <Link
                to={`/groups/${group.id}`}
                className="flex min-h-16 items-center gap-3 rounded-lg px-4 py-2"
              >
                <GroupBadge
                  id={group.id}
                  name={group.name}
                  cover={group.cover}
                />
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-semibold text-text">
                    {group.name}
                  </span>
                  <span className="block text-body-sm text-text-muted">
                    {group.currency}
                    {group.myRole === "OWNER" ? " · You're the Owner" : ""}
                    {group.archivedAt ? " · Archived" : ""}
                  </span>
                </span>
                <CaretRight
                  size={20}
                  aria-hidden="true"
                  className="text-text-muted"
                />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}

function NewGroupForm({
  defaultCurrency,
  onCancel,
}: {
  defaultCurrency: string;
  onCancel: () => void;
}) {
  const api = useApi();
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [type, setType] =
    useState<(typeof GROUP_TYPES)[number]["value"]>("TRIP");
  const [currency, setCurrency] = useState(defaultCurrency);
  const [cover, setCover] = useState<Cover>({ kind: "none" });
  const [errors, setErrors] = useState<Errors>({});
  const [problem, setProblem] = useState<PlainProblem>();
  const [submitting, setSubmitting] = useState(false);
  const nameRef = useRef<HTMLInputElement>(null);
  // One key per submit attempt: a retry after a failure reuses it, an edit does not.
  const attempt = useRef<{ key: string; signature: string }>(undefined);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setProblem(undefined);
    const trimmed = name.trim();
    if (!trimmed) {
      setErrors({ name: "Enter a name for the group." });
      return nameRef.current?.focus();
    }
    if (Array.from(trimmed).length > 60) {
      setErrors({ name: "Use 60 characters or fewer." });
      return nameRef.current?.focus();
    }
    setErrors({});
    const body = {
      name: trimmed,
      type,
      currency,
      cover: coverValue(cover),
    };
    const signature = JSON.stringify(body);
    if (attempt.current?.signature !== signature) {
      attempt.current = { key: newIdempotencyKey(), signature };
    }
    setSubmitting(true);
    try {
      const { data, error } = await api.POST("/api/v1/groups", {
        params: { header: { "Idempotency-Key": attempt.current.key } },
        body,
      });
      if (data) {
        navigate(`/groups/${data.id}`);
        return;
      }
      const raw = asProblem(error);
      setErrors(fieldMessages(raw) as Errors);
      setProblem(describeProblem(raw));
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form
      noValidate
      onSubmit={submit}
      aria-label="New group"
      className="flex flex-col gap-4 rounded-lg bg-surface p-4 shadow-sm"
    >
      <h2 className="font-display text-h2 font-bold text-text">New group</h2>
      {problem && <InlineProblem problem={problem} />}
      <fieldset className="flex flex-col gap-2">
        <legend className="text-body-sm font-semibold text-text">Type</legend>
        <div className="flex flex-wrap gap-2">
          {GROUP_TYPES.map((option) => (
            <label
              key={option.value}
              className={`inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-pill border px-4 text-body-sm font-semibold has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-focus-ring ${
                type === option.value
                  ? "border-primary bg-primary-soft text-primary-text"
                  : "border-border-strong bg-surface text-text"
              }`}
            >
              <input
                type="radio"
                name="type"
                value={option.value}
                checked={type === option.value}
                onChange={() => setType(option.value)}
                className="sr-only"
              />
              {option.label}
            </label>
          ))}
        </div>
      </fieldset>
      <TextField
        label="Name"
        name="name"
        autoComplete="off"
        placeholder="Goa, Dec 2026"
        value={name}
        error={errors.name}
        onChange={(e) => setName(e.target.value)}
        inputRef={nameRef}
      />
      <CurrencySelect
        label="Currency"
        value={currency}
        onChange={setCurrency}
        error={errors.currency}
      />
      <CoverPicker value={cover} onChange={setCover} error={errors.cover} />
      <div className="flex gap-2">
        <Button type="submit" size="lg" loading={submitting}>
          Create group
        </Button>
        <Button variant="ghost" size="lg" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  );
}

export function CoverPicker({
  value,
  onChange,
  error,
  allowClear = true,
}: {
  value: Cover;
  onChange: (cover: Cover) => void;
  error?: string;
  allowClear?: boolean;
}) {
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-body-sm font-semibold text-text">
        Cover (optional)
      </legend>
      <div className="flex flex-wrap gap-2">
        {COVER_COLORS.map((color) => {
          const selected = value.kind === "color" && value.color === color;
          return (
            <label
              key={color}
              style={{ background: `var(--id-${color}-tint)` }}
              className={`size-11 cursor-pointer rounded-pill border-2 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-focus-ring ${selected ? "border-primary" : "border-border"}`}
            >
              <input
                type="radio"
                name="cover"
                checked={selected}
                aria-label={`Colour ${color}`}
                onChange={() => onChange({ kind: "color", color })}
                className="sr-only"
              />
            </label>
          );
        })}
      </div>
      <div className="flex flex-wrap gap-2">
        {COVER_EMOJIS.map((emoji) => {
          const selected = value.kind === "emoji" && value.emoji === emoji;
          return (
            <label
              key={emoji}
              className={`inline-flex size-11 cursor-pointer items-center justify-center rounded-md border text-xl has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-focus-ring ${selected ? "border-primary bg-primary-soft" : "border-border bg-surface"}`}
            >
              <input
                type="radio"
                name="cover"
                checked={selected}
                aria-label={`Emoji ${emoji}`}
                onChange={() => onChange({ kind: "emoji", emoji })}
                className="sr-only"
              />
              <span aria-hidden="true">{emoji}</span>
            </label>
          );
        })}
      </div>
      {allowClear && value.kind !== "none" && (
        <button
          type="button"
          onClick={() => onChange({ kind: "none" })}
          className="self-start text-body-sm font-semibold text-primary-text underline"
        >
          Clear cover
        </button>
      )}
      {error && <p className="text-body-sm text-danger-text">{error}</p>}
    </fieldset>
  );
}
