import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const frontendDirectory = path.resolve(scriptDirectory, "..");
const repositoryDirectory = path.resolve(frontendDirectory, "..");
const fixturePath = path.join(
  repositoryDirectory,
  "api",
  "test",
  "invalid-openapi.yaml.fixture",
);
const configPath = path.join(repositoryDirectory, "redocly.yaml");
const redoclyCliPath = path.join(
  frontendDirectory,
  "node_modules",
  "@redocly",
  "cli",
  "bin",
  "cli.js",
);

const result = spawnSync(
  process.execPath,
  [
    redoclyCliPath,
    "lint",
    fixturePath,
    "--config",
    configPath,
    "--format=stylish",
  ],
  {
    cwd: frontendDirectory,
    encoding: "utf8",
    windowsHide: true,
  },
);

if (result.error) {
  throw result.error;
}

const output = `${result.stdout ?? ""}\n${result.stderr ?? ""}`;

if (result.status === 0) {
  throw new Error(
    "OpenAPI quality gate accepted the intentionally invalid fixture.",
  );
}

if (!output.includes("operation-operationId")) {
  throw new Error(
    "OpenAPI quality gate failed, but not for the seeded operationId violation.",
  );
}

console.log("OpenAPI quality gate rejected the seeded contract violation.");
