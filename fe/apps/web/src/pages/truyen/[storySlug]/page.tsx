import StoryPage from "../../stories/[idOrSlug]/page";

export default async function CleanStoryPage({ params, searchParams }: any) {
  const resolvedParams = await params;
  const newParams = Promise.resolve({ idOrSlug: resolvedParams.storySlug });
  return StoryPage({ params: newParams, searchParams });
}
