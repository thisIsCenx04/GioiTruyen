import styles from "./ui.module.css";

export type StoryCardProps = Readonly<{
  author: string;
  coverTone: "blue" | "coral" | "jade";
  eyebrow: string;
  href: string;
  latestChapter: string;
  title: string;
}>;

export function StoryCard({
  author,
  coverTone,
  eyebrow,
  href,
  latestChapter,
  title,
}: StoryCardProps) {
  return (
    <article className={styles.storyCard}>
      <a aria-label={`Đọc ${title}`} className={styles.storyCover} data-tone={coverTone} href={href}>
        <span className={styles.coverMonogram}>{title.slice(0, 1)}</span>
        <span className={styles.coverChapter}>{latestChapter}</span>
      </a>
      <div className={styles.storyBody}>
        <p className={styles.storyEyebrow}>{eyebrow}</p>
        <h3>
          <a href={href}>{title}</a>
        </h3>
        <p>{author}</p>
      </div>
    </article>
  );
}
