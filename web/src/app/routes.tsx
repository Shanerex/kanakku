import { useEffect } from "react";
import {
  Link,
  Navigate,
  Outlet,
  type RouteObject,
  useLocation,
} from "react-router";
import type { ApiClient } from "../api/client";
import SignInPage from "../features/auth/SignInPage";
import SignUpPage from "../features/auth/SignUpPage";
import ClaimInvitePage from "../features/groups/ClaimInvitePage";
import GroupPage from "../features/groups/GroupPage";
import GroupsPage from "../features/groups/GroupsPage";
import NewGroupPage from "../features/groups/NewGroupPage";
import MePage from "../features/me/MePage";
import ProfilePage from "../features/me/ProfilePage";
import WelcomePage from "../features/me/WelcomePage";
import { ToastProvider } from "../ui/Toast";
import { AppShell } from "./AppShell";
import { ApiProvider } from "./api-context";
import { InlineProblem } from "./InlineProblem";
import { SessionProvider, useSession } from "./session";

function Root({ client }: { client: ApiClient }) {
  return (
    <ApiProvider value={client}>
      <SessionProvider>
        <ToastProvider>
          <Outlet />
        </ToastProvider>
      </SessionProvider>
    </ApiProvider>
  );
}

function Loading() {
  return (
    <main className="grid min-h-screen place-items-center">
      <p role="status" className="text-text-muted">
        Loading...
      </p>
    </main>
  );
}

/** Wraps screens that need a session: signed-out visitors go to sign-in and come back after. */
export function RequireSession() {
  const { state, destination, clearDestination, reload } = useSession();
  const location = useLocation();
  useEffect(() => clearDestination, [clearDestination]);
  if (state.status === "loading") return <Loading />;
  if (state.status === "error") {
    return (
      <main className="mx-auto flex min-h-screen max-w-[400px] flex-col justify-center gap-4 p-4">
        <InlineProblem problem={state.problem} />
        <button
          type="button"
          className="text-primary-text underline"
          onClick={reload}
        >
          Try again
        </button>
      </main>
    );
  }
  if (state.status === "signed-out") {
    const returnTo = `${location.pathname}${location.search}`;
    return (
      <Navigate
        to={
          destination ??
          // The fragment (an invite token) stays out of the query string and out of server logs.
          `/sign-in?returnTo=${encodeURIComponent(returnTo)}${location.hash}`
        }
        replace
      />
    );
  }
  return <Outlet />;
}

/** Wraps screens only for signed-out visitors (sign-in, sign-up). */
export function RedirectIfSignedIn() {
  const { state, destination, clearDestination } = useSession();
  // Forget the destination once this wrapper is gone, i.e. the redirect has happened.
  useEffect(() => clearDestination, [clearDestination]);
  if (state.status === "loading") return <Loading />;
  if (state.status === "signed-in")
    return <Navigate to={destination ?? "/me"} replace />;
  return <Outlet />;
}

function Index() {
  const { state } = useSession();
  if (state.status === "loading") return <Loading />;
  return (
    <Navigate to={state.status === "signed-in" ? "/me" : "/sign-in"} replace />
  );
}

function NotFound() {
  return (
    <main className="mx-auto flex min-h-screen max-w-[400px] flex-col justify-center gap-2 p-4">
      <h1 className="font-display text-h1 font-bold">This isn't available</h1>
      <p className="text-text-muted">
        It may have been removed, or the link is wrong.
      </p>
      <Link to="/" className="text-primary-text underline">
        Go home
      </Link>
    </main>
  );
}

/** Screen routes get added here by the features that own them. */
export const authRoutes: RouteObject[] = [
  {
    element: <RedirectIfSignedIn />,
    children: [
      { path: "/sign-in", element: <SignInPage /> },
      { path: "/sign-up", element: <SignUpPage /> },
    ],
  },
];

export const profileRoutes: RouteObject[] = [
  {
    element: <RequireSession />,
    children: [
      // First-run onboarding keeps the signed-out card; everything else sits in the app shell.
      { path: "/welcome", element: <WelcomePage /> },
      {
        element: <AppShell />,
        children: [
          { path: "/me", element: <MePage /> },
          { path: "/me/profile", element: <ProfilePage /> },
        ],
      },
    ],
  },
];

export const groupRoutes: RouteObject[] = [
  {
    element: <RequireSession />,
    children: [
      {
        element: <AppShell />,
        children: [
          { path: "/groups", element: <GroupsPage /> },
          { path: "/groups/new", element: <NewGroupPage /> },
          { path: "/groups/:groupId", element: <GroupPage /> },
          { path: "/invite", element: <ClaimInvitePage /> },
        ],
      },
    ],
  },
];

export const screenRoutes: RouteObject[] = [
  ...authRoutes,
  ...profileRoutes,
  ...groupRoutes,
];

export function buildRoutes(
  client: ApiClient,
  screens: RouteObject[] = screenRoutes,
): RouteObject[] {
  return [
    {
      element: <Root client={client} />,
      children: [
        { index: true, element: <Index /> },
        ...screens,
        { path: "*", element: <NotFound /> },
      ],
    },
  ];
}
