/**
 * How money is written across the site.
 *
 * <p>Xu amounts run to five and six figures - a combo can cost 37,450 - and
 * printed as a bare "37450" they take a moment to read and are easy to
 * misjudge by a factor of ten. Grouping is with commas rather than the dots
 * Vietnamese typography would normally use, because a dot in a price reads as
 * a decimal point to a reader used to seeing "39.000đ" and "39.0" alike.
 */
const grouped = new Intl.NumberFormat("en-US", { maximumFractionDigits: 0 });

/** A coin amount with thousands separators, e.g. 37450 -> "37,450". */
export function formatXu(value: number | string | null | undefined): string {
  const amount = typeof value === "string" ? Number(value) : value;
  if (amount == null || !Number.isFinite(amount)) return "0";
  return grouped.format(Math.round(amount));
}
