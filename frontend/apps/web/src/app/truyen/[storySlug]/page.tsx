import type { Metadata } from "next";

import StoryPage, {
  generateMetadata as generateStoryMetadata,
} from "../../stories/[idOrSlug]/page";

type Props = Readonly<{ params: Promise<{ storySlug: string }> }>;

function storyParams(params: Props["params"]) {
  return params.then(({ storySlug }) => ({ idOrSlug: storySlug }));
}

export function generateMetadata({ params }: Props): Promise<Metadata> {
  return generateStoryMetadata({ params: storyParams(params) });
}

export default function CleanStoryPage({ params }: Props) {
  return StoryPage({ params: storyParams(params) });
}
