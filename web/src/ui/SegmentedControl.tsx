import { useId } from "react";

type Option<T extends string> = { value: T; label: string };

/** 2 to 4 options in a sunken track; the selected one is raised. Native radios: arrow keys work. */
export function SegmentedControl<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Option<T>[];
  value: T;
  onChange: (value: T) => void;
}) {
  const name = useId();
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className="inline-flex gap-1 self-start rounded-md bg-surface-sunken p-1"
    >
      {options.map((option) => {
        const on = option.value === value;
        return (
          <label
            key={option.value}
            className={`inline-flex min-h-9 cursor-pointer items-center rounded-sm px-4 text-body-sm font-bold has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-focus-ring ${
              on
                ? "bg-surface text-text shadow-sm"
                : "text-text-muted hover:text-text"
            }`}
          >
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={on}
              onChange={() => onChange(option.value)}
              className="sr-only"
            />
            {option.label}
          </label>
        );
      })}
    </div>
  );
}
