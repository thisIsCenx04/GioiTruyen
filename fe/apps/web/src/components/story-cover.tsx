/**
 * Story covers, and what to show when there is not one.
 *
 * A story with no cover used to render as a flat block of tinted colour that
 * looked deliberate, so nobody could tell which stories were still waiting for
 * artwork. The placeholder says so instead.
 */

/**
 * The API returns the cover as a public path, not an id, despite the field
 * name. An absolute URL is passed through untouched.
 */
export function coverUrl(coverAssetId: string | null | undefined): string {
  const value = coverAssetId?.trim();
  if (!value) return "";
  if (/^https?:\/\//u.test(value)) return value;
  return value.startsWith("/") ? value : `/uploads/stories/${value}`;
}

/** The single book image shown for every story that has no cover yet. */
export function StoryCoverPlaceholder() {
  return (
    <span className="coverPlaceholder">
      <svg aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.5" viewBox="0 0 24 24">
        <path d="M4 5.5A1.5 1.5 0 0 1 5.5 4H17a2 2 0 0 1 2 2v12.5" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M4 5.5V19a1 1 0 0 0 1 1h14" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M8 8.5h7M8 12h7" strokeLinecap="round" />
      </svg>
      <span>Chưa có ảnh bìa</span>
    </span>
  );
}
