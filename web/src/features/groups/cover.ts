/** The cover presets the server accepts: an identity colour or one emoji from a short list. */
export const COVER_COLORS = [
  "violet",
  "grape",
  "pink",
  "coral",
  "tangerine",
  "mango",
  "lime",
  "mint",
  "teal",
  "sky",
] as const;

export const COVER_EMOJIS = [
  "🏖",
  "🏠",
  "💞",
  "🎉",
  "👥",
  "✈",
  "🏔",
  "🍕",
  "🛒",
  "🚗",
  "🎂",
  "⛺",
  "🍻",
  "🎬",
  "🏝",
  "🍽",
] as const;

export type Cover =
  | { kind: "color"; color: (typeof COVER_COLORS)[number] }
  | { kind: "emoji"; emoji: string }
  | { kind: "none" };

export function parseCover(value?: string | null): Cover {
  if (value?.startsWith("color:")) {
    const color = COVER_COLORS.find((c) => c === value.slice(6));
    if (color) return { kind: "color", color };
  }
  if (value?.startsWith("emoji:")) {
    return { kind: "emoji", emoji: value.slice(6) };
  }
  return { kind: "none" };
}

export function coverValue(cover: Cover): string | undefined {
  if (cover.kind === "color") return `color:${cover.color}`;
  if (cover.kind === "emoji") return `emoji:${cover.emoji}`;
  return undefined;
}

export const GROUP_TYPES = [
  { value: "TRIP", label: "Trip" },
  { value: "HOME", label: "Home" },
  { value: "COUPLE", label: "Couple" },
  { value: "OTHER", label: "Other" },
] as const;
