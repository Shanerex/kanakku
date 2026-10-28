import {
  CaretRight,
  CircleNotch,
  SignOut,
  UserCircle,
} from "@phosphor-icons/react";
import { useState } from "react";
import { Link } from "react-router";
import { usePageTitle } from "../../app/AppShell";
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
import { Card } from "../../ui/Card";

/** Me: who you are, a Profile row and Sign out. Other rows arrive with their features. */
export default function MePage() {
  const api = useApi();
  const { state, clear } = useSession();
  const [problem, setProblem] = useState<PlainProblem>();
  const [busy, setBusy] = useState(false);
  usePageTitle("Me");
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
    <div className="flex w-full flex-col gap-4">
      <Card as="header" className="flex items-center gap-4">
        <Avatar id={profile.id} name={profile.displayName} size="xl" />
        <div className="min-w-0">
          <h2 className="truncate font-display text-h2 font-bold text-text">
            {profile.displayName}
          </h2>
          <p className="truncate text-text-muted">{profile.email}</p>
          <Link
            to="/me/profile"
            className="text-body-sm font-semibold text-primary-text underline"
          >
            Edit profile
          </Link>
        </div>
      </Card>
      <Card as="section" flush aria-label="Settings">
        <Link
          to="/me/profile"
          className="flex min-h-16 items-center gap-3 px-4 py-2 hover:bg-surface-sunken"
        >
          <span
            aria-hidden="true"
            className="inline-flex size-10 shrink-0 items-center justify-center rounded-md bg-primary-soft text-primary-text"
          >
            <UserCircle size={22} />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block font-bold text-text">Profile</span>
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
      </Card>
      {problem && <InlineProblem problem={problem} />}
      <Card as="section" flush aria-label="Account">
        <button
          type="button"
          aria-busy={busy || undefined}
          onClick={busy ? undefined : signOut}
          className="flex min-h-16 w-full items-center gap-3 px-4 py-2 text-left hover:bg-surface-sunken"
        >
          <span
            aria-hidden="true"
            className="inline-flex size-10 shrink-0 items-center justify-center rounded-md bg-negative-soft text-negative-text"
          >
            {busy ? (
              <CircleNotch size={22} className="animate-spin" />
            ) : (
              <SignOut size={22} />
            )}
          </span>
          <span className="font-semibold text-text">Sign out</span>
        </button>
      </Card>
    </div>
  );
}
