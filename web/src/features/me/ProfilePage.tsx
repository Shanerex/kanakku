import { CaretLeft } from "@phosphor-icons/react";
import { useState } from "react";
import { Link } from "react-router";
import { useSession } from "../../app/session";
import { ProfileForm } from "./ProfileForm";

/** Edit the profile; the form carries the version it started from, so a stale save is a 409. */
export default function ProfilePage() {
  const { state } = useSession();
  const [saved, setSaved] = useState(false);
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
      <h1 className="font-display text-h1 font-bold text-text">Profile</h1>
      {saved && (
        <p
          role="status"
          className="rounded-md bg-positive-soft p-3 text-body-sm text-positive-text"
        >
          Saved.
        </p>
      )}
      {/* key: after a save the form restarts from the server's profile and its new version */}
      <ProfileForm
        key={state.profile.version}
        profile={state.profile}
        showEmail
        submitLabel="Save"
        onSaved={() => setSaved(true)}
      />
    </main>
  );
}
