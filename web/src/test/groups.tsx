import { useLocation } from "react-router";
import type { Seen } from "./harness";

export const ASHA = "11111111-1111-4111-8111-111111111111";
export const GROUP_ID = "22222222-2222-4222-8222-222222222222";

export function aGroup(over: Record<string, unknown> = {}) {
  return {
    id: GROUP_ID,
    name: "Goa Trip",
    type: "TRIP",
    currency: "INR",
    cover: "color:mango",
    version: 0,
    myRole: "OWNER",
    ...over,
  };
}

export function aMember(over: Record<string, unknown> = {}) {
  return {
    id: "33333333-3333-4333-8333-333333333331",
    userId: ASHA,
    displayName: "Asha Rao",
    role: "OWNER",
    guest: false,
    joinedAt: "2026-10-24T10:00:00Z",
    balances: [],
    version: 0,
    ...over,
  };
}

export const KARTHIK = "33333333-3333-4333-8333-333333333332";
export const BALA = "33333333-3333-4333-8333-333333333333";

export function karthik(over: Record<string, unknown> = {}) {
  return aMember({
    id: KARTHIK,
    userId: null,
    displayName: "Karthik",
    role: "MEMBER",
    guest: true,
    ...over,
  });
}

export function bala(over: Record<string, unknown> = {}) {
  return aMember({
    id: BALA,
    userId: "44444444-4444-4444-8444-444444444444",
    displayName: "Bala",
    role: "MEMBER",
    ...over,
  });
}

export function requestsTo(requests: Seen[], method: string, path: string) {
  return requests.filter((r) => r.method === method && r.path === path);
}

/** A stand-in route that prints where the app navigated to. */
export function Here() {
  const location = useLocation();
  return (
    <p>{`now at ${location.pathname}${location.search}${location.hash}`}</p>
  );
}
