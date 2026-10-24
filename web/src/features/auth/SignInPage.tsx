import { type FormEvent, useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { AuthLayout } from "../../app/AuthLayout";
import { useApi } from "../../app/api-context";
import { InlineProblem } from "../../app/InlineProblem";
import {
  asProblem,
  describeNetworkError,
  describeProblem,
  formatWait,
  type PlainProblem,
} from "../../app/problems";
import { useSession } from "../../app/session";
import { Button } from "../../ui/Button";
import { PasswordField } from "../../ui/PasswordField";
import { TextField } from "../../ui/TextField";

const HOME = "/me";

/** Only same-site paths are allowed as a return target, so sign-in can't be used to bounce away. */
export function safeReturnTo(value: string | null): string {
  if (
    !value?.startsWith("/") ||
    value.startsWith("//") ||
    value.includes("\\") ||
    // biome-ignore lint/suspicious/noControlCharactersInRegex: rejecting them is the point
    /[\u0000-\u001f\u007f]/.test(value)
  )
    return HOME;
  return value;
}

/** Seconds left until `until` (ms since epoch), ticking once a second; 0 when done. */
function useSecondsLeft(until: number | undefined): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (until === undefined) return;
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [until]);
  return until === undefined ? 0 : Math.max(0, Math.ceil((until - now) / 1000));
}

export default function SignInPage() {
  const api = useApi();
  const session = useSession();
  const [params] = useSearchParams();
  const returnTo = safeReturnTo(params.get("returnTo"));
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<{ email?: string; password?: string }>(
    {},
  );
  const [problem, setProblem] = useState<PlainProblem>();
  const [submitting, setSubmitting] = useState(false);
  const [lockedUntil, setLockedUntil] = useState<number>();
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const secondsLeft = useSecondsLeft(lockedUntil);
  const locked = lockedUntil !== undefined && secondsLeft > 0;

  useEffect(() => {
    if (lockedUntil !== undefined && secondsLeft === 0) {
      setLockedUntil(undefined);
      setProblem(undefined);
    }
  }, [lockedUntil, secondsLeft]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (submitting || locked) return;
    setProblem(undefined);
    const found: typeof errors = {};
    if (!email.trim()) found.email = "Enter your email.";
    if (!password) found.password = "Enter your password.";
    setErrors(found);
    if (found.email) return emailRef.current?.focus();
    if (found.password) return passwordRef.current?.focus();

    setSubmitting(true);
    try {
      const { data, error } = await api.POST("/api/v1/auth/sign-in", {
        body: { email: email.trim(), password },
      });
      if (data) {
        session.signedIn(data, returnTo);
        return;
      }
      const raw = asProblem(error);
      if (raw?.type?.endsWith(":too-many-attempts") && raw.retryAfterSeconds) {
        setLockedUntil(Date.now() + raw.retryAfterSeconds * 1000);
      }
      setProblem(describeProblem(raw));
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout>
      <h1 className="font-display text-h1 font-bold text-text">Welcome back</h1>
      <form noValidate onSubmit={submit} className="flex flex-col gap-4">
        {locked ? (
          <InlineProblem
            problem={{
              title: "Too many tries",
              body: `Try again in ${formatWait(secondsLeft)}.`,
            }}
          />
        ) : (
          problem && <InlineProblem problem={problem} />
        )}
        <TextField
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          inputMode="email"
          value={email}
          error={errors.email}
          onChange={(e) => setEmail(e.target.value)}
          inputRef={emailRef}
        />
        <PasswordField
          label="Password"
          name="password"
          autoComplete="current-password"
          value={password}
          error={errors.password}
          onChange={(e) => setPassword(e.target.value)}
          inputRef={passwordRef}
        />
        <Button
          type="submit"
          size="lg"
          fullWidth
          loading={submitting}
          disabled={locked}
        >
          Sign in
        </Button>
      </form>
      <p className="text-center text-body-sm">
        New here?{" "}
        <Link
          to="/sign-up"
          className="font-semibold text-primary-text underline"
        >
          Create an account
        </Link>
      </p>
    </AuthLayout>
  );
}
