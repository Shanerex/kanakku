import type { Problem } from "../api/client";

/** A problem in plain words: an optional short title and a sentence saying what to do. */
export type PlainProblem = { title?: string; body: string };

const PREFIX = "urn:kanakku:problem:";

/** "8 minutes", "1 minute", "40 seconds": rounds minutes up so the wait is never understated. */
export function formatWait(seconds: number): string {
  if (seconds < 60) {
    const s = Math.max(1, Math.ceil(seconds));
    return s === 1 ? "1 second" : `${s} seconds`;
  }
  const minutes = Math.ceil(seconds / 60);
  return minutes === 1 ? "1 minute" : `${minutes} minutes`;
}

/**
 * Turns an RFC 9457 problem into words for the person. Never shows the type URI, the status code
 * or the server's detail text.
 */
export function describeProblem(problem?: Partial<Problem>): PlainProblem {
  const slug = problem?.type?.startsWith(PREFIX)
    ? problem.type.slice(PREFIX.length)
    : undefined;
  switch (slug) {
    case "invalid-credentials":
      return { body: "That email and password don't match." };
    case "too-many-attempts": {
      const seconds = problem?.retryAfterSeconds;
      return seconds
        ? {
            title: "Too many tries",
            body: `Try again in ${formatWait(seconds)}.`,
          }
        : {
            title: "Too many tries",
            body: "Wait a few minutes and try again.",
          };
    }
    case "busy":
      return {
        title: "Busy right now",
        body: "Wait a moment and try again.",
      };
    case "email-taken":
      return {
        title: "That email already has an account",
        body: "Sign in instead, or use a different email.",
      };
    case "version-conflict":
      return {
        title: "Changed somewhere else",
        body: "Your profile was changed somewhere else. Reload it and try again.",
      };
    case "group-not-found":
      return {
        title: "This group isn't available",
        body: "It may have been removed, or you may not be in it.",
      };
    case "member-not-found":
    case "invite-not-found":
      return { body: "That isn't there any more. Reload and try again." };
    case "owner-only":
      return {
        title: "Only the Owner can do this",
        body: "Ask the group's Owner to do it.",
      };
    case "stale-version":
      return {
        title: "Changed somewhere else",
        body: "This was changed somewhere else. Reload and try again.",
      };
    case "group-archived":
      return {
        title: "This group is archived",
        body: "Unarchive it to make changes.",
      };
    case "last-owner":
      return {
        title: "The Owner can't leave",
        body: "A group always keeps its Owner.",
      };
    case "already-member":
      return { body: "That person is already in this group." };
    case "invite-only":
      return {
        title: "Can't add them directly",
        body: "They only join through an invite link. Add them as a guest and share an invite.",
      };
    case "not-a-guest":
      return { body: "Invites are only for guests who haven't joined yet." };
    case "invalid-absorbers":
      return { body: "Pick at least one other member to share the balance." };
    case "search-too-short":
      return {
        body: "Type at least 3 characters, or 4 digits for a phone number.",
      };
    case "search-rate-limited":
      return {
        title: "You searched too often",
        body: problem?.retryAfterSeconds
          ? `Try again in ${formatWait(problem.retryAfterSeconds)}.`
          : "Wait a moment and try again.",
      };
    case "invalid-phone":
      return { body: "Enter a mobile number like 98765 43210." };
    case "phone-taken":
      return {
        title: "That number is already in use",
        body: "It's verified on another account. Use a different number.",
      };
    case "phone-missing":
      return { body: "Add your mobile number first." };
    case "phone-already-verified":
      return { body: "That number is already verified." };
    case "sms-not-configured":
      return {
        body: "This server can't send text messages, so your number stays unverified.",
      };
    case "wrong-code":
      return { body: "That code isn't right. Check the text and try again." };
    case "code-expired":
      return { body: "That code has expired. Send a new one." };
    case "code-locked":
      return {
        title: "Too many wrong codes",
        body: problem?.retryAfterSeconds
          ? `Try again in ${formatWait(problem.retryAfterSeconds)}.`
          : "Try again later.",
      };
    case "too-many-codes":
      return {
        title: "Too many codes requested",
        body: problem?.retryAfterSeconds
          ? `Try again in ${formatWait(problem.retryAfterSeconds)}.`
          : "Try again later.",
      };
    case "sms-busy":
      return {
        title: "Text messages are busy",
        body: problem?.retryAfterSeconds
          ? `This server has sent all the codes it allows this hour. Try again in ${formatWait(problem.retryAfterSeconds)}.`
          : "This server has sent all the codes it allows this hour. Try again later.",
      };
    case "phone-country-unsupported":
      return {
        title: "We can't text that number",
        body: "This server only sends codes to some countries. Ask the person who runs it, or use a number from a supported country.",
      };
    case "sign-up-expired":
      return {
        title: "This sign-up timed out",
        body: "Start the sign-up again.",
      };
    case "invite-for-someone-else":
      return {
        title: "This invite is for someone else",
        body: "It was made for another account. Sign in as that person, or ask the group's Owner for your own invite.",
      };
    case "user-not-found":
      return { body: "That person isn't there any more. Search again." };
    case "suggestion-not-found":
    case "suggestion-resolved":
      return { body: "This suggestion no longer applies." };
    case "invite-unusable":
      return {
        title: "This invite no longer works",
        body: "It expired, was revoked or was already used. Ask for a new one.",
      };
    case "member-has-balance":
      return {
        title: "Balance isn't settled",
        body: "Settle it first, or write it off.",
      };
    case "validation":
      return { body: "Check the highlighted fields and try again." };
    case "idempotency-key-required":
    case "idempotency-key-reuse":
      return {
        body: "That didn't go through. Check the details and try again.",
      };
    case "csrf-token-invalid":
      return { body: "This page expired. Try again." };
    case "unauthenticated":
      return { body: "Sign in to continue." };
    case "forbidden":
      return {
        title: "This isn't available",
        body: "It may have been removed, or it isn't yours.",
      };
    case "internal":
      return {
        title: "Something went wrong on our side",
        body: "Your change wasn't saved. Try again.",
      };
    default:
      if (problem?.status !== undefined && problem.status >= 500) {
        return {
          title: "Something went wrong on our side",
          body: "Your change wasn't saved. Try again.",
        };
      }
      return { body: "Something went wrong. Try again." };
  }
}

/** The message for a request that never reached the server. */
export function describeNetworkError(): PlainProblem {
  return {
    title: "Can't reach Kanakku",
    body: "Check your connection and try again.",
  };
}

/** Field messages from a 400 problem, keyed by field name. The first message per field wins. */
export function fieldMessages(
  problem?: Partial<Problem>,
): Record<string, string> {
  const result: Record<string, string> = {};
  for (const error of problem?.errors ?? []) {
    if (error.field && error.message && !(error.field in result)) {
      result[error.field] = error.message;
    }
  }
  return result;
}

/** Reads a problem body from an openapi-fetch `error` value, if it looks like one. */
export function asProblem(error: unknown): Partial<Problem> | undefined {
  if (typeof error === "object" && error !== null && "type" in error) {
    return error as Partial<Problem>;
  }
  return undefined;
}
