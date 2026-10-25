import minorUnits from "./minorUnits.json";

/**
 * The ISO 4217 minor-unit digits the server stores amounts with. Intl's own table follows CLDR,
 * which differs for some currencies (IQD is 3 here and 0 in CLDR), so it is not used. The file
 * lists every currency whose digits are not 2; a Java test keeps it equal to the JDK's table.
 */
export function minorUnitDigits(currency: string): number {
  return (minorUnits as Record<string, number>)[currency] ?? 2;
}

/**
 * Money from minor units, with no floating point: the amount is turned into a decimal string with
 * the currency's own number of digits and handed to Intl as text.
 */
export function formatMoney(minor: number, currency: string): string {
  const digits = minorUnitDigits(currency);
  const formatter = new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
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
