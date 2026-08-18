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

/**
 * The card-sized copy of a cover, by the same rule the server names it:
 * `/uploads/stories/x.png` is served as `/uploads/stories/thumb/x.jpg`.
 *
 * <p>Originals are print-resolution, several megabytes each, and every shelf
 * draws them under 200px wide - which is why a grid of cards used to fill in
 * one picture at a time. Files the server cannot resize (WEBP, GIF) and covers
 * hosted elsewhere have no thumbnail, so callers must keep the original as a
 * fallback; see `useCoverFallback`.
 */
export function coverThumbUrl(coverAssetId: string | null | undefined): string {
  const original = coverUrl(coverAssetId);
  const match = /^(\/uploads\/stories\/)([^/]+)\.(png|jpe?g)$/iu.exec(original);
  return match ? `${match[1]}thumb/${match[2]}.jpg` : original;
}

/**
 * Swaps a missing thumbnail for the original it was derived from.
 *
 * <p>A cover uploaded in a format the server cannot resize has no thumbnail,
 * and the backfill may not have reached an older one yet. Either way the
 * reader must see the picture, slow rather than broken. The guard stops a
 * failing original from retrying forever.
 */
export function onCoverError(original: string) {
  return (event: { currentTarget: HTMLImageElement }) => {
    const image = event.currentTarget;
    if (image.dataset.fellBack === "1") return;
    image.dataset.fellBack = "1";
    image.src = original;
  };
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
