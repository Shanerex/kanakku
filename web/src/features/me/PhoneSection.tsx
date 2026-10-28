import { CheckCircle, WarningCircle } from "@phosphor-icons/react";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { newIdempotencyKey } from "../../api/client";
import { useApi } from "../../app/api-context";
import { InlineProblem } from "../../app/InlineProblem";
import {
  asProblem,
  describeNetworkError,
  describeProblem,
  type PlainProblem,
} from "../../app/problems";
import { useSession } from "../../app/session";
import { Button } from "../../ui/Button";
import { Card } from "../../ui/Card";
import { CodeField } from "../../ui/CodeField";
import { TextField } from "../../ui/TextField";
import { useCooldown } from "../../ui/useCooldown";

const RESEND_SECONDS = 30;

/** "+919876543210" as "+91 98765 43210"; other countries are shown as stored. */
export function formatPhone(e164: string): string {
  const india = /^\+91(\d{5})(\d{5})$/.exec(e164);
  return india ? `+91 ${india[1]} ${india[2]}` : e164;
}

/**
 * The one mobile number on my profile: add, replace or remove it, and confirm it with a texted
 * code when the server can send texts. A new number always starts unverified.
 */
export function PhoneSection() {
  const api = useApi();
  const session = useSession();
  const profile =
    session.state.status === "signed-in" ? session.state.profile : undefined;
  const [textsCodes, setTextsCodes] = useState<boolean>();
  const [editing, setEditing] = useState(false);
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [codeAsked, setCodeAsked] = useState(false);
  const [smsSent, setSmsSent] = useState(true);
  const [fieldError, setFieldError] = useState<string>();
  const [codeError, setCodeError] = useState<string>();
  const [problem, setProblem] = useState<PlainProblem>();
  const [conflict, setConflict] = useState(false);
  const [busy, setBusy] = useState<"save" | "send" | "confirm" | "remove">();
  const cooldown = useCooldown(RESEND_SECONDS);
  const phoneRef = useRef<HTMLInputElement>(null);
  const codeRef = useRef<HTMLInputElement>(null);

  // biome-ignore lint/correctness/useExhaustiveDependencies: asked once on arrival
  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const { data } = await api.GET("/api/v1/auth/sign-up-options");
        if (active) setTextsCodes(data?.phoneVerification ?? false);
      } catch {
        if (active) setTextsCodes(false);
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  if (!profile) return null;
  const number = profile.phone ?? undefined;
  const verified = profile.phoneVerified;

  function clearMessages() {
    setProblem(undefined);
    setFieldError(undefined);
    setCodeError(undefined);
    setConflict(false);
  }

  function fail(raw: ReturnType<typeof asProblem>, target: "phone" | "code") {
    const words = describeProblem(raw);
    const slug = raw?.type?.split(":").pop();
    setConflict(slug === "version-conflict");
    if (
      target === "phone" &&
      (slug === "invalid-phone" || slug === "phone-taken")
    ) {
      setFieldError(words.body);
      phoneRef.current?.focus();
    } else if (
      target === "code" &&
      (slug === "wrong-code" || slug === "code-expired")
    ) {
      setCodeError(words.body);
      codeRef.current?.focus();
      codeRef.current?.select();
    } else setProblem(words);
  }

  async function guard(
    action: "save" | "send" | "confirm" | "remove",
    call: () => Promise<void>,
  ) {
    if (busy) return;
    setBusy(action);
    clearMessages();
    try {
      await call();
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setBusy(undefined);
    }
  }

  function save(event: FormEvent) {
    event.preventDefault();
    if (!phone.trim()) {
      setFieldError("Enter your mobile number.");
      phoneRef.current?.focus();
      return;
    }
    return guard("save", async () => {
      const { data, error } = await api.PUT("/api/v1/me/phone", {
        body: { phone: phone.trim(), version: profile?.version ?? 0 },
      });
      if (data) {
        session.setProfile(data);
        setEditing(false);
        setPhone("");
        setCode("");
        setCodeAsked(false);
        cooldown.start();
      } else fail(asProblem(error), "phone");
    });
  }

  const remove = () =>
    guard("remove", async () => {
      const { data, error } = await api.DELETE("/api/v1/me/phone", {
        params: { query: { version: profile?.version ?? 0 } },
      });
      if (data) {
        session.setProfile(data);
        setEditing(false);
        setCode("");
        setCodeAsked(false);
      } else fail(asProblem(error), "phone");
    });

  const sendCode = () =>
    guard("send", async () => {
      const { data, error } = await api.POST("/api/v1/me/phone/verification", {
        params: { header: { "Idempotency-Key": newIdempotencyKey() } },
      });
      if (data) {
        setCodeAsked(true);
        setSmsSent(data.smsSent);
        setCode("");
        cooldown.start();
      } else fail(asProblem(error), "code");
    });

  const confirm = (event: FormEvent) => {
    event.preventDefault();
    if (code.length !== 6) return;
    return guard("confirm", async () => {
      const { data, error } = await api.POST(
        "/api/v1/me/phone/verification/confirm",
        {
          body: { code },
        },
      );
      if (data) {
        session.setProfile(data);
        setCodeAsked(false);
        setCode("");
      } else fail(asProblem(error), "code");
    });
  };

  async function reloadLatest() {
    const { data } = await api.GET("/api/v1/me");
    if (data) {
      session.setProfile(data);
      clearMessages();
    }
  }

  const showForm = editing || !number;
  const chip = verified ? (
    <span className="inline-flex items-center gap-1 rounded-pill bg-positive-soft px-3 py-1 text-caption font-bold text-positive-text">
      <CheckCircle size={16} weight="fill" aria-hidden="true" />
      Verified
    </span>
  ) : (
    <span className="inline-flex items-center gap-1 rounded-pill bg-info-soft px-3 py-1 text-caption font-bold text-info-text">
      <WarningCircle size={16} weight="fill" aria-hidden="true" />
      Unverified
    </span>
  );

  return (
    <Card
      as="section"
      aria-labelledby="phone-title"
      className="flex flex-col gap-3"
    >
      <h2 id="phone-title" className="font-display text-h2 font-bold text-text">
        Mobile number
      </h2>
      {problem && <InlineProblem problem={problem} />}
      {conflict && (
        <Button variant="secondary" onClick={reloadLatest}>
          Load the latest version
        </Button>
      )}

      {showForm ? (
        <form noValidate onSubmit={save} className="flex flex-col gap-3">
          {!number && (
            <p className="text-text-muted">
              Add your number so friends can find you.
            </p>
          )}
          <TextField
            label="Mobile number"
            name="phone"
            type="tel"
            autoComplete="tel"
            inputMode="tel"
            value={phone}
            error={fieldError}
            helper="Starts with +91 unless you type another country code."
            onChange={(e) => setPhone(e.target.value)}
            inputRef={phoneRef}
          />
          <div className="flex gap-2">
            <Button type="submit" variant="secondary" loading={busy === "save"}>
              Save number
            </Button>
            {number && (
              <Button
                variant="ghost"
                onClick={() => {
                  setEditing(false);
                  clearMessages();
                }}
              >
                Cancel
              </Button>
            )}
          </div>
        </form>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-3">
            <p className="font-semibold tabular-nums text-text">
              {formatPhone(number ?? "")}
            </p>
            {chip}
          </div>
          {!verified && textsCodes === false && (
            <p className="text-body-sm text-text-muted">
              This server can't send texts, so your number stays unverified.
            </p>
          )}
          {!verified && textsCodes && codeAsked && (
            <form noValidate onSubmit={confirm} className="flex flex-col gap-3">
              {smsSent ? (
                <p className="text-text-muted">
                  We texted a 6-digit code to {formatPhone(number ?? "")}.
                </p>
              ) : (
                <InlineProblem
                  problem={{
                    title: "We couldn't send the text",
                    body: "Try again in a moment.",
                  }}
                />
              )}
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
                loading={busy === "confirm"}
                disabled={code.length !== 6}
              >
                Confirm number
              </Button>
            </form>
          )}
          <div className="flex flex-wrap gap-2">
            {!verified && textsCodes && !codeAsked && (
              <Button
                variant="secondary"
                loading={busy === "send"}
                onClick={sendCode}
              >
                Send code
              </Button>
            )}
            {!verified && textsCodes && codeAsked && (
              <Button
                variant="ghost"
                loading={busy === "send"}
                disabled={cooldown.left > 0}
                onClick={sendCode}
              >
                {cooldown.left > 0
                  ? `Resend in ${cooldown.left} s`
                  : "Resend code"}
              </Button>
            )}
            <Button
              variant="ghost"
              onClick={() => {
                setEditing(true);
                clearMessages();
              }}
            >
              Change number
            </Button>
            <Button
              variant="ghost"
              loading={busy === "remove"}
              onClick={remove}
            >
              Remove number
            </Button>
          </div>
        </>
      )}
    </Card>
  );
}
