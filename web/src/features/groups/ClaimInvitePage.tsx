import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { usePageTitle } from "../../app/AppShell";
import { useApi } from "../../app/api-context";
import { InlineProblem } from "../../app/InlineProblem";
import {
  asProblem,
  describeNetworkError,
  describeProblem,
  type PlainProblem,
} from "../../app/problems";
import { Button } from "../../ui/Button";

type Preview = { groupName: string; guestName: string };

/**
 * `/invite#<token>`: shows which group and guest the link is for, then lets the signed-in person
 * take that guest's place. The token lives in the URL fragment and goes to the server only in a
 * request body.
 */
export default function ClaimInvitePage() {
  const api = useApi();
  const navigate = useNavigate();
  const token = useLocation().hash.slice(1);
  const [preview, setPreview] = useState<Preview>();
  const [problem, setProblem] = useState<PlainProblem>();
  const [dead, setDead] = useState(false);
  const [busy, setBusy] = useState(false);
  const loaded = useRef<string>(undefined);
  usePageTitle(
    !token
      ? "No invite here"
      : preview
        ? `Join ${preview.groupName}`
        : "Invite",
  );

  useEffect(() => {
    if (!token || loaded.current === token) return;
    loaded.current = token;
    (async () => {
      try {
        const { data, error, response } = await api.POST(
          "/api/v1/invites/preview",
          {
            body: { token },
          },
        );
        if (data) setPreview(data);
        else {
          setDead(response.status === 410);
          setProblem(describeProblem(asProblem(error)));
        }
      } catch {
        setProblem(describeNetworkError());
      }
    })();
  }, [api, token]);

  async function claim() {
    if (busy) return;
    setBusy(true);
    setProblem(undefined);
    try {
      const { data, error } = await api.POST("/api/v1/invites/claim", {
        body: { token },
      });
      if (data) navigate(`/groups/${data.id}`);
      else setProblem(describeProblem(asProblem(error)));
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto flex w-full max-w-[400px] flex-col gap-4 pt-6">
      {!token && (
        <p className="text-text-muted">
          Open the full invite link you were sent.
        </p>
      )}
      {token && !preview && !problem && (
        <p role="status" className="text-text-muted">
          Checking your invite...
        </p>
      )}
      {problem && <InlineProblem problem={problem} />}
      {preview && (
        <>
          <p className="text-text-muted">
            You'll take over {preview.guestName}'s place: their expenses and
            balance become yours.
          </p>
          <Button size="lg" fullWidth loading={busy} onClick={claim}>
            Join {preview.groupName}
          </Button>
        </>
      )}
      {(dead || !token) && (
        <Link to="/groups" className="text-primary-text underline">
          Go to your groups
        </Link>
      )}
    </div>
  );
}
