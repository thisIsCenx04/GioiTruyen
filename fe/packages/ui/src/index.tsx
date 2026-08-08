import React from "react";

export interface BrandMarkProps {
  inverse?: boolean;
  className?: string;
}

export function BrandMark({ inverse, className }: BrandMarkProps) {
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
          height: "36px",
          width: "auto",
          maxHeight: "36px",
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
  tone?: "success" | "warning" | "error" | "info" | "neutral";
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
  };
  const colorMap = {
    success: "#15803d",
    warning: "#b45309",
    error: "#b91c1c",
    info: "#0369a1",
    neutral: "#475569",
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
