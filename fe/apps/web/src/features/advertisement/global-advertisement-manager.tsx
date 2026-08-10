import { useLocation } from "react-router-dom";

import { useActiveAdvertisement } from "./hooks/useActiveAdvertisement";
import { useGlobalAdvertisement } from "./hooks/useGlobalAdvertisement";

export function GlobalAdvertisementManager() {
  const advertisement = useActiveAdvertisement();
  const location = useLocation();

  useGlobalAdvertisement(advertisement, location.pathname);

  return null;
}
