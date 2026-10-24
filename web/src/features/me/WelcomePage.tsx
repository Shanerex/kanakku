import { useState } from "react";
import { useNavigate } from "react-router";
import { AuthLayout } from "../../app/AuthLayout";
import { type SessionState, useSession } from "../../app/session";
import { Avatar } from "../../ui/Avatar";
import { Button } from "../../ui/Button";
import { ProfileForm } from "./ProfileForm";

function signedInProfile(state: SessionState) {
  return state.status === "signed-in" ? state.profile : undefined;
}

/** First-run profile: name, default currency (INR preselected) and an optional UPI ID. */
export default function WelcomePage() {
  const { state } = useSession();
  const navigate = useNavigate();
  const profile = signedInProfile(state);
  const [name, setName] = useState(profile?.displayName ?? "");
  if (!profile) return null;
  const home = () => navigate("/me", { replace: true });
  return (
    <AuthLayout>
      <div className="flex items-center gap-3">
        <Avatar id={profile.id} name={name.trim() || profile.email} size="lg" />
        <h1 className="font-display text-h1 font-bold text-text">
          Welcome to Kanakku
        </h1>
      </div>
      <ProfileForm
        profile={profile}
        submitLabel="Let's go"
        onNameChange={setName}
        onSaved={home}
        secondary={
          <Button variant="ghost" fullWidth onClick={home}>
            Skip for now
          </Button>
        }
      />
    </AuthLayout>
  );
}
