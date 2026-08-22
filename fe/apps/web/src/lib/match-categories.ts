/**
 * Turns the genre names an uploaded file lists into the site's own genre ids.
 *
 * <p>A file writes "Ngôn Tình, Huyền Huyễn, Vô Hạn Lưu"; the form holds ~84
 * genres with ids. Matching them here means the publisher does not tick a list
 * the file already gave them - and, more to the point, does not have to notice
 * that the file gave it.
 *
 * <p>Matching is deliberately forgiving about case, accents and spacing,
 * because a file is typed by hand: "vô hạn lưu", "Vô Hạn Lưu" and "vo han luu"
 * are the same genre. It is not forgiving about anything else - a name that
 * does not match a real genre is reported back rather than guessed at, since a
 * wrong genre is worse than a missing one.
 */

export type Category = { id: string; name: string; slug: string };

/** Lowercase, unaccented, single-spaced - the form a name is compared in. */
function normalise(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[̀-ͯ]/gu, "")
    .replace(/đ/giu, "d")
    .toLowerCase()
    .replace(/[^a-z0-9]+/gu, " ")
    .trim();
}

export type CategoryMatch = {
  /** Genre ids to tick. */
  ids: string[];
  /** Names that matched, for reporting back. */
  matched: string[];
  /** Names the site has no genre for. */
  unmatched: string[];
};

export function matchCategoryNames(
  names: readonly string[],
  categories: readonly Category[],
): CategoryMatch {
  const byName = new Map<string, Category>();
  for (const category of categories) {
    byName.set(normalise(category.name), category);
    // The slug is already normalised on the server, and some files write the
    // slug rather than the display name.
    byName.set(normalise(category.slug), category);
  }

  const ids: string[] = [];
  const matched: string[] = [];
  const unmatched: string[] = [];
  const seen = new Set<string>();

  for (const raw of names) {
    const name = raw.trim();
    if (!name) continue;
    const found = byName.get(normalise(name));
    if (!found) {
      unmatched.push(name);
      continue;
    }
    if (seen.has(found.id)) continue;
    seen.add(found.id);
    ids.push(found.id);
    matched.push(found.name);
  }

  return { ids, matched, unmatched };
}
