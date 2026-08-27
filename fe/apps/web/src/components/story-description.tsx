"use client";

import { useState } from "react";

interface StoryDescriptionProps {
  synopsis: string | null;
}

export function StoryDescription({ synopsis }: StoryDescriptionProps) {
  const [expanded, setExpanded] = useState(false);
  if (!synopsis) return null;
  const isLong = synopsis.length > 180;
  const isHtml = /<[a-z][\s\S]*>/i.test(synopsis);

  return (
    <div className="storyDescriptionBlock">
      <h3 className="descriptionHeading">GIỚI THIỆU:</h3>
      {isHtml ? (
        <div
          className={`storyDescription ${!expanded && isLong ? "isClamped" : ""}`}
          dangerouslySetInnerHTML={{ __html: synopsis }}
        />
      ) : (
        <p className={`storyDescription ${!expanded && isLong ? "isClamped" : ""}`}>
          {synopsis}
        </p>
      )}
      {isLong && (
        <button
          type="button"
          className="descriptionToggleBtn"
          onClick={() => setExpanded((prev) => !prev)}
        >
          {expanded ? "Thu gọn ▲" : "Xem thêm ▼"}
        </button>
      )}
    </div>
  );
}
