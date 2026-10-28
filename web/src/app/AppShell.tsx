import { CaretLeft, Plus, UsersThree } from "@phosphor-icons/react";
import {
  createContext,
  type ReactNode,
  useContext,
  useEffect,
  useState,
} from "react";
import { createPortal } from "react-dom";
import { Link, Outlet, useLocation, useMatch } from "react-router";
import { GroupBadge } from "../features/groups/GroupBadge";
import { useGroups } from "../features/groups/useGroups";
import { Avatar } from "../ui/Avatar";
import { Wordmark } from "../ui/Wordmark";
import { useSession } from "./session";

type BarState = {
  title: string;
  /** The page draws its own h1 (a group's header band); the bar keeps only its actions. */
  hidden: boolean;
  /** A back link shown in the bar below 768 px, where the sidebar is not there to go back with. */
  back?: { to: string; label: string };
};

const TitleContext = createContext<(bar: BarState) => void>(() => {});
const ActionsContext = createContext<HTMLElement | null>(null);

/** Puts its children at the right end of the top bar (e.g. a "New group" link). */
export function TopBarActions({ children }: { children: ReactNode }) {
  const slot = useContext(ActionsContext);
  return slot ? createPortal(children, slot) : null;
}

/**
 * Names the screen in the top bar's h1. Call it once per screen; the screen renders no h1 of its
 * own, so there is one per page.
 */
export function usePageTitle(
  title: string,
  options: { hidden?: boolean; backTo?: string; backLabel?: string } = {},
) {
  const setBar = useContext(TitleContext);
  const { hidden = false, backTo, backLabel } = options;
  useEffect(() => {
    setBar({
      title,
      hidden,
      back: backTo ? { to: backTo, label: backLabel ?? "Back" } : undefined,
    });
    document.title = `${title} · Kanakku`;
  }, [setBar, title, hidden, backTo, backLabel]);
}

/** Which nav item the current path belongs to. A group's own pages count as Groups. */
function useActive() {
  const { pathname } = useLocation();
  return {
    groupList: pathname === "/groups",
    groups:
      pathname === "/groups" ||
      (pathname.startsWith("/groups/") && pathname !== "/groups/new"),
    newGroup: pathname === "/groups/new",
    me: pathname === "/me" || pathname.startsWith("/me/"),
  };
}

const NAV_ITEM =
  "flex min-h-11 items-center gap-3 rounded-pill px-3 font-semibold text-text hover:bg-surface-sunken";
const NAV_ON = "bg-primary-soft font-extrabold text-primary-text";

function Sidebar() {
  const { state } = useSession();
  const profile = state.status === "signed-in" ? state.profile : undefined;
  const active = useActive();
  const { pathname } = useLocation();
  const open = useMatch("/groups/:id/*");
  const openId = open?.params.id === "new" ? undefined : open?.params.id;
  // Reload on navigation so a group made or archived elsewhere shows up; an error or a slow
  // load just leaves the section out, never the rest of the sidebar.
  const { groups: all } = useGroups(pathname);
  const groups = all?.filter((g) => !g.archivedAt);
  const item = (on: boolean) => `${NAV_ITEM} ${on ? NAV_ON : ""}`;
  return (
    <aside className="sticky top-0 hidden h-screen w-[var(--size-sidebar)] shrink-0 flex-col gap-0.5 overflow-auto border-r border-border bg-surface px-3 py-4 md:flex">
      <div className="px-3 pb-4 text-[28px]">
        <Wordmark />
      </div>
      <nav aria-label="Main" className="flex flex-1 flex-col gap-0.5">
        <Link
          to="/groups"
          aria-current={active.groupList ? "page" : undefined}
          className={`mt-2 flex items-center px-3 pt-4 pb-1.5 text-caption font-extrabold hover:text-text ${active.groupList ? "text-primary-text" : "text-text-muted"}`}
        >
          Groups
        </Link>
        {groups && groups.length > 0 && (
          <ul aria-label="Active groups" className="flex flex-col gap-0.5">
            {groups.map((g) => (
              <li key={g.id}>
                <Link
                  to={`/groups/${g.id}`}
                  aria-current={g.id === openId ? "page" : undefined}
                  className={item(g.id === openId)}
                >
                  <GroupBadge
                    id={g.id}
                    name={g.name}
                    cover={g.cover}
                    size="sm"
                  />
                  <span className="truncate">{g.name}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
        <Link
          to="/groups/new"
          aria-current={active.newGroup ? "page" : undefined}
          className={item(active.newGroup)}
        >
          <Plus
            size={20}
            weight={active.newGroup ? "bold" : "regular"}
            aria-hidden="true"
          />
          New group
        </Link>
        <div className="flex-1" />
        {profile && (
          <Link
            to="/me"
            aria-current={active.me ? "page" : undefined}
            className={item(active.me)}
          >
            <Avatar
              id={profile.id}
              name={profile.displayName}
              size="sm"
              decorative
            />
            <span className="truncate">{profile.displayName}</span>
          </Link>
        )}
      </nav>
    </aside>
  );
}

const TAB =
  "flex min-h-14 flex-col items-center justify-center gap-0.5 text-caption font-bold";

/** The tab's icon slot: a primary-soft pill behind it while the tab is selected. */
function TabIcon({ on, children }: { on: boolean; children: ReactNode }) {
  return (
    <span
      className={`inline-flex h-8 w-16 items-center justify-center rounded-pill ${on ? "bg-primary-soft" : ""}`}
    >
      {children}
    </span>
  );
}

function BottomBar() {
  const { state } = useSession();
  const profile = state.status === "signed-in" ? state.profile : undefined;
  const active = useActive();
  const tab = (on: boolean) =>
    `${TAB} ${on ? "text-primary-text" : "text-text-muted"}`;
  return (
    <nav
      aria-label="Main tabs"
      className="fixed inset-x-0 bottom-0 z-20 grid h-[var(--size-bottom-bar)] grid-cols-2 border-t border-border bg-surface md:hidden"
    >
      <Link
        to="/groups"
        aria-current={active.groups ? "page" : undefined}
        className={tab(active.groups)}
      >
        <TabIcon on={active.groups}>
          <UsersThree
            size={24}
            weight={active.groups ? "fill" : "regular"}
            aria-hidden="true"
          />
        </TabIcon>
        Groups
      </Link>
      <Link
        to="/me"
        aria-current={active.me ? "page" : undefined}
        className={tab(active.me)}
      >
        <TabIcon on={active.me}>
          {profile && (
            <Avatar
              id={profile.id}
              name={profile.displayName}
              size="sm"
              decorative
            />
          )}
        </TabIcon>
        Me
      </Link>
    </nav>
  );
}

function TopBar({
  bar,
  onSlot,
}: {
  bar: BarState;
  onSlot: (el: HTMLElement | null) => void;
}) {
  // The bar spans the column edge to edge for its blur; its content shares the page's edges.
  return (
    <header className="sticky top-0 z-10 -mx-4 h-16 bg-bg/90 px-4 backdrop-blur md:-mx-6 md:px-6">
      <div className="mx-auto flex h-full max-w-[var(--size-content-max)] items-center gap-3">
        {bar.back && (
          <Link
            to={bar.back.to}
            className="inline-flex min-h-11 items-center gap-1 font-bold text-primary-text md:hidden"
          >
            <CaretLeft size={20} weight="bold" aria-hidden="true" />
            {bar.back.label}
          </Link>
        )}
        {!bar.hidden && (
          <h1 className="truncate font-display text-h1 font-bold text-text">
            {bar.title}
          </h1>
        )}
        <div ref={onSlot} className="ml-auto flex items-center gap-2" />
      </div>
    </header>
  );
}

/**
 * The frame around every signed-in screen (patterns.md): sidebar from 768 px, bottom bar below it,
 * a 64 px top bar with the page title, and content capped at 960 px.
 */
export function AppShell() {
  const [bar, setBar] = useState<BarState>({ title: "", hidden: false });
  const [slot, setSlot] = useState<HTMLElement | null>(null);
  return (
    <TitleContext.Provider value={setBar}>
      <ActionsContext.Provider value={slot}>
        <div className="min-h-screen md:flex">
          <Sidebar />
          <div className="min-w-0 flex-1 px-4 md:px-6">
            <TopBar bar={bar} onSlot={setSlot} />
            <main className="mx-auto flex max-w-[var(--size-content-max)] flex-col gap-4 pt-2 pb-24 md:pb-12">
              <Outlet />
            </main>
          </div>
          <BottomBar />
        </div>
      </ActionsContext.Provider>
    </TitleContext.Provider>
  );
}
