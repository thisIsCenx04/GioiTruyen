import React from "react";

export interface BrandMarkProps {
  inverse?: boolean;
  className?: string;
  /** Logo height in px. The site header row is ~74px tall, so 52 still clears it. */
  size?: number;
}

export function BrandMark({ inverse, className, size = 52 }: BrandMarkProps) {
  return (
    <span
      className={className}
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: "0.5rem",
        fontWeight: 900,
        fontSize: "1.25rem",
        letterSpacing: "-0.03em",
        color: inverse ? "#ffffff" : "#0f5fff",
        textDecoration: "none",
      }}
    >
      <img
        src="/logo-full.png"
        alt="Giới Truyện Logo"
        style={{
          height: `${size}px`,
          width: "auto",
          maxHeight: `${size}px`,
          objectFit: "contain",
        }}
        onError={(e) => {
          const img = e.currentTarget;
          if (!img.dataset.fallback) {
            img.dataset.fallback = "true";
            img.src = "/logo-icon.png";
          }
        }}
      />
    </span>
  );
}

export interface StatusPillProps {
  status?: string;
  label?: string;
  children?: React.ReactNode;
  /** "active"/"attention" are what the workspace consoles label a live or stalled item with. */
  tone?: "success" | "warning" | "error" | "info" | "neutral" | "active" | "attention";
  className?: string;
}

export function StatusPill({ status, label, children, tone = "info", className }: StatusPillProps) {
  const text = label || children || status || "Thông tin";
  const bgMap = {
    success: "#dcfce7",
    warning: "#fef3c7",
    error: "#fee2e2",
    info: "#e0f2fe",
    neutral: "#f1f5f9",
    // A held item reads as live, one waiting on someone reads as a warning.
    active: "#dcfce7",
    attention: "#fef3c7",
  };
  const colorMap = {
    success: "#15803d",
    warning: "#b45309",
    error: "#b91c1c",
    info: "#0369a1",
    neutral: "#475569",
    active: "#15803d",
    attention: "#b45309",
  };

  return (
    <span
      className={className}
      style={{
        display: "inline-flex",
        alignItems: "center",
        padding: "0.2rem 0.65rem",
        borderRadius: "9999px",
        fontSize: "0.75rem",
        fontWeight: 700,
        backgroundColor: bgMap[tone] || bgMap.info,
        color: colorMap[tone] || colorMap.info,
      }}
    >
      {text}
    </span>
  );
}
