const PALETTE = [
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

/** FNV-1a, 32 bit, over the UTF-8 bytes of the id. */
export function fnv1a32(text: string): number {
  let hash = 0x811c9dc5;
  for (const byte of new TextEncoder().encode(text)) {
    hash ^= byte;
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash >>> 0;
}

/** A person's identity colour: the same on every device, from `palette[fnv1a32(id) % 10]`. */
export function identityColor(id: string): (typeof PALETTE)[number] {
  return PALETTE[fnv1a32(id) % PALETTE.length];
}

/** First letter of the first two words of the display name. */
export function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => Array.from(word)[0].toUpperCase())
    .join("");
}

const SIZES = { md: 36, lg: 48, xl: 72 } as const;

export function Avatar({
  id,
  name,
  size = "md",
}: {
  id: string;
  name: string;
  size?: keyof typeof SIZES;
}) {
  const color = identityColor(id);
  const px = SIZES[size];
  return (
    <span
      role="img"
      aria-label={name}
      style={{
        width: px,
        height: px,
        background: `var(--id-${color}-tint)`,
        color: `var(--id-${color}-ink)`,
        fontSize: px / 2.4,
      }}
      className="inline-flex shrink-0 items-center justify-center rounded-pill font-body font-extrabold"
    >
      {initials(name)}
    </span>
  );
}
