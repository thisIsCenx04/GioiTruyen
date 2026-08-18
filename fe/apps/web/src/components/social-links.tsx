import { Facebook, Send } from "lucide-react";

/**
 * The platform's public contact channels.
 *
 * Kept in one place so the header and footer cannot drift apart, and so a
 * changed handle is a one-line edit.
 */
export const SOCIAL_LINKS = [
  {
    href: "https://www.facebook.com/profile.php?id=61593287553709",
    icon: Facebook,
    label: "Facebook",
  },
  {
    href: "https://t.me/khanguyet567",
    icon: Send,
    label: "Telegram",
  },
] as const;

export function SocialLinks({ compact = false }: Readonly<{ compact?: boolean }>) {
  return (
    <div className={compact ? "socialLinks isCompact" : "socialLinks"}>
      {SOCIAL_LINKS.map((link) => {
        const Icon = link.icon;
        return (
          <a
            aria-label={link.label}
            href={link.href}
            key={link.label}
            rel="noreferrer noopener"
            target="_blank"
            title={link.label}
          >
            <Icon aria-hidden="true" size={compact ? 16 : 18} />
            {compact ? null : <span>{link.label}</span>}
          </a>
        );
      })}
    </div>
  );
}
