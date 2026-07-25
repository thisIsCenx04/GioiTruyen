import http from "k6/http";
import { check, fail } from "k6";
import { Rate } from "k6/metrics";
import exec from "k6/execution";
import {
  chooseTraffic,
  loadOptions,
} from "../load/profile-config.mjs";

const baseUrl = (__ENV.BASE_URL || "http://127.0.0.1:8080/api/v1")
  .replace(/\/+$/, "");
const storyId = __ENV.STORY_ID || "";
const chapterId = __ENV.CHAPTER_ID || "";
const teamId = __ENV.TEAM_ID || "";
const bearerToken = __ENV.BEARER_TOKEN || "";
const profile = __ENV.PROFILE || "smoke";
const serverErrorRate = new Rate("server_error_rate");
const degradationFailureRate = new Rate(
  "controlled_degradation_failure_rate",
);

export const options = loadOptions(profile, {
  targetRps: __ENV.TARGET_RPS,
  spikeMultiplier: __ENV.SPIKE_MULTIPLIER,
  soakDuration: __ENV.SOAK_DURATION,
  smokeDuration: __ENV.SMOKE_DURATION,
});

export function setup() {
  const missing = [
    ["STORY_ID", storyId],
    ["CHAPTER_ID", chapterId],
    ["TEAM_ID", teamId],
  ].filter(([, value]) => !uuid(value));

  if (missing.length > 0) {
    fail(
      `Seeded UUID variables are required: ${missing
        .map(([name]) => name)
        .join(", ")}`,
    );
  }

  const health = http.get(`${baseUrl}/livez`, {
    tags: { flow: "control", name: "preflight_liveness" },
  });
  if (health.status !== 200) {
    fail(`Target liveness failed with HTTP ${health.status}`);
  }
  return { runId: `${Date.now()}` };
}

export function mixedTraffic(data) {
  switch (chooseTraffic(Math.random())) {
    case "publicRead":
      return publicRead(data, false);
    case "readingWrite":
      return startReadingSession();
    case "search":
      return searchOrRanking();
    case "community":
      return communityRead();
    default:
      return privateOrTeamRead();
  }
}

export function coldCacheTraffic(data) {
  const stampedeKey =
    `${data.runId}-${exec.vu.iterationInScenario}`;
  publicRead(data, true, stampedeKey);
}

export function degradationTraffic(data) {
  const response = publicRead(data, true, "", true);
  const controlled = [200, 304, 429, 503].includes(response.status);
  const backpressureHasRetry =
    ![429, 503].includes(response.status) ||
    Boolean(response.headers["Retry-After"]);
  degradationFailureRate.add(!controlled || !backpressureHasRetry);
  check(response, {
    "dependency failure degrades in a controlled way": () => controlled,
    "backpressure response includes Retry-After": () => backpressureHasRetry,
  });
}

function publicRead(
  data,
  bypassCache,
  stampedeKey = "",
  allowBackpressure = false,
) {
  const selection = Math.floor(Math.random() * 5);
  const suffix = bypassCache
    ? `?loadTestNonce=${encodeURIComponent(stampedeKey || data.runId)}`
    : "";
  const requests = [
    [`${baseUrl}/home${suffix}`, "home"],
    [`${baseUrl}/stories?limit=20`, "story_list"],
    [`${baseUrl}/stories/${storyId}${suffix}`, "story_detail"],
    [`${baseUrl}/chapters/${chapterId}${suffix}`, "chapter_detail"],
    [`${baseUrl}/teams/${teamId}${suffix}`, "team_detail"],
  ];
  const [url, name] = requests[selection];
  return observedGet(url, {
    headers: bypassCache ? { "Cache-Control": "no-cache" } : {},
    tags: {
      flow: bypassCache ? "public_uncached" : "public_cached",
      name,
    },
  }, allowBackpressure);
}

function startReadingSession() {
  const response = observedRequest(
    "POST",
    `${baseUrl}/reading-sessions`,
    JSON.stringify({
      storyId,
      chapterId,
      anonymousId: deterministicUuid(),
    }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { flow: "write", name: "reading_session_start" },
    },
  );
  check(response, {
    "reading session accepted": (result) => result.status === 201,
  });
}

function searchOrRanking() {
  const selection = Math.floor(Math.random() * 3);
  const paths = [
    ["/search?q=truyen&limit=20", "search"],
    ["/rankings/stories?period=DAY&metric=VALID_VIEWS&limit=20", "story_rank"],
    ["/rankings/teams?period=DAY&metric=VALID_VIEWS&limit=20", "team_rank"],
  ];
  const [path, name] = paths[selection];
  observedGet(`${baseUrl}${path}`, {
    tags: { flow: "search", name },
  });
}

function communityRead() {
  observedGet(
    `${baseUrl}/comments?targetType=story&targetId=${storyId}&limit=20`,
    {
    tags: { flow: "public_uncached", name: "comment_list" },
    },
  );
}

function privateOrTeamRead() {
  if (bearerToken) {
    observedGet(`${baseUrl}/wallets/me`, {
      headers: { Authorization: `Bearer ${bearerToken}` },
      tags: { flow: "wallet", name: "wallet_balance" },
    });
    return;
  }
  observedGet(`${baseUrl}/teams/${teamId}`, {
    tags: { flow: "public_uncached", name: "team_fallback" },
  });
}

function observedGet(url, params, allowBackpressure = false) {
  const response = http.get(url, params);
  observe(response, allowBackpressure);
  return response;
}

function observedRequest(method, url, body, params) {
  const response = http.request(method, url, body, params);
  observe(response);
  return response;
}

function observe(response, allowBackpressure = false) {
  serverErrorRate.add(response.status >= 500);
  check(response, {
    "response is successful": (result) =>
      result.status >= 200 && result.status < 400 ||
      allowBackpressure && [429, 503].includes(result.status),
  });
}

function deterministicUuid() {
  const vu = exec.vu.idInTest.toString(16).padStart(8, "0").slice(-8);
  const iteration = exec.vu.iterationInScenario
    .toString(16)
    .padStart(12, "0")
    .slice(-12);
  return `${vu}-0000-4000-8000-${iteration}`;
}

function uuid(value) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
    .test(value);
}
