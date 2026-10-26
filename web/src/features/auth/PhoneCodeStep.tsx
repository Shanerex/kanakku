import { useEffect, useRef, useState } from "react";
import { newIdempotencyKey } from "../../api/client";
import type { components } from "../../api/schema";
import { useApi } from "../../app/api-context";
import { InlineProblem } from "../../app/InlineProblem";
import {
  asProblem,
  describeNetworkError,
  describeProblem,
  type PlainProblem,
} from "../../app/problems";
import { Button } from "../../ui/Button";
import { CodeField } from "../../ui/CodeField";
import { useCooldown } from "../../ui/useCooldown";

export type Pending = components["schemas"]["PendingSignUp"];

const RESEND_SECONDS = 30;

/**
 * The second step of sign-up when the server texts codes: no account exists until the code is
 * confirmed or the person finishes without it, which they may do at any time (a text can be
 * accepted by the provider and still never arrive). Every verify try gets a fresh
 * Idempotency-Key, because a wrong answer is stored under the key it was sent with.
 */
export function PhoneCodeStep({
  pending,
  onPending,
  onCreated,
  onBack,
  onExpired,
}: {
  pending: Pending;
  /** The latest pending sign-up, after a resend. */
  onPending: (pending: Pending) => void;
  /** The account exists; sign the person in. */
  onCreated: () => Promise<void>;
  /** Back to the form with everything kept. */
  onBack: () => void;
  /** The sign-up is gone: back to the form with a note. */
  onExpired: (problem: PlainProblem) => void;
}) {
  const api = useApi();
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState<string>();
  const [problem, setProblem] = useState<PlainProblem>();
  const [notice, setNotice] = useState<string>();
  const [locked, setLocked] = useState(false);
  const [busy, setBusy] = useState<"verify" | "resend" | "finish">();
  const cooldown = useCooldown(RESEND_SECONDS);
  const codeRef = useRef<HTMLInputElement>(null);
  const finishKey = useRef(newIdempotencyKey());
  const path = { id: pending.id };

  // biome-ignore lint/correctness/useExhaustiveDependencies: a code was just sent, so the wait starts once
  useEffect(() => {
    cooldown.start();
  }, []);

  function handleProblem(raw: ReturnType<typeof asProblem>) {
    const slug = raw?.type?.split(":").pop();
    const words = describeProblem(raw);
    switch (slug) {
      case "wrong-code":
      case "code-expired":
        setCodeError(words.body);
        codeRef.current?.focus();
        codeRef.current?.select();
        return;
      case "code-locked":
        setLocked(true);
        break;
      case "sign-up-expired":
      case "email-taken":
      case "phone-taken":
        onExpired(words);
        return;
    }
    setProblem(words);
  }

  async function run(
    action: "verify" | "resend" | "finish",
    call: () => Promise<void>,
  ) {
    if (busy) return;
    setBusy(action);
    setProblem(undefined);
    setCodeError(undefined);
    setNotice(undefined);
    try {
      await call();
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setBusy(undefined);
    }
  }

  const verify = () =>
    run("verify", async () => {
      const { data, error } = await api.POST(
        "/api/v1/auth/sign-ups/{id}/verify",
        {
          params: {
            path,
            header: { "Idempotency-Key": newIdempotencyKey() },
          },
          body: { code },
        },
      );
      if (data) await onCreated();
      else handleProblem(asProblem(error));
    });

  const resend = () =>
    run("resend", async () => {
      const { data, error } = await api.POST(
        "/api/v1/auth/sign-ups/{id}/resend",
        {
          params: {
            path,
            header: { "Idempotency-Key": newIdempotencyKey() },
          },
        },
      );
      if (data) {
        onPending(data);
        setCode("");
        setLocked(false);
        cooldown.start();
        setNotice(
          data.smsSent
            ? "New code sent. The old one no longer works."
            : undefined,
        );
      } else handleProblem(asProblem(error));
    });

  const finish = () =>
    run("finish", async () => {
      const { data, error } = await api.POST(
        "/api/v1/auth/sign-ups/{id}/finish-unverified",
        { params: { path, header: { "Idempotency-Key": finishKey.current } } },
      );
      if (data) {
        finishKey.current = newIdempotencyKey();
        await onCreated();
      } else handleProblem(asProblem(error));
    });

  return (
    <>
      <h1 className="font-display text-h1 font-bold text-text">
        Enter your code
      </h1>
      {pending.smsSent ? (
        <p className="text-text-muted">
          We texted a 6-digit code to{" "}
          <span className="font-semibold text-text">{pending.maskedPhone}</span>
          .
        </p>
      ) : (
        <InlineProblem
          problem={{
            title: "We couldn't send the text",
            body: "Try again, or finish now and verify your number later.",
          }}
        />
      )}
      {problem && <InlineProblem problem={problem} />}
      {notice && (
        <p
          role="status"
          className="rounded-md bg-positive-soft p-3 text-body-sm text-positive-text"
        >
          {notice}
        </p>
      )}
      <form
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          if (code.length === 6 && !locked) verify();
        }}
        className="flex flex-col gap-4"
      >
        <CodeField
          value={code}
          onChange={(digits) => {
            setCode(digits);
            setCodeError(undefined);
          }}
          error={codeError}
          helper="It works for 10 minutes."
          inputRef={codeRef}
        />
        <Button
          type="submit"
          size="lg"
          fullWidth
          loading={busy === "verify"}
          disabled={code.length !== 6 || locked}
        >
          Create account
        </Button>
      </form>
      <div className="flex flex-col gap-1">
        <Button
          variant="ghost"
          loading={busy === "resend"}
          disabled={cooldown.left > 0}
          onClick={resend}
        >
          {cooldown.left > 0 ? `Resend in ${cooldown.left} s` : "Resend code"}
        </Button>
        <Button
          variant={pending.smsSent ? "ghost" : "secondary"}
          loading={busy === "finish"}
          onClick={finish}
        >
          Finish without verifying
        </Button>
        <Button variant="ghost" onClick={onBack}>
          Use a different number
        </Button>
      </div>
    </>
  );
}
