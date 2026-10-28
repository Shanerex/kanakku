import { identityColor } from "../../ui/Avatar";
import { parseCover } from "./cover";

const SIZES = { sm: 28, md: 40, lg: 48, xl: 72 } as const;

/** The group's identity colour: its chosen colour, else one picked from its id. */
export function groupColor(id: string, cover?: string | null) {
  const parsed = parseCover(cover);
  return parsed.kind === "color" ? parsed.color : identityColor(id);
}

/** The group's cover: its emoji, or its colour with the first letter of the name. */
export function GroupBadge({
  id,
  name,
  cover,
  size = "md",
  onTint = false,
}: {
  id: string;
  name: string;
  cover?: string | null;
  size?: keyof typeof SIZES;
  /** Sits on a tinted header, so it takes the surface colour to stay visible. */
  onTint?: boolean;
}) {
  const parsed = parseCover(cover);
  const color = groupColor(id, cover);
  const px = SIZES[size];
  return (
    <span
      aria-hidden="true"
      style={{
        width: px,
        height: px,
        background: onTint ? "var(--color-surface)" : `var(--id-${color}-tint)`,
        color: `var(--id-${color}-ink)`,
        fontSize: size === "xl" ? 40 : size === "sm" ? 15 : px / 1.9,
      }}
      className={`inline-flex shrink-0 items-center justify-center font-display font-extrabold ${size === "sm" ? "rounded-sm" : size === "xl" ? "rounded-lg" : "rounded-md"}`}
    >
      {parsed.kind === "emoji"
        ? parsed.emoji
        : (Array.from(name.trim())[0]?.toUpperCase() ?? "?")}
    </span>
  );
}
