import { CircleNotch } from "@phosphor-icons/react";
import type { ButtonHTMLAttributes } from "react";

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md" | "lg";
  loading?: boolean;
  fullWidth?: boolean;
};

const VARIANTS = {
  primary:
    "bg-primary text-text-on-primary hover:bg-primary-hover active:bg-primary-pressed",
  secondary:
    "bg-surface text-text border border-border-strong hover:bg-surface-sunken",
  ghost: "bg-transparent text-primary-text hover:bg-primary-soft",
  danger: "bg-danger text-text-on-primary hover:opacity-90",
} as const;

const SIZES = {
  sm: "min-h-8 px-3 text-body-sm",
  md: "min-h-11 px-4 text-body",
  lg: "min-h-[52px] px-5 text-body",
} as const;

/** The one button. A loading button keeps its size and label and ignores presses. */
export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  fullWidth = false,
  className = "",
  type = "button",
  onClick,
  children,
  ...rest
}: Props) {
  return (
    <button
      {...rest}
      type={type}
      aria-busy={loading || undefined}
      onClick={loading ? (event) => event.preventDefault() : onClick}
      className={`inline-flex items-center justify-center gap-2 rounded-md font-body font-semibold transition-transform duration-[var(--duration-fast)] active:scale-[0.97] disabled:cursor-not-allowed disabled:opacity-40 ${VARIANTS[variant]} ${SIZES[size]} ${fullWidth ? "w-full" : ""} ${className}`}
    >
      {loading && (
        <CircleNotch className="animate-spin" size={20} aria-hidden="true" />
      )}
      {children}
    </button>
  );
}
