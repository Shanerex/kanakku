import { type FormEvent, type ReactNode, useRef, useState } from "react";
import type { Profile } from "../../api/client";
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

const UPI = /^[\w.-]{2,256}@[a-zA-Z]{2,64}$/;

type Errors = {
  displayName?: string;
  defaultCurrency?: string;
  upiId?: string;
};

export function validateProfile(values: {
  displayName: string;
  upiId: string;
}): Errors {
  const errors: Errors = {};
  const name = values.displayName.trim();
  if (!name) errors.displayName = "Enter your name.";
  else if (Array.from(name).length > 80)
    errors.displayName = "Use 80 characters or fewer.";
  const upi = values.upiId.trim();
  if (upi && !UPI.test(upi)) errors.upiId = "Use a UPI ID like name@okbank.";
  return errors;
}

type Props = {
  profile: Profile;
  /** Called with the saved profile once the server has accepted the edit. */
  onSaved: (profile: Profile) => void;
  submitLabel: string;
  /** Shown beside the primary button, e.g. "Skip for now". */
  secondary?: ReactNode;
  /** Show the email as a read-only field. */
  showEmail?: boolean;
  /** Called with each name edit, for a live avatar preview. */
  onNameChange?: (name: string) => void;
  /** Show "who can add me directly"; when hidden the stored choice is left as it is. */
  showDirectAdd?: boolean;
};

/** Display name, default currency and optional UPI ID, saved with the version it started from. */
export function ProfileForm({
  profile,
  onSaved,
  submitLabel,
  secondary,
  showEmail,
  onNameChange,
  showDirectAdd,
}: Props) {
  const api = useApi();
  const session = useSession();
  const [displayName, setDisplayName] = useState(profile.displayName);
  const [defaultCurrency, setDefaultCurrency] = useState(
    profile.defaultCurrency,
  );
  const [upiId, setUpiId] = useState(profile.upiId ?? "");
  const [allowDirect, setAllowDirect] = useState(
    (profile.directAdd ?? "ALLOW") === "ALLOW",
  );
  const [errors, setErrors] = useState<Errors>({});
  const [problem, setProblem] = useState<PlainProblem>();
  const [conflict, setConflict] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const nameRef = useRef<HTMLInputElement>(null);
  const upiRef = useRef<HTMLInputElement>(null);

  async function reloadLatest() {
    const { data } = await api.GET("/api/v1/me");
    if (data) {
      session.setProfile(data);
      setDisplayName(data.displayName);
      setDefaultCurrency(data.defaultCurrency);
      setUpiId(data.upiId ?? "");
      setAllowDirect((data.directAdd ?? "ALLOW") === "ALLOW");
      onNameChange?.(data.displayName);
      setConflict(false);
      setProblem(undefined);
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setProblem(undefined);
    setConflict(false);
    const found = validateProfile({ displayName, upiId });
    setErrors(found);
    if (found.displayName) return nameRef.current?.focus();
    if (found.upiId) return upiRef.current?.focus();

    setSubmitting(true);
    try {
      const { data, error } = await api.PUT("/api/v1/me", {
        body: {
          displayName: displayName.trim(),
          defaultCurrency,
          upiId: upiId.trim() || undefined,
          ...(showDirectAdd
            ? { directAdd: allowDirect ? "ALLOW" : "INVITE_ONLY" }
            : {}),
          version: profile.version,
        },
      });
      if (data) {
        session.setProfile(data);
        onSaved(data);
        return;
      }
      const raw = asProblem(error);
      setErrors(fieldMessages(raw) as Errors);
      setConflict(raw?.type?.endsWith(":version-conflict") ?? false);
      setProblem(describeProblem(raw));
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form noValidate onSubmit={submit} className="flex flex-col gap-4">
      {problem && <InlineProblem problem={problem} />}
      {conflict && (
        <Button variant="secondary" onClick={reloadLatest}>
          Load the latest version
        </Button>
      )}
      <TextField
        label="Display name"
        name="displayName"
        autoComplete="name"
        value={displayName}
        error={errors.displayName}
        onChange={(e) => {
          setDisplayName(e.target.value);
          onNameChange?.(e.target.value);
        }}
        inputRef={nameRef}
      />
      {showEmail && (
        <TextField
          label="Email"
          name="email"
          value={profile.email}
          readOnly
          helper="Your email can't be changed yet."
        />
      )}
      <CurrencySelect
        label="Default currency"
        value={defaultCurrency}
        onChange={setDefaultCurrency}
        error={errors.defaultCurrency}
      />
      <TextField
        label="UPI ID (optional)"
        name="upiId"
        autoComplete="off"
        autoCapitalize="none"
        value={upiId}
        error={errors.upiId}
        helper="So friends can pay you with one tap. Looks like name@okbank."
        onChange={(e) => setUpiId(e.target.value)}
        inputRef={upiRef}
      />
      {showDirectAdd && (
        <div className="flex items-start gap-3">
          <input
            id="direct-add"
            type="checkbox"
            checked={allowDirect}
            aria-describedby="direct-add-help"
            onChange={(e) => setAllowDirect(e.target.checked)}
            className="mt-3 size-5"
          />
          <div className="flex min-h-11 flex-col justify-center">
            <label htmlFor="direct-add" className="font-semibold text-text">
              Let group Owners add me directly
            </label>
            <p id="direct-add-help" className="text-body-sm text-text-muted">
              Turn this off to join groups only through an invite link.
            </p>
          </div>
        </div>
      )}
      <div className="flex flex-col gap-2">
        <Button type="submit" size="lg" fullWidth loading={submitting}>
          {submitLabel}
        </Button>
        {secondary}
      </div>
    </form>
  );
}
