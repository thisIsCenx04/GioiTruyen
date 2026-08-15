import React from "react";

export function XuIcon({
  size = 18,
  className = "",
  style = {},
}: {
  size?: number;
  className?: string;
  style?: React.CSSProperties;
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={`currencyIcon xuIcon ${className}`}
      style={{
        display: "inline-block",
        verticalAlign: "middle",
        flexShrink: 0,
        ...style,
      }}
    >
      <defs>
        <linearGradient id="goldCoinGrad" x1="2" y1="2" x2="22" y2="22" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#fef08a" />
          <stop offset="45%" stopColor="#f59e0b" />
          <stop offset="100%" stopColor="#b45309" />
        </linearGradient>
        <linearGradient id="goldInnerGrad" x1="4" y1="4" x2="20" y2="20" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#fffbeb" />
          <stop offset="50%" stopColor="#fbbf24" />
          <stop offset="100%" stopColor="#d97706" />
        </linearGradient>
      </defs>
      {/* Outer coin border */}
      <circle cx="12" cy="12" r="10" fill="url(#goldCoinGrad)" stroke="#78350f" strokeWidth="1.2" />
      {/* Inner ring */}
      <circle cx="12" cy="12" r="7.2" fill="url(#goldInnerGrad)" stroke="#92400e" strokeWidth="0.8" />
      {/* Coin symbol $ */}
      <path
        d="M12 6.5v11M14.2 8.8h-3.4a1.3 1.3 0 0 0 0 2.6h2.4a1.3 1.3 0 0 1 0 2.6H9.8"
        stroke="#78350f"
        strokeWidth="1.75"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function NgocIcon({
  size = 18,
  className = "",
  style = {},
}: {
  size?: number;
  className?: string;
  style?: React.CSSProperties;
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={`currencyIcon ngocIcon ${className}`}
      style={{
        display: "inline-block",
        verticalAlign: "middle",
        flexShrink: 0,
        ...style,
      }}
    >
      <defs>
        <linearGradient id="cyanGemGrad" x1="3" y1="3" x2="21" y2="21" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#cff4fc" />
          <stop offset="40%" stopColor="#22d3ee" />
          <stop offset="100%" stopColor="#0284c7" />
        </linearGradient>
      </defs>
      {/* Main Gem Silhouette */}
      <path
        d="M6 3h12l4 6-10 12L2 9l4-6z"
        fill="url(#cyanGemGrad)"
        stroke="#0f172a"
        strokeWidth="1.5"
        strokeLinejoin="round"
      />
      {/* Facet lines */}
      <path
        d="M2 9h20M6 3l6 18L18 3M6 3l6 6 6-6"
        stroke="#0f172a"
        strokeWidth="1.1"
        strokeLinejoin="round"
      />
    </svg>
  );
}
