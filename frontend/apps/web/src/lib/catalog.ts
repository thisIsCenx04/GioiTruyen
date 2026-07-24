import {
  createPublicCatalogClient,
  type HomeResponse,
  type HomeStorySummary,
} from "@gioitruyen/api-client";

const apiBaseUrl = (
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/u, "");

export const catalog = createPublicCatalogClient({ baseUrl: apiBaseUrl });

const sampleStories: readonly HomeStorySummary[] = [
  {
    coverAssetId: null,
    id: "10000000-0000-4000-8000-000000000001",
    publishedAt: "2026-07-24T00:00:00Z",
    slug: "nguoi-chep-su-cuoi-cung",
    teamId: "20000000-0000-4000-8000-000000000001",
    title: "Người Chép Sử Cuối Cùng",
  },
  {
    coverAssetId: null,
    id: "10000000-0000-4000-8000-000000000002",
    publishedAt: "2026-07-23T00:00:00Z",
    slug: "thanh-pho-khong-ngu",
    teamId: "20000000-0000-4000-8000-000000000002",
    title: "Thành Phố Không Ngủ",
  },
  {
    coverAssetId: null,
    id: "10000000-0000-4000-8000-000000000003",
    publishedAt: "2026-07-22T00:00:00Z",
    slug: "ban-thao-mau-luc",
    teamId: "20000000-0000-4000-8000-000000000003",
    title: "Bản Thảo Màu Lục",
  },
];

export const fallbackHome: HomeResponse = {
  generatedAt: "2026-07-24T00:00:00Z",
  locale: "vi-VN",
  sections: [
    {
      id: "latest",
      stories: sampleStories,
      title: "Mới cập nhật",
      type: "LATEST",
    },
    {
      id: "completed",
      stories: sampleStories.slice().reverse(),
      title: "Đã hoàn thành",
      type: "COMPLETED",
    },
    {
      id: "original",
      stories: sampleStories.slice(1),
      title: "Truyện sáng tác",
      type: "ORIGINAL",
    },
  ],
  version: "preview",
};

export async function loadHome() {
  try {
    return await catalog.home();
  } catch {
    return fallbackHome;
  }
}
