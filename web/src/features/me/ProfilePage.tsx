import { useState } from "react";
import { usePageTitle } from "../../app/AppShell";
import { useSession } from "../../app/session";
import { Card } from "../../ui/Card";
import { PhoneSection } from "./PhoneSection";
import { ProfileForm } from "./ProfileForm";

/** Edit the profile; the form carries the version it started from, so a stale save is a 409. */
export default function ProfilePage() {
  const { state } = useSession();
  const [saved, setSaved] = useState(false);
  const [saves, setSaves] = useState(0);
  usePageTitle("Profile", { backTo: "/me", backLabel: "Me" });
  if (state.status !== "signed-in") return null;
  return (
    <div className="flex w-full max-w-[560px] flex-col gap-4">
      {saved && (
        <p
          role="status"
          className="rounded-md bg-positive-soft p-3 text-body-sm text-positive-text"
        >
          Saved.
        </p>
      )}
      {/* key: after a save the form restarts from the server's profile and its new version. It
          counts the form's own saves, so saving the phone number below keeps unsaved edits. */}
      <Card as="section" aria-label="Profile details">
        <ProfileForm
          key={saves}
          profile={state.profile}
          showEmail
          showDirectAdd
          submitLabel="Save"
          onSaved={() => {
            setSaved(true);
            setSaves((n) => n + 1);
          }}
        />
      </Card>
      <PhoneSection />
    </div>
  );
}
