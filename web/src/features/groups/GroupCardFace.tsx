import { Chip } from "../../ui/Chip";
import { GROUP_TYPES } from "./cover";
import { GroupBadge, groupColor } from "./GroupBadge";

/**
 * What a group looks like as a card (web-home.html): a 6 px bar in its solid colour, a header band
 * in its tint with the badge, name and type in the ink colour, then a white body with the facts the
 * list API has: my role and the currency. The groups grid and the new-group preview both use it.
 */
export function GroupCardFace({
  id,
  name,
  cover,
  type,
  currency,
  owner,
  archived = false,
}: {
  id: string;
  name: string;
  cover?: string | null;
  type: string;
  currency: string;
  owner: boolean;
  archived?: boolean;
}) {
  const color = groupColor(id, cover);
  const typeLabel = GROUP_TYPES.find((t) => t.value === type)?.label ?? "Group";
  return (
    <>
      <div
        data-identity-bar
        style={{ background: `var(--id-${color}-solid)` }}
        className="h-1.5"
      />
      <div
        data-identity-band
        style={{ background: `var(--id-${color}-tint)` }}
        className="flex items-center gap-3 px-5 py-4"
      >
        <GroupBadge id={id} name={name} cover={cover} size="lg" onTint />
        <div className="min-w-0">
          <p
            style={{ color: `var(--id-${color}-ink)` }}
            className="truncate text-h3 font-extrabold"
          >
            {name}
          </p>
          <p
            style={{ color: `var(--id-${color}-ink)` }}
            className="text-body-sm"
          >
            {typeLabel}
          </p>
        </div>
      </div>
      <div
        data-identity-body
        className="flex flex-1 flex-wrap content-center items-center gap-2 px-5 py-4"
      >
        <Chip tone={owner ? "primary" : "neutral"}>
          {owner ? "Owner" : "Member"}
        </Chip>
        <Chip>{currency}</Chip>
        {archived && <Chip tone="info">Archived</Chip>}
      </div>
    </>
  );
}
