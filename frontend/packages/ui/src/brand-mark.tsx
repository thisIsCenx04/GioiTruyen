import styles from "./ui.module.css";

export type BrandMarkProps = Readonly<{
  compact?: boolean;
  inverse?: boolean;
}>;

export function BrandMark({
  compact = false,
  inverse = false,
}: BrandMarkProps) {
  return (
    <span
      className={styles.brand}
      data-compact={compact || undefined}
      data-inverse={inverse || undefined}
    >
      <span aria-hidden="true" className={styles.brandSeal}>
        G
      </span>
      {!compact && <span className={styles.brandName}>Giới Truyện</span>}
    </span>
  );
}
