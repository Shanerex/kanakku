import * as Label from "@radix-ui/react-label";
import { useId, useMemo } from "react";

type Props = {
  label: string;
  value: string;
  onChange: (code: string) => void;
  helper?: string;
  error?: string;
};

function currencyCodes(): string[] {
  const all = Intl.supportedValuesOf("currency");
  return ["INR", ...all.filter((code) => code !== "INR")];
}

/** ISO 4217 picker. A native select: ~160 options, full keyboard and screen-reader support. */
export function CurrencySelect({
  label,
  value,
  onChange,
  helper,
  error,
}: Props) {
  const id = useId();
  const helperId = `${id}-helper`;
  const errorId = `${id}-error`;
  const options = useMemo(() => {
    const names = new Intl.DisplayNames(["en"], { type: "currency" });
    return currencyCodes().map((code) => ({
      code,
      text: `${code} · ${names.of(code) ?? code}`,
    }));
  }, []);
  return (
    <div className="flex flex-col gap-1">
      <Label.Root htmlFor={id} className="text-body-sm font-semibold text-text">
        {label}
      </Label.Root>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={
          [error ? errorId : undefined, helper ? helperId : undefined]
            .filter(Boolean)
            .join(" ") || undefined
        }
        className={`min-h-11 rounded-md border bg-surface-sunken px-3 text-body text-text focus-visible:border-primary ${
          error ? "border-danger" : "border-border"
        }`}
      >
        {options.map((option) => (
          <option key={option.code} value={option.code}>
            {option.text}
          </option>
        ))}
      </select>
      {error && (
        <p id={errorId} className="text-body-sm text-danger-text">
          {error}
        </p>
      )}
      {helper && (
        <p id={helperId} className="text-body-sm text-text-muted">
          {helper}
        </p>
      )}
    </div>
  );
}
