import { TextField } from "./TextField";

/**
 * The six-digit code from a text message. One field, so password managers, autofill from SMS
 * (`one-time-code`) and screen readers all work; the digits are set large, wide and tabular so a
 * code is easy to check against the text.
 */
export function CodeField({
  value,
  onChange,
  error,
  helper,
  label = "6-digit code",
  inputRef,
}: {
  value: string;
  onChange: (digits: string) => void;
  error?: string;
  helper?: string;
  label?: string;
  inputRef?: React.Ref<HTMLInputElement>;
}) {
  return (
    <TextField
      label={label}
      name="code"
      inputMode="numeric"
      autoComplete="one-time-code"
      pattern="[0-9]*"
      maxLength={6}
      value={value}
      error={error}
      helper={helper}
      inputRef={inputRef}
      className="[&_input]:text-center [&_input]:font-display [&_input]:text-h1 [&_input]:font-bold [&_input]:tracking-widest [&_input]:tabular-nums"
      onChange={(e) => onChange(e.target.value.replace(/\D/g, "").slice(0, 6))}
    />
  );
}
