import assert from "node:assert/strict";
import test from "node:test";

import {
  chooseTraffic,
  loadOptions,
  supportedProfiles,
  trafficWeights,
} from "../load/profile-config.mjs";

test("declares every mandatory load profile", () => {
  assert.deepEqual(supportedProfiles, [
    "smoke",
    "ramp",
    "spike",
    "soak",
    "cold-cache",
    "degraded",
  ]);
});

test("traffic selector follows the documented 70/15/8/5/2 mix", () => {
  assert.equal(
    Object.values(trafficWeights).reduce((total, value) => total + value, 0),
    100,
  );
  assert.equal(chooseTraffic(0), "publicRead");
  assert.equal(chooseTraffic(0.6999), "publicRead");
  assert.equal(chooseTraffic(0.7), "readingWrite");
  assert.equal(chooseTraffic(0.85), "search");
  assert.equal(chooseTraffic(0.93), "community");
  assert.equal(chooseTraffic(0.98), "teamOrWallet");
  assert.throws(() => chooseTraffic(1), /\[0, 1\)/);
});

test("ramp lasts 30 minutes and reaches target RPS", () => {
  const options = loadOptions("ramp", { targetRps: 300 });
  const scenario = options.scenarios.ramp;

  assert.deepEqual(
    scenario.stages.map(({ duration }) => duration),
    ["5m", "20m", "5m"],
  );
  assert.equal(Math.max(...scenario.stages.map(({ target }) => target)), 300);
  assert.equal(scenario.executor, "ramping-arrival-rate");
});

test("spike reaches configured multiplier then recovers", () => {
  const options = loadOptions("spike", {
    targetRps: 300,
    spikeMultiplier: 5,
  });
  const targets = options.scenarios.spike.stages.map(({ target }) => target);

  assert.deepEqual(targets, [300, 1500, 300, 0]);
});

test("soak defaults to eight hours at MVP peak target", () => {
  const scenario = loadOptions("soak").scenarios.soak;

  assert.equal(scenario.executor, "constant-arrival-rate");
  assert.equal(scenario.rate, 300);
  assert.equal(scenario.duration, "8h");
});

test("cold-cache creates a synchronized high-concurrency wave", () => {
  const scenario = loadOptions("cold-cache", {
    targetRps: 300,
  }).scenarios.coldCache;

  assert.equal(scenario.executor, "per-vu-iterations");
  assert.equal(scenario.vus, 300);
  assert.equal(scenario.iterations, 10);
  assert.equal(scenario.exec, "coldCacheTraffic");
});

test("thresholds enforce documented latency and error budgets", () => {
  const thresholds = loadOptions("smoke").thresholds;

  assert.deepEqual(thresholds.http_req_failed, ["rate<0.001"]);
  assert.deepEqual(thresholds.server_error_rate, ["rate<0.001"]);
  assert.ok(
    thresholds["http_req_duration{flow:public_cached}"].includes(
      "p(95)<150",
    ),
  );
  assert.ok(
    thresholds["http_req_duration{flow:public_uncached}"].includes(
      "p(95)<300",
    ),
  );
  assert.ok(
    thresholds["http_req_duration{flow:search}"].includes("p(95)<500"),
  );
  assert.ok(
    thresholds["http_req_duration{flow:wallet}"].includes("p(95)<700"),
  );
});

test("rejects unsafe or unknown profile configuration", () => {
  assert.throws(() => loadOptions("unknown"), /Unsupported PROFILE/);
  assert.throws(
    () => loadOptions("ramp", { targetRps: "0" }),
    /targetRps must be a positive integer/,
  );
  assert.throws(
    () => loadOptions("soak", { soakDuration: "forever" }),
    /Invalid k6 duration/,
  );
});

test("degraded profile gates controlled backpressure separately", () => {
  const thresholds = loadOptions("degraded").thresholds;

  assert.equal(thresholds.http_req_failed, undefined);
  assert.deepEqual(thresholds.controlled_degradation_failure_rate, [
    "rate<0.001",
  ]);
});
