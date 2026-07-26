import {
  createPublicCatalogClient,
} from "@gioitruyen/api-client";

const apiBaseUrl = (
  process.env.API_INTERNAL_URL ?? "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/u, "");

export const catalog = createPublicCatalogClient({ baseUrl: apiBaseUrl });

export async function loadHome() {
  const [home, taxonomy] = await Promise.all([
    catalog.home(),
    catalog.categories(),
  ]);
  return { ...home, taxonomy };
}
