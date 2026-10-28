import { WarningCircle } from "@phosphor-icons/react";
import * as Label from "@radix-ui/react-label";
import {
  type InputHTMLAttributes,
  type ReactNode,
  type Ref,
  useId,
} from "react";

export type TextFieldProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "id"
> & {
  label: string;
  helper?: string;
  error?: string;
  /** Sits inside the input's right edge, e.g. a show/hide button. */
  trailing?: ReactNode;
  /** Extra content on the label row's right, e.g. a link. */
  labelAside?: ReactNode;
  inputRef?: Ref<HTMLInputElement>;
};

/** Label above, 44 px input, helper and error text wired with aria-describedby. */
export function TextField({
  label,
  helper,
  error,
  trailing,
  labelAside,
  inputRef,
  className = "",
  ...input
}: TextFieldProps) {
  const id = useId();
  const helperId = `${id}-helper`;
  const errorId = `${id}-error`;
  const describedBy =
    [error ? errorId : undefined, helper ? helperId : undefined]
      .filter(Boolean)
      .join(" ") || undefined;
  return (
    <div className={`flex flex-col gap-1 ${className}`}>
      <div className="flex items-center justify-between gap-2">
        <Label.Root htmlFor={id} className="text-body-sm font-bold text-text">
          {label}
        </Label.Root>
        {labelAside}
      </div>
      <div
        className={`flex min-h-11 items-center rounded-md border bg-surface-sunken focus-within:border-primary focus-within:shadow-[0_0_0_3px_var(--color-primary-soft)] ${
          error ? "border-danger" : "border-border"
        }`}
      >
        <input
          {...input}
          ref={inputRef}
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className="min-h-11 w-full min-w-0 flex-1 rounded-md bg-transparent px-3 text-body text-text placeholder:text-text-faint focus-visible:outline-none"
        />
        {trailing}
      </div>
      {error && (
        <p
          id={errorId}
          className="flex items-center gap-1 text-body-sm text-danger-text"
        >
          <WarningCircle size={16} weight="bold" aria-hidden="true" />
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
