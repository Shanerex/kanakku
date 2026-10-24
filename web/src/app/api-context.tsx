import { createContext, useContext } from "react";
import type { ApiClient } from "../api/client";

const ApiContext = createContext<ApiClient | undefined>(undefined);

export const ApiProvider = ApiContext.Provider;

/** The typed API client for the whole app. */
export function useApi(): ApiClient {
  const client = useContext(ApiContext);
  if (!client) throw new Error("useApi must be used inside <ApiProvider>");
  return client;
}
