import { useEffect, useState } from "react";

import { getGlobalAdvertisement } from "../api/advertisementApi";
import type { GlobalAdvertisement } from "../types/advertisement";

const REFRESH_INTERVAL_MS = 5 * 60 * 1000;

export function useActiveAdvertisement() {
  const [advertisement, setAdvertisement] = useState<GlobalAdvertisement | null>(null);

  useEffect(() => {
    let active = true;
    let controller = new AbortController();

    const load = async () => {
      controller.abort();
      controller = new AbortController();
      const next = await getGlobalAdvertisement(controller.signal);
      if (active) {
        setAdvertisement(next);
      }
    };

    void load();
    const interval = window.setInterval(() => void load(), REFRESH_INTERVAL_MS);

    return () => {
      active = false;
      controller.abort();
      window.clearInterval(interval);
    };
  }, []);

  return advertisement;
}
