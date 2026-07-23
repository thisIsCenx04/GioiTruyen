import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const vitestEntry = fileURLToPath(
  new URL("../node_modules/vitest/vitest.mjs", import.meta.url),
);
const nodeArguments = [];
const inheritedNodeOptions = process.env.NODE_OPTIONS?.trim();
let nodeOptions = inheritedNodeOptions;

if (
  process.allowedNodeEnvironmentFlags.has("--no-experimental-webstorage")
) {
  nodeArguments.push("--no-experimental-webstorage");
  nodeOptions = [
    inheritedNodeOptions,
    "--no-experimental-webstorage",
  ].filter(Boolean).join(" ");
}

const result = spawnSync(
  process.execPath,
  [...nodeArguments, vitestEntry, ...process.argv.slice(2)],
  {
    env: {
      ...process.env,
      LANG: "en_US.UTF-8",
      LC_ALL: "en_US.UTF-8",
      ...(nodeOptions ? { NODE_OPTIONS: nodeOptions } : {}),
      TZ: "UTC",
    },
    stdio: "inherit",
  },
);

if (result.error) {
  throw result.error;
}

process.exitCode = result.status ?? 1;
