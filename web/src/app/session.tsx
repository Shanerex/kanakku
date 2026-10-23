import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import type { Profile } from "../api/client";
import { useApi } from "./api-context";
import {
  asProblem,
  describeNetworkError,
  describeProblem,
  type PlainProblem,
} from "./problems";

export type SessionState =
  | { status: "loading" }
  | { status: "signed-out" }
  | { status: "signed-in"; profile: Profile }
  | { status: "error"; problem: PlainProblem };

type SessionValue = {
  state: SessionState;
  /** Call after a profile edit to show the server's latest profile. */
  setProfile: (profile: Profile) => void;
  /**
   * Call after a successful sign-in: stores the profile and says where to go next. The sign-in
   * and sign-up screens redirect signed-in visitors, and this tells them where to send this one.
   */
  signedIn: (profile: Profile, to: string) => void;
  /** Where the redirecting wrappers should send the visitor next, set by `signedIn`/`clear`. */
  destination: string | undefined;
  /** Forget the destination once it has been used. */
  clearDestination: () => void;
  /** Call after sign-out (or a 401); `to` says where to send the visitor. */
  clear: (to?: string) => void;
  reload: () => void;
};

const SessionContext = createContext<SessionValue | undefined>(undefined);

/** Loads `GET /me` once to learn whether a session cookie is still good. */
export function SessionProvider({ children }: { children: ReactNode }) {
  const api = useApi();
  const [state, setState] = useState<SessionState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [destination, setDestination] = useState<string>();

  // biome-ignore lint/correctness/useExhaustiveDependencies: `attempt` re-runs the load on reload()
  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const { data, error, response } = await api.GET("/api/v1/me");
        if (!active) return;
        if (data) setState({ status: "signed-in", profile: data });
        else if (response.status === 401) setState({ status: "signed-out" });
        else
          setState({
            status: "error",
            problem: describeProblem(asProblem(error)),
          });
      } catch {
        if (active)
          setState({ status: "error", problem: describeNetworkError() });
      }
    })();
    return () => {
      active = false;
    };
  }, [api, attempt]);

  const setProfile = useCallback(
    (profile: Profile) => setState({ status: "signed-in", profile }),
    [],
  );
  const signedIn = useCallback((profile: Profile, to: string) => {
    setDestination(to);
    setState({ status: "signed-in", profile });
  }, []);
  const clearDestination = useCallback(() => setDestination(undefined), []);
  const clear = useCallback((to?: string) => {
    setDestination(to);
    setState({ status: "signed-out" });
  }, []);
  const reload = useCallback(() => {
    setState({ status: "loading" });
    setAttempt((n) => n + 1);
  }, []);

  const value = useMemo(
    () => ({
      state,
      destination,
      setProfile,
      signedIn,
      clearDestination,
      clear,
      reload,
    }),
    [state, destination, setProfile, signedIn, clearDestination, clear, reload],
  );
  return (
    <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
  );
}

export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (!value)
    throw new Error("useSession must be used inside <SessionProvider>");
  return value;
}
