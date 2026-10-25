import { type FormEvent, useRef, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router";
import { newIdempotencyKey } from "../../api/client";
import { AuthLayout } from "../../app/AuthLayout";
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
import { PasswordField } from "../../ui/PasswordField";
import { TextField } from "../../ui/TextField";
import { safeReturnTo } from "./SignInPage";

const MIN = 8;
const MAX = 128;

type Fields = { displayName: string; email: string; password: string };
type Errors = Partial<Record<keyof Fields, string>>;

function length(text: string): number {
  return Array.from(text).length;
}

export function validate(fields: Fields): Errors {
  const errors: Errors = {};
  if (!fields.displayName.trim()) errors.displayName = "Enter your name.";
  else if (length(fields.displayName.trim()) > 80)
    errors.displayName = "Use 80 characters or fewer.";
  if (!/^[^\s@]+@[^\s@]+$/.test(fields.email.trim()))
    errors.email = "Enter an email like name@example.com.";
  const n = length(fields.password);
  if (n < MIN || n > MAX) errors.password = "Use 8 to 128 characters.";
  return errors;
}

const STRENGTH = [
  { label: "", segments: 0 },
  { label: "Too short", segments: 1 },
  { label: "Okay", segments: 2 },
  { label: "Strong", segments: 3 },
] as const;

function strengthOf(password: string) {
  const n = length(password);
  if (n === 0) return STRENGTH[0];
  if (n < MIN) return STRENGTH[1];
  if (n < 12) return STRENGTH[2];
  return STRENGTH[3];
}

function StrengthMeter({ password }: { password: string }) {
  const strength = strengthOf(password);
  return (
    <div className="flex items-center gap-2" data-testid="strength">
      <div aria-hidden="true" className="flex flex-1 gap-1">
        {[1, 2, 3].map((segment) => (
          <span
            key={segment}
            className={`h-1 flex-1 rounded-pill ${
              segment <= strength.segments ? "bg-primary" : "bg-border"
            }`}
          />
        ))}
      </div>
      <span className="min-w-16 text-caption font-bold text-text-muted">
        {strength.label && `Strength: ${strength.label}`}
      </span>
    </div>
  );
}

export default function SignUpPage() {
  const api = useApi();
  const session = useSession();
  const [params] = useSearchParams();
  const location = useLocation();
  // A visitor sent here from an invite link (returnTo plus the #token) goes back to it after
  // sign-up; anyone else lands on /welcome.
  const destination = params.has("returnTo")
    ? `${safeReturnTo(params.get("returnTo"))}${location.hash}`
    : "/welcome";
  const [fields, setFields] = useState<Fields>({
    displayName: "",
    email: "",
    password: "",
  });
  const [errors, setErrors] = useState<Errors>({});
  const [problem, setProblem] = useState<PlainProblem>();
  const [submitting, setSubmitting] = useState(false);
  const idempotencyKey = useRef(newIdempotencyKey());
  const refs = {
    displayName: useRef<HTMLInputElement>(null),
    email: useRef<HTMLInputElement>(null),
    password: useRef<HTMLInputElement>(null),
  };

  function change(name: keyof Fields, value: string) {
    setFields((current) => ({ ...current, [name]: value }));
    // The body changed, so this is a new submission: it gets a new key.
    idempotencyKey.current = newIdempotencyKey();
  }

  function focusFirst(found: Errors) {
    for (const name of ["displayName", "email", "password"] as const) {
      if (found[name]) {
        refs[name].current?.focus();
        return;
      }
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setProblem(undefined);
    const found = validate(fields);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      focusFirst(found);
      return;
    }
    setSubmitting(true);
    try {
      const body = {
        displayName: fields.displayName.trim(),
        email: fields.email.trim(),
        password: fields.password,
      };
      const created = await api.POST("/api/v1/auth/sign-up", {
        params: { header: { "Idempotency-Key": idempotencyKey.current } },
        body,
      });
      if (created.error) {
        const raw = asProblem(created.error);
        const serverErrors = fieldMessages(raw) as Errors;
        if (Object.keys(serverErrors).length > 0) {
          setErrors(serverErrors);
          focusFirst(serverErrors);
        }
        if (raw?.type?.endsWith(":email-taken"))
          setErrors({ email: "That email already has an account." });
        setProblem(describeProblem(raw));
        return;
      }
      // The account exists; this key is spent. A later attempt is a new submission.
      idempotencyKey.current = newIdempotencyKey();
      const signedIn = await api.POST("/api/v1/auth/sign-in", {
        body: { email: body.email, password: body.password },
      });
      if (signedIn.data) {
        session.signedIn(signedIn.data, destination);
      } else {
        setProblem({
          title: "Your account is ready",
          body: "We couldn't sign you in just now. Go to sign in and try again.",
        });
      }
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout>
      <h1 className="font-display text-h1 font-bold text-text">
        Create your account
      </h1>
      <form noValidate onSubmit={submit} className="flex flex-col gap-4">
        {problem && <InlineProblem problem={problem} />}
        <TextField
          label="Display name"
          name="displayName"
          autoComplete="name"
          value={fields.displayName}
          error={errors.displayName}
          onChange={(e) => change("displayName", e.target.value)}
          inputRef={refs.displayName}
        />
        <TextField
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          inputMode="email"
          value={fields.email}
          error={errors.email}
          onChange={(e) => change("email", e.target.value)}
          inputRef={refs.email}
        />
        <div className="flex flex-col gap-2">
          <PasswordField
            label="Password"
            name="password"
            autoComplete="new-password"
            value={fields.password}
            error={errors.password}
            helper="8 to 128 characters. A passphrase works well."
            onChange={(e) => change("password", e.target.value)}
            inputRef={refs.password}
          />
          <StrengthMeter password={fields.password} />
        </div>
        <Button type="submit" size="lg" fullWidth loading={submitting}>
          Create account
        </Button>
      </form>
      <p className="text-center text-body-sm">
        Already have an account?{" "}
        <Link
          to={{
            pathname: "/sign-in",
            search: location.search,
            hash: location.hash,
          }}
          className="font-semibold text-primary-text underline"
        >
          Sign in
        </Link>
      </p>
    </AuthLayout>
  );
}
