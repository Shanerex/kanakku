import { Check } from "@phosphor-icons/react";
import { COVER_COLORS, COVER_EMOJIS, type Cover } from "./cover";

function titleCase(word: string) {
  return word.charAt(0).toUpperCase() + word.slice(1);
}

/** Ten named colour swatches and a grid of emoji; one choice at a time, as the server stores it. */
export function CoverPicker({
  value,
  onChange,
  error,
  allowClear = true,
}: {
  value: Cover;
  onChange: (cover: Cover) => void;
  error?: string;
  allowClear?: boolean;
}) {
  return (
    <div className="flex flex-col gap-3">
      <div
        role="radiogroup"
        aria-label="Colour"
        className="flex flex-col gap-2"
      >
        <p aria-hidden="true" className="text-body-sm font-bold text-text">
          Colour
        </p>
        <div className="flex flex-wrap gap-2">
          {COVER_COLORS.map((color) => {
            const selected = value.kind === "color" && value.color === color;
            return (
              <label
                key={color}
                style={{ background: `var(--id-${color}-solid)` }}
                className={`inline-flex size-11 cursor-pointer items-center justify-center rounded-pill text-text-on-primary transition-shadow has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-4 has-[:focus-visible]:outline-focus-ring ${selected ? "shadow-[0_0_0_2px_var(--color-surface),0_0_0_4px_var(--color-text)]" : "hover:shadow-md"}`}
              >
                <input
                  type="radio"
                  name="cover-colour"
                  checked={selected}
                  aria-label={titleCase(color)}
                  onChange={() => onChange({ kind: "color", color })}
                  className="sr-only"
                />
                {selected && (
                  <Check size={20} weight="bold" aria-hidden="true" />
                )}
              </label>
            );
          })}
        </div>
      </div>
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-body-sm font-bold text-text">Emoji</legend>
        <div className="flex flex-wrap gap-2">
          {COVER_EMOJIS.map((emoji) => {
            const selected = value.kind === "emoji" && value.emoji === emoji;
            return (
              <label
                key={emoji}
                className={`inline-flex size-11 cursor-pointer items-center justify-center rounded-md border text-xl has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-focus-ring ${selected ? "border-primary bg-primary-soft" : "border-border bg-surface"}`}
              >
                <input
                  type="radio"
                  name="cover-emoji"
                  checked={selected}
                  aria-label={`Emoji ${emoji}`}
                  onChange={() => onChange({ kind: "emoji", emoji })}
                  className="sr-only"
                />
                <span aria-hidden="true">{emoji}</span>
              </label>
            );
          })}
        </div>
      </fieldset>
      {allowClear && value.kind !== "none" && (
        <button
          type="button"
          onClick={() => onChange({ kind: "none" })}
          className="self-start text-body-sm font-semibold text-primary-text underline"
        >
          Clear cover
        </button>
      )}
      {error && <p className="text-body-sm text-danger-text">{error}</p>}
    </div>
  );
}
