import { useEffect, useMemo, useState } from "react";
import { createBrowserRouter, RouterProvider } from "react-router";
import { createApiClient } from "./api/client";
import { type Config, loadConfig } from "./api/config";
import { buildRoutes } from "./app/routes";

type State =
  | { kind: "loading" }
  | { kind: "config-error" }
  | { kind: "ready"; config: Config };

export default function App() {
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    let active = true;
    loadConfig().then(
      (config) => active && setState({ kind: "ready", config }),
      () => active && setState({ kind: "config-error" }),
    );
    return () => {
      active = false;
    };
  }, []);

  if (state.kind === "loading") return <p role="status">Loading...</p>;
  if (state.kind === "config-error")
    return (
      <p role="alert">Could not load the web configuration (config.json).</p>
    );
  return <Ready config={state.config} />;
}

function Ready({ config }: { config: Config }) {
  const router = useMemo(() => {
    const client = createApiClient({
      baseUrl: config.apiBaseUrl.replace(/\/+$/, ""),
    });
    return createBrowserRouter(buildRoutes(client));
  }, [config.apiBaseUrl]);
  return <RouterProvider router={router} />;
}
