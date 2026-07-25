const sharedThresholds = Object.freeze({
  http_req_failed: ["rate<0.001"],
  checks: ["rate>0.99"],
  "http_req_duration{flow:public_cached}": ["p(95)<150", "p(99)<300"],
  "http_req_duration{flow:public_uncached}": ["p(95)<300", "p(99)<600"],
  "http_req_duration{flow:search}": ["p(95)<500", "p(99)<1000"],
  "http_req_duration{flow:write}": ["p(95)<500", "p(99)<1000"],
  "http_req_duration{flow:wallet}": ["p(95)<700", "p(99)<1400"],
  server_error_rate: ["rate<0.001"],
});

const degradedThresholds = Object.freeze({
  checks: ["rate>0.99"],
  controlled_degradation_failure_rate: ["rate<0.001"],
  "http_req_duration{flow:public_uncached}": ["p(95)<1000", "p(99)<2000"],
});

const defaultTargetRps = 300;

export const supportedProfiles = Object.freeze([
  "smoke",
  "ramp",
  "spike",
  "soak",
  "cold-cache",
  "degraded",
]);

export const trafficWeights = Object.freeze({
  publicRead: 70,
  readingWrite: 15,
  search: 8,
  community: 5,
  teamOrWallet: 2,
});

export function chooseTraffic(draw) {
  if (!Number.isFinite(draw) || draw < 0 || draw >= 1) {
    throw new Error("draw must be in the [0, 1) interval");
  }
  let boundary = 0;
  for (const [flow, weight] of Object.entries(trafficWeights)) {
    boundary += weight / 100;
    if (draw < boundary) {
      return flow;
    }
  }
  throw new Error("traffic weights must total 100");
}

export function loadOptions(profileName, overrides = {}) {
  const profile = profileName || "smoke";
  const targetRps = positiveInteger(
    overrides.targetRps,
    defaultTargetRps,
    "targetRps",
  );
  const spikeMultiplier = positiveInteger(
    overrides.spikeMultiplier,
    5,
    "spikeMultiplier",
  );
  const soakDuration = duration(overrides.soakDuration, "8h");
  const smokeDuration = duration(overrides.smokeDuration, "30s");

  const scenarios = {
    smoke: {
      smoke: constantRate(
        Math.min(targetRps, 10),
        smokeDuration,
        "mixedTraffic",
        10,
        50,
      ),
    },
    ramp: {
      ramp: {
        executor: "ramping-arrival-rate",
        exec: "mixedTraffic",
        startRate: Math.max(1, Math.floor(targetRps / 20)),
        timeUnit: "1s",
        preAllocatedVUs: Math.max(50, targetRps),
        maxVUs: Math.max(200, targetRps * 4),
        stages: [
          { target: Math.floor(targetRps / 3), duration: "5m" },
          { target: targetRps, duration: "20m" },
          { target: 0, duration: "5m" },
        ],
      },
    },
    spike: {
      spike: {
        executor: "ramping-arrival-rate",
        exec: "mixedTraffic",
        startRate: Math.max(1, Math.floor(targetRps / 3)),
        timeUnit: "1s",
        preAllocatedVUs: Math.max(100, targetRps),
        maxVUs: Math.max(500, targetRps * spikeMultiplier * 3),
        stages: [
          { target: targetRps, duration: "1m" },
          {
            target: targetRps * spikeMultiplier,
            duration: "30s",
          },
          { target: targetRps, duration: "2m" },
          { target: 0, duration: "1m" },
        ],
      },
    },
    soak: {
      soak: constantRate(
        targetRps,
        soakDuration,
        "mixedTraffic",
        Math.max(100, targetRps),
        Math.max(400, targetRps * 3),
      ),
    },
    "cold-cache": {
      coldCache: {
        executor: "per-vu-iterations",
        exec: "coldCacheTraffic",
        vus: Math.max(100, targetRps),
        iterations: 10,
        maxDuration: "5m",
      },
    },
    degraded: {
      degraded: constantRate(
        Math.max(10, Math.floor(targetRps / 3)),
        "10m",
        "degradationTraffic",
        50,
        Math.max(200, targetRps),
      ),
    },
  };

  if (!Object.hasOwn(scenarios, profile)) {
    throw new Error(
      `Unsupported PROFILE "${profile}". Expected: ${supportedProfiles.join(", ")}`,
    );
  }

  return {
    discardResponseBodies: true,
    scenarios: scenarios[profile],
    thresholds: profile === "degraded"
      ? degradedThresholds
      : sharedThresholds,
    summaryTrendStats: ["avg", "med", "p(90)", "p(95)", "p(99)", "max"],
  };
}

function constantRate(
  rate,
  selectedDuration,
  exec,
  preAllocatedVUs,
  maxVUs,
) {
  return {
    executor: "constant-arrival-rate",
    exec,
    rate,
    duration: selectedDuration,
    timeUnit: "1s",
    preAllocatedVUs,
    maxVUs,
  };
}

function positiveInteger(value, fallback, name) {
  if (value === undefined || value === null || value === "") {
    return fallback;
  }
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 1) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}

function duration(value, fallback) {
  const candidate = value || fallback;
  if (!/^[1-9]\d*(?:s|m|h)$/.test(candidate)) {
    throw new Error(`Invalid k6 duration "${candidate}"`);
  }
  return candidate;
}
