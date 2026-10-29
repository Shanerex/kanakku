import { useCallback, useEffect, useState } from "react";
import { useApi } from "../../app/api-context";
import {
  asProblem,
  describeNetworkError,
  describeProblem,
  type PlainProblem,
} from "../../app/problems";
import type { Group } from "./types";

/**
 * The groups I belong to (`GET /api/v1/groups`). Loads on mount and again whenever `refreshKey`
 * changes; a failed refresh keeps the last list and reports the problem.
 */
export function useGroups(refreshKey?: unknown) {
  const api = useApi();
  const [groups, setGroups] = useState<Group[]>();
  const [problem, setProblem] = useState<PlainProblem>();

  // biome-ignore lint/correctness/useExhaustiveDependencies: refreshKey only triggers a reload.
  const load = useCallback(async () => {
    try {
      const { data, error } = await api.GET("/api/v1/groups");
      if (data) {
        setGroups(data);
        setProblem(undefined);
      } else setProblem(describeProblem(asProblem(error)));
    } catch {
      setProblem(describeNetworkError());
    }
  }, [api, refreshKey]);

  useEffect(() => {
    load();
  }, [load]);

  return { groups, problem };
}
