import { MagnifyingGlass, X } from "@phosphor-icons/react";
import { useRef } from "react";

/** A 44 px search box with a leading magnifier and a clear button once there is text. */
export function SearchField({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}) {
  const input = useRef<HTMLInputElement>(null);
  return (
    <div className="flex min-h-11 w-full items-center gap-2 rounded-md border border-border bg-surface-sunken px-3 focus-within:border-primary focus-within:shadow-[0_0_0_3px_var(--color-primary-soft)]">
      <MagnifyingGlass
        size={20}
        aria-hidden="true"
        className="shrink-0 text-text-muted"
      />
      <input
        ref={input}
        type="search"
        aria-label={label}
        placeholder={placeholder}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="min-h-11 min-w-0 flex-1 bg-transparent text-body text-text placeholder:text-text-faint focus-visible:outline-none"
      />
      {value && (
        <button
          type="button"
          aria-label="Clear search"
          onClick={() => {
            onChange("");
            input.current?.focus();
          }}
          className="-mr-2 inline-flex size-11 shrink-0 items-center justify-center rounded-pill text-text-muted hover:bg-primary-soft"
        >
          <X size={16} aria-hidden="true" />
        </button>
      )}
    </div>
  );
}
