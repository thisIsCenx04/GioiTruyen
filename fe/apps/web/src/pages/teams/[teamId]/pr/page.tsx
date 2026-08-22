"use client";

import { useParams } from "react-router-dom";

import { PrQuestWorkspace } from "@/components/pr-quest-workspace";

export default function TeamPrQuestPage() {
  const { teamId } = useParams<{ teamId: string }>();
  if (!teamId) return null;
  return <PrQuestWorkspace teamId={teamId} />;
}
