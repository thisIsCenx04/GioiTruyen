import type { GlobalAdvertisement } from "../types/advertisement";

type AdvertisementStorageState = {
  count: number;
  date: string;
  lastOpenedAt: number;
};

function storageKey(adId: string) {
  return `ad_click_${adId}`;
}

function todayKey() {
  return new Date().toISOString().slice(0, 10);
}

function readState(adId: string): AdvertisementStorageState {
  const today = todayKey();
  try {
    const raw = window.localStorage.getItem(storageKey(adId));
    if (!raw) {
      return { count: 0, date: today, lastOpenedAt: 0 };
    }

    const parsed = JSON.parse(raw) as Partial<AdvertisementStorageState>;
    if (parsed.date !== today) {
      return { count: 0, date: today, lastOpenedAt: 0 };
    }

    return {
      count: Number.isFinite(parsed.count) ? Number(parsed.count) : 0,
      date: today,
      lastOpenedAt: Number.isFinite(parsed.lastOpenedAt) ? Number(parsed.lastOpenedAt) : 0,
    };
  } catch {
    return { count: 0, date: today, lastOpenedAt: 0 };
  }
}

function writeState(adId: string, state: AdvertisementStorageState) {
  try {
    window.localStorage.setItem(storageKey(adId), JSON.stringify(state));
  } catch {
    // LocalStorage is a UX guard only; ad flow should fail closed if it is unavailable.
  }
}

export function canOpenAdvertisement(ad: GlobalAdvertisement) {
  if (ad.maxClicksPerDay <= 0) return false;

  const state = readState(ad.id);
  if (state.count >= ad.maxClicksPerDay) return false;

  const elapsedSeconds = Math.floor((Date.now() - state.lastOpenedAt) / 1000);
  return state.lastOpenedAt === 0 || elapsedSeconds >= ad.cooldownSeconds;
}

export function markAdvertisementOpened(ad: GlobalAdvertisement) {
  const state = readState(ad.id);
  writeState(ad.id, {
    count: state.count + 1,
    date: todayKey(),
    lastOpenedAt: Date.now(),
  });
}
