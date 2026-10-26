import { identityColor } from "../../ui/Avatar";
import { parseCover } from "./cover";

const SIZES = { md: 40, lg: 56 } as const;

/** The group's cover: its emoji, or its colour with the first letter of the name. */
export function GroupBadge({
  id,
  name,
  cover,
  size = "md",
}: {
  id: string;
  name: string;
  cover?: string | null;
  size?: keyof typeof SIZES;
}) {
  const parsed = parseCover(cover);
  const color = parsed.kind === "color" ? parsed.color : identityColor(id);
  const px = SIZES[size];
  return (
    <span
      aria-hidden="true"
      style={{
        width: px,
        height: px,
        background: `var(--id-${color}-tint)`,
        color: `var(--id-${color}-ink)`,
        fontSize: px / 2.2,
      }}
      className="inline-flex shrink-0 items-center justify-center rounded-lg font-display font-extrabold"
    >
      {parsed.kind === "emoji"
        ? parsed.emoji
        : (Array.from(name.trim())[0]?.toUpperCase() ?? "?")}
    </span>
  );
}
