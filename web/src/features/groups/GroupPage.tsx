import { CaretLeft, Info } from "@phosphor-icons/react";
import {
  type FormEvent,
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";
import { Link, useNavigate, useParams } from "react-router";
import { newIdempotencyKey } from "../../api/client";
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
import { Avatar } from "../../ui/Avatar";
import { Button } from "../../ui/Button";
import { TextField } from "../../ui/TextField";
import { type Cover, coverValue, GROUP_TYPES, parseCover } from "./cover";
import { Dialog } from "./Dialog";
import { GroupBadge } from "./GroupBadge";
import { CoverPicker } from "./GroupsPage";
import { InvitesPanel } from "./InvitesPanel";
import { MemberPicker } from "./MemberPicker";
import { balanceWords, formatMoney } from "./money";
import type { Group, Invite, Member, MoneyBalance } from "./types";
import { WriteOffDialog } from "./WriteOffDialog";

type Confirm =
  | { kind: "archive" }
  | { kind: "unarchive" }
  | { kind: "leave" }
  | { kind: "edit" }
  | { kind: "remove"; member: Member };

/** A group: its members with balances, adding people, invites, archive and leave. */
export default function GroupPage() {
  const { groupId = "" } = useParams();
  const api = useApi();
  const navigate = useNavigate();
  const { state } = useSession();
  const [group, setGroup] = useState<Group>();
  const [members, setMembers] = useState<Member[]>();
  const [invites, setInvites] = useState<Invite[]>([]);
  const [problem, setProblem] = useState<PlainProblem>();
  const [owed, setOwed] = useState<{
    member: Member;
    balances: MoneyBalance[];
  }>();
  const [writeOff, setWriteOff] = useState<Member>();
  const [phoneFor, setPhoneFor] = useState<Member>();
  const [confirm, setConfirm] = useState<Confirm>();
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [g, m] = await Promise.all([
        api.GET("/api/v1/groups/{groupId}", { params: { path: { groupId } } }),
        api.GET("/api/v1/groups/{groupId}/members", {
          params: { path: { groupId } },
        }),
      ]);
      if (g.data && m.data) {
        setGroup(g.data);
        setMembers(m.data);
        setProblem(undefined);
        if (g.data.myRole === "OWNER") {
          const i = await api.GET("/api/v1/groups/{groupId}/invites", {
            params: { path: { groupId } },
          });
          if (i.data) setInvites(i.data);
        }
      } else {
        setProblem(describeProblem(asProblem(g.error ?? m.error)));
      }
    } catch {
      setProblem(describeNetworkError());
    }
  }, [api, groupId]);

  useEffect(() => {
    load();
  }, [load]);

  if (state.status !== "signed-in") return null;
  const me = state.profile.id;

  async function run(action: () => Promise<PlainProblem | undefined>) {
    if (busy) return;
    setBusy(true);
    setProblem(undefined);
    try {
      const failed = await action();
      if (failed) setProblem(failed);
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setBusy(false);
    }
  }

  async function setArchived(archive: boolean) {
    if (!group) return;
    setConfirm(undefined);
    await run(async () => {
      const path = archive
        ? "/api/v1/groups/{groupId}/archive"
        : "/api/v1/groups/{groupId}/unarchive";
      const { data, error } = await api.POST(path, {
        params: { path: { groupId } },
        body: { version: group.version },
      });
      if (data) {
        setGroup(data);
        return undefined;
      }
      return describeProblem(asProblem(error));
    });
  }

  async function leave() {
    setConfirm(undefined);
    await run(async () => {
      const { response, error } = await api.POST(
        "/api/v1/groups/{groupId}/leave",
        { params: { path: { groupId } } },
      );
      if (response.ok) {
        navigate("/groups");
        return undefined;
      }
      return describeProblem(asProblem(error));
    });
  }

  async function remove(member: Member) {
    setConfirm(undefined);
    setOwed(undefined);
    await run(async () => {
      const { response, error } = await api.DELETE(
        "/api/v1/groups/{groupId}/members/{memberId}",
        {
          params: {
            path: { groupId, memberId: member.id },
            query: { version: member.version },
          },
        },
      );
      if (response.ok) {
        await load();
        return undefined;
      }
      const raw = asProblem(error);
      if (raw?.type?.endsWith(":member-has-balance")) {
        const balances =
          (raw as { balances?: MoneyBalance[] }).balances ?? member.balances;
        setOwed({ member, balances });
        return undefined;
      }
      return describeProblem(raw);
    });
  }

  if (!group || !members) {
    return (
      <main className="mx-auto flex max-w-[560px] flex-col gap-4 px-4 py-6">
        <Link
          to="/groups"
          className="inline-flex items-center gap-1 text-primary-text"
        >
          <CaretLeft size={16} aria-hidden="true" />
          Groups
        </Link>
        {problem ? (
          <InlineProblem problem={problem} />
        ) : (
          <p role="status" className="text-text-muted">
            Loading...
          </p>
        )}
      </main>
    );
  }

  const isOwner = group.myRole === "OWNER";
  const archived = Boolean(group.archivedAt);
  const guests = members.filter((m) => m.guest);

  return (
    <main className="mx-auto flex max-w-[560px] flex-col gap-4 px-4 py-6">
      <Link
        to="/groups"
        className="inline-flex items-center gap-1 text-primary-text"
      >
        <CaretLeft size={16} aria-hidden="true" />
        Groups
      </Link>
      <header className="flex items-center gap-3">
        <GroupBadge
          id={group.id}
          name={group.name}
          cover={group.cover}
          size="lg"
        />
        <div className="min-w-0">
          <h1 className="truncate font-display text-h1 font-bold text-text">
            {group.name}
          </h1>
          <p className="text-body-sm text-text-muted">
            {group.currency} · {members.length}{" "}
            {members.length === 1 ? "person" : "people"}
          </p>
        </div>
      </header>

      {archived && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-md bg-info-soft p-3 text-body-sm text-info-text"
        >
          <Info size={20} aria-hidden="true" className="mt-0.5 shrink-0" />
          This group is archived. It is read-only, except that settlements still
          work.
        </p>
      )}
      {problem && <InlineProblem problem={problem} />}
      {owed && (
        <div
          role="alert"
          className="flex flex-col gap-2 rounded-md bg-danger-soft p-3 text-body-sm text-danger-text"
        >
          <p>
            <strong className="block font-bold">
              {owed.member.displayName} still has a balance
            </strong>
            {owed.balances
              .map(
                (b) =>
                  `${owed.member.displayName} ${balanceWords(b.amountMinor, b.currency)}`,
              )
              .join("; ")}
            . Settle it first{isOwner ? ", or write it off." : "."}
          </p>
          {isOwner && (
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                setWriteOff(owed.member);
                setOwed(undefined);
              }}
            >
              Write off {owed.member.displayName}'s balance
            </Button>
          )}
        </div>
      )}

      <section
        aria-labelledby="members-title"
        className="flex flex-col rounded-lg bg-surface shadow-sm"
      >
        <h2
          id="members-title"
          className="px-4 pt-4 font-display text-h2 font-bold text-text"
        >
          Members
        </h2>
        <ul className="flex flex-col">
          {members.map((member) => (
            <MemberRow
              key={member.id}
              member={member}
              isMe={member.userId === me}
              canManage={isOwner && !archived}
              onRemove={() => setConfirm({ kind: "remove", member })}
              onWriteOff={() => setWriteOff(member)}
              onPhone={() => setPhoneFor(member)}
            />
          ))}
        </ul>
      </section>

      {!archived && <AddGuestForm groupId={groupId} onAdded={load} />}
      {isOwner && !archived && (
        <MemberPicker groupId={groupId} onAdded={load} />
      )}
      {isOwner && !archived && (
        <InvitesPanel
          groupId={groupId}
          guests={guests}
          invites={invites}
          onChanged={load}
        />
      )}

      <section className="flex flex-col gap-2">
        {isOwner && !archived && (
          <Button
            variant="secondary"
            onClick={() => setConfirm({ kind: "edit" })}
          >
            Edit group
          </Button>
        )}
        {isOwner && !archived && (
          <Button
            variant="secondary"
            onClick={() => setConfirm({ kind: "archive" })}
          >
            Archive group
          </Button>
        )}
        {isOwner && archived && (
          <Button
            variant="secondary"
            onClick={() => setConfirm({ kind: "unarchive" })}
          >
            Unarchive group
          </Button>
        )}
        {!isOwner && !archived && (
          <Button
            variant="secondary"
            onClick={() => setConfirm({ kind: "leave" })}
          >
            Leave group
          </Button>
        )}
      </section>

      {confirm?.kind === "edit" && (
        <EditGroupDialog
          group={group}
          onClose={() => setConfirm(undefined)}
          onSaved={(saved) => {
            setGroup(saved);
            setConfirm(undefined);
          }}
        />
      )}
      {confirm?.kind === "archive" && (
        <Dialog
          title={`Archive ${group.name}?`}
          onClose={() => setConfirm(undefined)}
        >
          <p className="text-text-muted">
            It becomes read-only: no new people or invites. Settlements still
            work, and you can unarchive it any time.
          </p>
          <div className="flex gap-2">
            <Button loading={busy} onClick={() => setArchived(true)}>
              Archive
            </Button>
            <Button variant="ghost" onClick={() => setConfirm(undefined)}>
              Cancel
            </Button>
          </div>
        </Dialog>
      )}
      {confirm?.kind === "unarchive" && (
        <Dialog
          title={`Unarchive ${group.name}?`}
          onClose={() => setConfirm(undefined)}
        >
          <p className="text-text-muted">The group can be changed again.</p>
          <div className="flex gap-2">
            <Button loading={busy} onClick={() => setArchived(false)}>
              Unarchive
            </Button>
            <Button variant="ghost" onClick={() => setConfirm(undefined)}>
              Cancel
            </Button>
          </div>
        </Dialog>
      )}
      {confirm?.kind === "leave" && (
        <Dialog
          title={`Leave ${group.name}?`}
          onClose={() => setConfirm(undefined)}
        >
          <p className="text-text-muted">
            You can leave when your balance is settled. To come back you need an
            invite or the Owner has to add you.
          </p>
          <div className="flex gap-2">
            <Button variant="danger" loading={busy} onClick={leave}>
              Leave
            </Button>
            <Button variant="ghost" onClick={() => setConfirm(undefined)}>
              Cancel
            </Button>
          </div>
        </Dialog>
      )}
      {confirm?.kind === "remove" && (
        <Dialog
          title={`Remove ${confirm.member.displayName}?`}
          onClose={() => setConfirm(undefined)}
        >
          <p className="text-text-muted">
            They leave the group. Their past expenses stay in its history.
          </p>
          <div className="flex gap-2">
            <Button
              variant="danger"
              loading={busy}
              onClick={() => remove(confirm.member)}
            >
              Remove
            </Button>
            <Button variant="ghost" onClick={() => setConfirm(undefined)}>
              Cancel
            </Button>
          </div>
        </Dialog>
      )}
      {phoneFor && (
        <GuestPhoneDialog
          groupId={groupId}
          guest={phoneFor}
          onClose={() => setPhoneFor(undefined)}
          onSaved={() => {
            setPhoneFor(undefined);
            load();
          }}
        />
      )}
      {writeOff && (
        <WriteOffDialog
          groupId={groupId}
          target={writeOff}
          members={members}
          onClose={() => setWriteOff(undefined)}
          onDone={() => {
            setWriteOff(undefined);
            load();
          }}
        />
      )}
    </main>
  );
}

/** Owner-only: rename the group, change its type or cover. Sends the version it loaded. */
function EditGroupDialog({
  group,
  onClose,
  onSaved,
}: {
  group: Group;
  onClose: () => void;
  onSaved: (group: Group) => void;
}) {
  const api = useApi();
  const [name, setName] = useState(group.name);
  const [type, setType] = useState<string>(group.type);
  const [cover, setCover] = useState<Cover>(parseCover(group.cover));
  const [errors, setErrors] = useState<{ name?: string; cover?: string }>({});
  const [problem, setProblem] = useState<PlainProblem>();
  const [submitting, setSubmitting] = useState(false);
  const nameRef = useRef<HTMLInputElement>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setProblem(undefined);
    const trimmed = name.trim();
    if (!trimmed) {
      setErrors({ name: "Enter a name for the group." });
      return nameRef.current?.focus();
    }
    if (Array.from(trimmed).length > 60) {
      setErrors({ name: "Use 60 characters or fewer." });
      return nameRef.current?.focus();
    }
    setErrors({});
    const nextCover = coverValue(cover);
    const body = {
      version: group.version,
      ...(trimmed !== group.name ? { name: trimmed } : {}),
      ...(type !== group.type
        ? { type: type as (typeof GROUP_TYPES)[number]["value"] }
        : {}),
      ...(nextCover && nextCover !== (group.cover ?? undefined)
        ? { cover: nextCover }
        : {}),
    };
    setSubmitting(true);
    try {
      const { data, error } = await api.PATCH("/api/v1/groups/{groupId}", {
        params: { path: { groupId: group.id } },
        body,
      });
      if (data) {
        onSaved(data);
        return;
      }
      const raw = asProblem(error);
      setErrors(fieldMessages(raw));
      setProblem(describeProblem(raw));
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog title="Edit group" onClose={onClose}>
      <form noValidate onSubmit={submit} className="flex flex-col gap-4">
        {problem && <InlineProblem problem={problem} />}
        <fieldset className="flex flex-col gap-2">
          <legend className="text-body-sm font-semibold text-text">Type</legend>
          <div className="flex flex-wrap gap-2">
            {GROUP_TYPES.map((option) => (
              <label
                key={option.value}
                className={`inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-pill border px-4 text-body-sm font-semibold has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-focus-ring ${
                  type === option.value
                    ? "border-primary bg-primary-soft text-primary-text"
                    : "border-border-strong bg-surface text-text"
                }`}
              >
                <input
                  type="radio"
                  name="editType"
                  value={option.value}
                  checked={type === option.value}
                  onChange={() => setType(option.value)}
                  className="sr-only"
                />
                {option.label}
              </label>
            ))}
          </div>
        </fieldset>
        <TextField
          label="Name"
          name="editName"
          autoComplete="off"
          value={name}
          error={errors.name}
          onChange={(e) => setName(e.target.value)}
          inputRef={nameRef}
        />
        <CoverPicker
          value={cover}
          onChange={setCover}
          error={errors.cover}
          allowClear={false}
        />
        <div className="flex gap-2">
          <Button type="submit" loading={submitting}>
            Save changes
          </Button>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

function MemberRow({
  member,
  isMe,
  canManage,
  onRemove,
  onWriteOff,
  onPhone,
}: {
  member: Member;
  isMe: boolean;
  canManage: boolean;
  onRemove: () => void;
  onWriteOff: () => void;
  onPhone: () => void;
}) {
  const hasBalance = member.balances.length > 0;
  return (
    <li className="flex min-h-16 flex-wrap items-center gap-3 border-t border-border px-4 py-2 first:border-t-0">
      <Avatar id={member.userId ?? member.id} name={member.displayName} />
      <span className="min-w-0 flex-1">
        <span className="block truncate font-semibold text-text">
          {member.displayName}
          {isMe ? " (you)" : ""}
        </span>
        <span className="flex flex-wrap gap-x-2 text-body-sm text-text-muted">
          {member.role === "OWNER" && <span>Owner</span>}
          {member.guest && (
            <span>
              Guest
              {member.guestPhoneMasked ? ` · ${member.guestPhoneMasked}` : ""}
            </span>
          )}
          {hasBalance ? (
            member.balances.map((b) => (
              <span
                key={b.currency}
                className={
                  b.amountMinor < 0
                    ? "text-negative-text"
                    : "text-positive-text"
                }
              >
                {balanceWords(b.amountMinor, b.currency)}
              </span>
            ))
          ) : (
            <span>Settled</span>
          )}
        </span>
      </span>
      {canManage && member.role !== "OWNER" && (
        <span className="flex gap-1">
          {member.guest && (
            <Button
              variant="ghost"
              size="sm"
              aria-label={`Set ${member.displayName}'s number`}
              onClick={onPhone}
            >
              Number
            </Button>
          )}
          {hasBalance && (
            <Button
              variant="ghost"
              size="sm"
              aria-label={`Write off ${member.displayName}'s balance`}
              onClick={onWriteOff}
            >
              Write off
            </Button>
          )}
          <Button
            variant="ghost"
            size="sm"
            aria-label={`Remove ${member.displayName}`}
            onClick={onRemove}
          >
            Remove
          </Button>
        </span>
      )}
    </li>
  );
}

/**
 * Owner-only: set, change or clear a guest's number. The current number is shown masked only, so
 * the field starts empty. A number a member has verified becomes a claim suggestion for them, and
 * a changed number withdraws the old suggestions.
 */
function GuestPhoneDialog({
  groupId,
  guest,
  onClose,
  onSaved,
}: {
  groupId: string;
  guest: Member;
  onClose: () => void;
  onSaved: () => void;
}) {
  const api = useApi();
  const [phone, setPhone] = useState("");
  const [error, setError] = useState<string>();
  const [problem, setProblem] = useState<PlainProblem>();
  const [busy, setBusy] = useState<"save" | "clear">();
  const phoneRef = useRef<HTMLInputElement>(null);

  async function put(action: "save" | "clear", value: string | undefined) {
    if (busy) return;
    setBusy(action);
    setProblem(undefined);
    setError(undefined);
    try {
      const { data, error: failed } = await api.PUT(
        "/api/v1/groups/{groupId}/members/{memberId}/guest-phone",
        {
          params: { path: { groupId, memberId: guest.id } },
          body: { phone: value, version: guest.version },
        },
      );
      if (data) {
        onSaved();
        return;
      }
      const raw = asProblem(failed);
      if (raw?.type?.endsWith(":invalid-phone")) {
        setError(describeProblem(raw).body);
        phoneRef.current?.focus();
      } else setProblem(describeProblem(raw));
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setBusy(undefined);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!phone.trim()) {
      setError("Enter a mobile number.");
      return phoneRef.current?.focus();
    }
    return put("save", phone.trim());
  }

  return (
    <Dialog title={`${guest.displayName}'s number`} onClose={onClose}>
      <form noValidate onSubmit={submit} className="flex flex-col gap-4">
        {problem && <InlineProblem problem={problem} />}
        {guest.guestPhoneMasked && (
          <p className="text-text-muted">
            Now:{" "}
            <span className="font-semibold tabular-nums text-text">
              {guest.guestPhoneMasked}
            </span>
          </p>
        )}
        <TextField
          label="Mobile number"
          name="guestPhoneEdit"
          type="tel"
          autoComplete="off"
          inputMode="tel"
          value={phone}
          error={error}
          helper="Starts with +91 unless you type another country code."
          onChange={(e) => setPhone(e.target.value)}
          inputRef={phoneRef}
        />
        <div className="flex flex-wrap gap-2">
          <Button type="submit" loading={busy === "save"}>
            Save number
          </Button>
          {guest.guestPhoneMasked && (
            <Button
              variant="secondary"
              loading={busy === "clear"}
              onClick={() => put("clear", undefined)}
            >
              Remove number
            </Button>
          )}
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

function AddGuestForm({
  groupId,
  onAdded,
}: {
  groupId: string;
  onAdded: () => void;
}) {
  const api = useApi();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [errors, setErrors] = useState<{
    name?: string;
    email?: string;
    phone?: string;
  }>({});
  const [problem, setProblem] = useState<PlainProblem>();
  const [submitting, setSubmitting] = useState(false);
  const nameRef = useRef<HTMLInputElement>(null);
  const attempt = useRef<{ key: string; signature: string }>(undefined);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    setProblem(undefined);
    const trimmed = name.trim();
    if (!trimmed) {
      setErrors({ name: "Enter the guest's name." });
      return nameRef.current?.focus();
    }
    setErrors({});
    const signature = `${trimmed}/${email.trim()}/${phone.trim()}`;
    if (attempt.current?.signature !== signature) {
      attempt.current = { key: newIdempotencyKey(), signature };
    }
    setSubmitting(true);
    try {
      const { response, error } = await api.POST(
        "/api/v1/groups/{groupId}/guests",
        {
          params: {
            path: { groupId },
            header: { "Idempotency-Key": attempt.current.key },
          },
          body: {
            name: trimmed,
            email: email.trim() || undefined,
            phone: phone.trim() || undefined,
          },
        },
      );
      if (response.ok) {
        setName("");
        setEmail("");
        setPhone("");
        attempt.current = undefined;
        onAdded();
        return;
      }
      const raw = asProblem(error);
      if (raw?.type?.endsWith(":invalid-phone")) {
        setErrors({ phone: describeProblem(raw).body });
        return;
      }
      setErrors(fieldMessages(raw));
      setProblem(describeProblem(raw));
    } catch {
      setProblem(describeNetworkError());
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form
      noValidate
      onSubmit={submit}
      aria-labelledby="guest-title"
      className="flex flex-col gap-3 rounded-lg bg-surface p-4 shadow-sm"
    >
      <h2 id="guest-title" className="font-display text-h2 font-bold text-text">
        Add a guest
      </h2>
      <p className="text-body-sm text-text-muted">
        For someone without an account yet. They can take their place later with
        an invite link.
      </p>
      {problem && <InlineProblem problem={problem} />}
      <TextField
        label="Guest name"
        name="guestName"
        autoComplete="off"
        value={name}
        error={errors.name}
        onChange={(e) => setName(e.target.value)}
        inputRef={nameRef}
      />
      <TextField
        label="Email (optional)"
        name="guestEmail"
        type="email"
        autoComplete="off"
        value={email}
        error={errors.email}
        onChange={(e) => setEmail(e.target.value)}
      />
      <TextField
        label="Mobile number (optional)"
        name="guestPhone"
        type="tel"
        autoComplete="off"
        inputMode="tel"
        value={phone}
        error={errors.phone}
        helper="If they sign up later and verify this number, we'll suggest their seat to them."
        onChange={(e) => setPhone(e.target.value)}
      />
      <Button type="submit" variant="secondary" loading={submitting}>
        Add guest
      </Button>
    </form>
  );
}

export { formatMoney };
