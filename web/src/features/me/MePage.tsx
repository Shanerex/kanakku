import { CaretRight } from "@phosphor-icons/react";
import { useState } from "react";
import { Link } from "react-router";
import { useApi } from "../../app/api-context";
import { InlineProblem } from "../../app/InlineProblem";
import {
  asProblem,
  describeNetworkError,
  describeProblem,
  type PlainProblem,
} from "../../app/problems";
import { useSession } from "../../app/session";
import { Avatar } from "../../ui/Avatar";
import { Button } from "../../ui/Button";

/** Me: who you are, a Profile row and Sign out. Other rows arrive with their features. */
export default function MePage() {
  const api = useApi();
  const { state, clear } = useSession();
  const [problem, setProblem] = useState<PlainProblem>();
  const [busy, setBusy] = useState(false);
  if (state.status !== "signed-in") return null;
  const { profile } = state;

  async function signOut() {
    setBusy(true);
    setProblem(undefined);
    try {
      const { error, response } = await api.POST("/api/v1/auth/sign-out");
      if (response.ok) {
        clear("/sign-in");
      } else {
        setProblem(describeProblem(asProblem(error)));
      }
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto flex max-w-[560px] flex-col gap-6 px-4 py-6">
      <header className="flex items-center gap-4">
        <Avatar id={profile.id} name={profile.displayName} size="xl" />
        <div className="min-w-0">
          <h1 className="truncate font-display text-h1 font-bold text-text">
            {profile.displayName}
          </h1>
          <p className="truncate text-text-muted">{profile.email}</p>
          <Link
            to="/me/profile"
            className="text-body-sm font-semibold text-primary-text underline"
          >
            Edit profile
          </Link>
        </div>
      </header>
      <section
        aria-label="Settings"
        className="rounded-lg bg-surface shadow-sm"
      >
        <Link
          to="/me/profile"
          className="flex min-h-16 items-center justify-between gap-3 rounded-lg px-4"
        >
          <span>
            <span className="block font-semibold text-text">Profile</span>
            <span className="block text-body-sm text-text-muted">
              Name, default currency, UPI ID, who can add you
            </span>
          </span>
          <CaretRight
            size={20}
            aria-hidden="true"
            className="text-text-muted"
          />
        </Link>
        <Link
          to="/groups"
          className="flex min-h-16 items-center justify-between gap-3 rounded-lg px-4"
        >
          <span>
            <span className="block font-semibold text-text">Groups</span>
            <span className="block text-body-sm text-text-muted">
              Trips, flats and couples you share costs with
            </span>
          </span>
          <CaretRight
            size={20}
            aria-hidden="true"
            className="text-text-muted"
          />
        </Link>
      </section>
      {problem && <InlineProblem problem={problem} />}
      <Button
        variant="secondary"
        size="lg"
        fullWidth
        loading={busy}
        onClick={signOut}
      >
        Sign out
      </Button>
    </main>
  );
}
