import { CheckCircle, WarningCircle, X } from "@phosphor-icons/react";
import * as RadixToast from "@radix-ui/react-toast";
import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useMemo,
  useState,
} from "react";

type Tone = "info" | "error";
type Entry = { id: number; message: string; tone: Tone };

type ToastApi = {
  /** Shows one short message for about four seconds; a newer one replaces it. */
  show: (message: string, options?: { tone?: Tone }) => void;
};

const ToastContext = createContext<ToastApi | undefined>(undefined);

const DURATION_MS = 4000;

/**
 * Toasts for the whole app (Radix Toast, tokens only): bottom-centre on phones, bottom-left from
 * 768 px, one at a time, announced politely, or at once for an error.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [entry, setEntry] = useState<Entry>();
  const show = useCallback<ToastApi["show"]>((message, options) => {
    setEntry((current) => ({
      id: (current?.id ?? 0) + 1,
      message,
      tone: options?.tone ?? "info",
    }));
  }, []);
  const api = useMemo(() => ({ show }), [show]);
  return (
    <ToastContext.Provider value={api}>
      <RadixToast.Provider duration={DURATION_MS} label="Notifications">
        {children}
        {entry && (
          <RadixToast.Root
            key={entry.id}
            type={entry.tone === "error" ? "foreground" : "background"}
            onOpenChange={(open) => {
              if (!open)
                setEntry((current) =>
                  current?.id === entry.id ? undefined : current,
                );
            }}
            className="flex items-start gap-3 rounded-md bg-surface-raised p-4 text-body-sm text-text shadow-lg"
          >
            {entry.tone === "error" ? (
              <WarningCircle
                size={20}
                weight="fill"
                aria-hidden="true"
                className="mt-0.5 shrink-0 text-danger-text"
              />
            ) : (
              <CheckCircle
                size={20}
                weight="fill"
                aria-hidden="true"
                className="mt-0.5 shrink-0 text-positive-text"
              />
            )}
            <RadixToast.Description className="min-w-0 flex-1">
              {entry.message}
            </RadixToast.Description>
            <RadixToast.Close
              aria-label="Dismiss"
              className="-m-2 inline-flex size-11 shrink-0 items-center justify-center rounded-pill text-text-muted hover:bg-primary-soft"
            >
              <X size={16} aria-hidden="true" />
            </RadixToast.Close>
          </RadixToast.Root>
        )}
        <RadixToast.Viewport className="fixed bottom-4 left-1/2 z-50 flex w-[calc(100vw-2rem)] max-w-[420px] -translate-x-1/2 flex-col md:left-4 md:translate-x-0" />
      </RadixToast.Provider>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const value = useContext(ToastContext);
  if (!value) throw new Error("useToast must be used inside <ToastProvider>");
  return value;
}
