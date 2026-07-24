export function localDateTimeWithOffset(
  localValue: string,
  offsetMinutes: number,
) {
  if (
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/u.test(localValue) ||
    !Number.isInteger(offsetMinutes) ||
    Math.abs(offsetMinutes) > 14 * 60
  ) {
    throw new Error("Invalid local publishing time");
  }
  const total = -offsetMinutes;
  const sign = total >= 0 ? "+" : "-";
  const absolute = Math.abs(total);
  const hours = Math.floor(absolute / 60).toString().padStart(2, "0");
  const minutes = (absolute % 60).toString().padStart(2, "0");
  const time = localValue.length === 16 ? `${localValue}:00` : localValue;
  return `${time}${sign}${hours}:${minutes}`;
}
