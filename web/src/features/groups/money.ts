/**
 * Money from minor units, with no floating point: the amount is turned into a decimal string with
 * the currency's own number of digits and handed to Intl as text.
 */
export function formatMoney(minor: number, currency: string): string {
  const formatter = new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
  });
  const digits = formatter.resolvedOptions().maximumFractionDigits ?? 2;
  const value = BigInt(minor);
  const negative = value < 0n;
  const digitsText = (negative ? -value : value)
    .toString()
    .padStart(digits + 1, "0");
  const decimal =
    digits === 0
      ? digitsText
      : `${digitsText.slice(0, -digits)}.${digitsText.slice(-digits)}`;
  // Intl reads a decimal string exactly; the cast only satisfies the older `number` typing.
  const text = formatter.format(decimal as unknown as number);
  return negative ? `-${text}` : text;
}

/** "owes ₹45.00" or "is owed ₹45.00", the words a screen reader and a person both need. */
export function balanceWords(minor: number, currency: string): string {
  const amount = formatMoney(Math.abs(minor), currency);
  return minor < 0 ? `owes ${amount}` : `is owed ${amount}`;
}
