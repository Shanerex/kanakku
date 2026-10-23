import { Eye, EyeSlash } from "@phosphor-icons/react";
import { useState } from "react";
import { TextField, type TextFieldProps } from "./TextField";

/** A password input with a show/hide toggle (`aria-pressed`, labelled for screen readers). */
export function PasswordField(
  props: Omit<TextFieldProps, "type" | "trailing">,
) {
  const [shown, setShown] = useState(false);
  return (
    <TextField
      {...props}
      type={shown ? "text" : "password"}
      trailing={
        <button
          type="button"
          aria-label="Show password"
          aria-pressed={shown}
          onClick={() => setShown((value) => !value)}
          className="mr-1 inline-flex size-11 shrink-0 items-center justify-center rounded-pill text-text-muted hover:bg-primary-soft"
        >
          {shown ? (
            <EyeSlash size={20} aria-hidden="true" />
          ) : (
            <Eye size={20} aria-hidden="true" />
          )}
        </button>
      }
    />
  );
}
