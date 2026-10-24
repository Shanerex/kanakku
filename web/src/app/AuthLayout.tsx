import type { ReactNode } from "react";

/** Shared frame for signed-out screens: a centred 400 px card over soft identity-colour blobs. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className="relative grid min-h-screen place-items-center overflow-hidden px-4 py-8">
      <svg
        aria-hidden="true"
        focusable="false"
        className="absolute inset-0 size-full"
        preserveAspectRatio="xMidYMid slice"
        viewBox="0 0 1200 800"
      >
        <circle cx="160" cy="140" r="180" fill="var(--id-violet-tint)" />
        <circle cx="1060" cy="160" r="120" fill="var(--id-mango-tint)" />
        <circle cx="980" cy="680" r="220" fill="var(--id-mint-tint)" />
        <circle cx="240" cy="700" r="90" fill="var(--id-pink-tint)" />
        <circle
          cx="1120"
          cy="420"
          r="18"
          fill="var(--id-sky-solid)"
          opacity=".5"
        />
        <circle
          cx="90"
          cy="420"
          r="12"
          fill="var(--id-tangerine-solid)"
          opacity=".5"
        />
      </svg>
      <div className="relative flex w-full max-w-[400px] flex-col gap-4 rounded-lg bg-surface p-8 shadow-lg">
        <header>
          <p className="font-display text-[34px] font-extrabold leading-tight text-primary">
            kanakku<span className="text-primary-text">.</span>
          </p>
          <p className="text-body-sm text-text-muted">
            Split trips, flats and dinners. Settle over UPI.
          </p>
        </header>
        {children}
      </div>
    </main>
  );
}
