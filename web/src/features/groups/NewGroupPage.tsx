import { type FormEvent, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { newIdempotencyKey } from "../../api/client";
import { usePageTitle } from "../../app/AppShell";
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
import { Button, ButtonLink } from "../../ui/Button";
import { cardClass } from "../../ui/Card";
import { CurrencySelect } from "../../ui/CurrencySelect";
import { TextField } from "../../ui/TextField";
import { CoverPicker } from "./CoverPicker";
import { type Cover, coverValue, GROUP_TYPES } from "./cover";
import { GroupCardFace } from "./GroupCardFace";

type Errors = { name?: string; currency?: string; cover?: string };
type GroupType = (typeof GROUP_TYPES)[number]["value"];

/** Each type's default cover emoji; picking a type sets it until a cover is chosen. */
const TYPE_EMOJI: Record<GroupType, string> = {
  TRIP: "🏖",
  HOME: "🏠",
  COUPLE: "💞",
  OTHER: "👥",
};

/** `/groups/new`: a one-screen form with a live preview of the group's badge and colour band. */
export default function NewGroupPage() {
  const api = useApi();
  const navigate = useNavigate();
  const { state } = useSession();
  const [name, setName] = useState("");
  const [type, setType] = useState<GroupType>("TRIP");
  const [currency, setCurrency] = useState<string>();
  const [picked, setPicked] = useState<Cover>();
  const [errors, setErrors] = useState<Errors>({});
  const [problem, setProblem] = useState<PlainProblem>();
  const [submitting, setSubmitting] = useState(false);
  const nameRef = useRef<HTMLInputElement>(null);
  // One key per submit attempt: a retry after a failure reuses it, an edit does not.
  const attempt = useRef<{ key: string; signature: string }>(undefined);
  usePageTitle("New group");
  if (state.status !== "signed-in") return null;

  const cover: Cover = picked ?? { kind: "emoji", emoji: TYPE_EMOJI[type] };
  const chosenCurrency = currency ?? state.profile.defaultCurrency;
  const trimmed = name.trim();

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setProblem(undefined);
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
      currency: chosenCurrency,
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
    <div className="flex w-full max-w-[560px] flex-col gap-4">
      <section
        aria-label="Preview"
        className="flex flex-col overflow-hidden rounded-lg border border-border bg-surface shadow-sm"
      >
        <GroupCardFace
          id="new-group"
          name={trimmed || "Your group"}
          cover={coverValue(cover)}
          type={type}
          currency={chosenCurrency}
          owner
        />
      </section>
      <form
        noValidate
        onSubmit={submit}
        aria-label="New group"
        className={`${cardClass()} flex flex-col gap-4`}
      >
        {problem && <InlineProblem problem={problem} />}
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-body-sm font-bold text-text">
            Type
          </legend>
          <div className="flex flex-wrap gap-2">
            {GROUP_TYPES.map((option) => (
              <label
                key={option.value}
                className={`inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-pill border px-4 text-body-sm font-bold has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-focus-ring ${
                  type === option.value
                    ? "border-transparent bg-primary-soft text-primary-text"
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
                <span aria-hidden="true">{TYPE_EMOJI[option.value]}</span>
                {option.label}
              </label>
            ))}
          </div>
        </fieldset>
        <TextField
          label="Name"
          name="name"
          autoComplete="off"
          placeholder={type === "HOME" ? "Flat 4B" : "Goa, Dec 2026"}
          value={name}
          error={errors.name}
          onChange={(e) => setName(e.target.value)}
          inputRef={nameRef}
        />
        <CoverPicker
          value={cover}
          onChange={setPicked}
          error={errors.cover}
          allowClear={false}
        />
        <CurrencySelect
          label="Currency"
          value={chosenCurrency}
          onChange={setCurrency}
          error={errors.currency}
        />
        <div className="flex gap-2">
          <Button type="submit" size="lg" loading={submitting}>
            Create group
          </Button>
          <ButtonLink to="/groups" variant="ghost" size="lg">
            Cancel
          </ButtonLink>
        </div>
      </form>
    </div>
  );
}
