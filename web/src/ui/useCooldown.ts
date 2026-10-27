import { useCallback, useEffect, useState } from "react";

/**
 * A countdown for "Resend in 24 s". `start` begins a new wait; `left` is the whole seconds
 * remaining, 0 when the action is available again. It counts down from a deadline, so a slow tick
 * or a background tab never makes the wait longer than stated.
 */
export function useCooldown(seconds: number) {
  const [until, setUntil] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (until <= Date.now()) return;
    const timer = setInterval(() => {
      const t = Date.now();
      setNow(t);
      if (t >= until) clearInterval(timer);
    }, 250);
    return () => clearInterval(timer);
  }, [until]);
  const start = useCallback(() => {
    setUntil(Date.now() + seconds * 1000);
    setNow(Date.now());
  }, [seconds]);
  return { left: Math.max(0, Math.ceil((until - now) / 1000)), start };
}
