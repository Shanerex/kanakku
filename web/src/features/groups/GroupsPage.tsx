import { CaretRight, Plus } from "@phosphor-icons/react";
import { useState } from "react";
import { Link } from "react-router";
import { TopBarActions, usePageTitle } from "../../app/AppShell";
import { InlineProblem } from "../../app/InlineProblem";
import { useSession } from "../../app/session";
import { useMediaQuery } from "../../app/useMediaQuery";
import { ButtonLink } from "../../ui/Button";
import { Card } from "../../ui/Card";
import { EmptyState } from "../../ui/EmptyState";
import { SearchField } from "../../ui/SearchField";
import { SegmentedControl } from "../../ui/SegmentedControl";
import { ClaimSuggestionsBanner } from "./ClaimSuggestionsBanner";
import { GroupBadge } from "./GroupBadge";
import { GroupCardFace } from "./GroupCardFace";
import type { Group } from "./types";
import { useGroups } from "./useGroups";

type View = "active" | "archived";

function subtitle(group: Group) {
  return `${group.currency}${group.myRole === "OWNER" ? " · You're the Owner" : ""}${group.archivedAt ? " · Archived" : ""}`;
}

/** The groups I belong to: search, Active or Archived, a row list and (from 1024 px) a card grid. */
export default function GroupsPage() {
  const { state } = useSession();
  const { groups, problem } = useGroups();
  const [query, setQuery] = useState("");
  const [view, setView] = useState<View>("active");
  const wide = useMediaQuery("(min-width: 1024px)");
  usePageTitle("Groups");

  if (state.status !== "signed-in") return null;

  const needle = query.trim().toLowerCase();
  const shown = (groups ?? []).filter(
    (g) =>
      Boolean(g.archivedAt) === (view === "archived") &&
      g.name.toLowerCase().includes(needle),
  );
  const newGroup = (
    <ButtonLink to="/groups/new" pill>
      <Plus size={16} weight="bold" aria-hidden="true" />
      New group
    </ButtonLink>
  );

  return (
    <div className="flex flex-col gap-4">
      <TopBarActions>{newGroup}</TopBarActions>
      <ClaimSuggestionsBanner />
      {problem && <InlineProblem problem={problem} />}
      {groups && groups.length === 0 && (
        <EmptyState
          illustration={<SuitcaseBlobs />}
          title="Start your first group"
          body="A trip, a flat, a dinner. Add people and split as you go."
          action={
            <ButtonLink to="/groups/new" size="lg" pill>
              <Plus size={16} weight="bold" aria-hidden="true" />
              New group
            </ButtonLink>
          }
        />
      )}
      {groups && groups.length > 0 && (
        <>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="sm:max-w-[360px] sm:flex-1">
              <SearchField
                label="Search groups"
                placeholder="Search groups"
                value={query}
                onChange={setQuery}
              />
            </div>
            <SegmentedControl
              label="Show"
              value={view}
              onChange={setView}
              options={[
                { value: "active", label: "Active" },
                { value: "archived", label: "Archived" },
              ]}
            />
          </div>
          {shown.length === 0 ? (
            <p className="rounded-lg border border-border bg-surface p-6 text-center text-text-muted">
              {needle
                ? "No groups match that search."
                : view === "archived"
                  ? "No archived groups."
                  : "No active groups."}
            </p>
          ) : wide ? (
            <ul
              aria-label="Your groups"
              className="grid grid-cols-3 items-stretch gap-5"
            >
              {shown.map((group) => (
                <li key={group.id} className="flex">
                  <IdentityCard group={group} />
                </li>
              ))}
              <li className="flex">
                <Link
                  to="/groups/new"
                  className="flex min-h-36 w-full flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-border-strong p-5 font-bold text-primary-text hover:bg-primary-soft"
                >
                  <Plus size={24} aria-hidden="true" />
                  New group
                </Link>
              </li>
            </ul>
          ) : (
            <Card
              as="ul"
              flush
              aria-label="Your groups"
              className="flex flex-col divide-y divide-border"
            >
              {shown.map((group) => (
                <li key={group.id}>
                  <GroupRow group={group} />
                </li>
              ))}
            </Card>
          )}
        </>
      )}
    </div>
  );
}

function GroupRow({ group }: { group: Group }) {
  return (
    <Link
      to={`/groups/${group.id}`}
      className="flex min-h-16 items-center gap-3 px-4 py-2 hover:bg-surface-sunken"
    >
      <GroupBadge id={group.id} name={group.name} cover={group.cover} />
      <span className="min-w-0 flex-1">
        <span className="block truncate font-semibold text-text">
          {group.name}
        </span>
        <span className="block text-body-sm text-text-muted">
          {subtitle(group)}
        </span>
      </span>
      <CaretRight size={20} aria-hidden="true" className="text-text-muted" />
    </Link>
  );
}

/** A groups-grid Card around the shared card face. */
function IdentityCard({ group }: { group: Group }) {
  return (
    <Link
      to={`/groups/${group.id}`}
      className="flex w-full flex-col overflow-hidden rounded-lg border border-border bg-surface shadow-sm transition-shadow hover:shadow-md"
    >
      <GroupCardFace
        id={group.id}
        name={group.name}
        cover={group.cover}
        type={group.type}
        currency={group.currency}
        owner={group.myRole === "OWNER"}
        archived={Boolean(group.archivedAt)}
      />
    </Link>
  );
}

/** Three overlapping blobs (violet, mango, mint) with a suitcase, from tokens only. */
function SuitcaseBlobs() {
  return (
    <svg
      viewBox="0 0 144 144"
      className="size-full"
      aria-hidden="true"
      focusable="false"
    >
      <circle cx="52" cy="58" r="40" fill="var(--id-violet-tint)" />
      <circle cx="94" cy="54" r="34" fill="var(--id-mango-tint)" />
      <circle cx="74" cy="96" r="38" fill="var(--id-mint-tint)" />
      <rect
        x="48"
        y="62"
        width="48"
        height="38"
        rx="8"
        fill="var(--color-surface)"
        stroke="var(--id-violet-solid)"
        strokeWidth="3"
      />
      <path
        d="M62 62v-8a6 6 0 0 1 6-6h8a6 6 0 0 1 6 6v8"
        fill="none"
        stroke="var(--id-violet-solid)"
        strokeWidth="3"
      />
      <path
        d="M48 78h48"
        stroke="var(--id-violet-solid)"
        strokeWidth="3"
        opacity=".5"
      />
    </svg>
  );
}
