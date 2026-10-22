import { useEffect, useState } from "react";
import { getReadiness, loadConfig } from "./api";

type State =
  | { kind: "loading" }
  | { kind: "config-error" }
  | { kind: "status"; label: "UP" | "DOWN" | "unreachable" };

export default function App() {
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    let active = true;
    (async () => {
      let config: Awaited<ReturnType<typeof loadConfig>>;
      try {
        config = await loadConfig();
      } catch {
        if (active) setState({ kind: "config-error" });
        return;
      }
      try {
        const label = await getReadiness(config);
        if (active) setState({ kind: "status", label });
      } catch {
        if (active) setState({ kind: "status", label: "unreachable" });
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  return (
    <main>
      <h1>Kanakku</h1>
      {state.kind === "loading" && <p>Loading...</p>}
      {state.kind === "config-error" && (
        <p role="alert">Could not load the web configuration (config.json).</p>
      )}
      {state.kind === "status" && (
        <p role="status">API status: {state.label}</p>
      )}
    </main>
  );
}
