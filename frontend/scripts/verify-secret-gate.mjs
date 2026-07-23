import { spawnSync } from "node:child_process";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const SEEDED_RULE_ID = "gioitruyen-seeded-secret";
export const SEEDED_EXIT_CODE = 17;

export function buildSeededSecret() {
  return [
    "GIOITRUYEN_TEST_",
    "SECRET_",
    "0123456789abcdef",
    "fedcba9876543210",
  ].join("");
}

export function assertSeededFindingWasBlocked(status, findings) {
  if (status !== SEEDED_EXIT_CODE) {
    throw new Error(
      `Secret gate returned ${String(status)} instead of ${SEEDED_EXIT_CODE}.`,
    );
  }

  if (
    !Array.isArray(findings) ||
    !findings.some((finding) => finding.RuleID === SEEDED_RULE_ID)
  ) {
    throw new Error(
      `Secret gate did not report the seeded rule ${SEEDED_RULE_ID}.`,
    );
  }
}

async function main() {
  const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
  const frontendDirectory = path.resolve(scriptDirectory, "..");
  const repositoryDirectory = path.resolve(frontendDirectory, "..");
  const gitleaksExecutable = process.env.GITLEAKS_BIN ?? "gitleaks";
  const temporaryDirectory = await mkdtemp(
    path.join(os.tmpdir(), "gioitruyen-secret-gate-"),
  );
  const reportPath = path.join(temporaryDirectory, "report.json");

  try {
    await writeFile(
      path.join(temporaryDirectory, "seeded.env"),
      `API_KEY=${buildSeededSecret()}\n`,
      {
        encoding: "utf8",
        mode: 0o600,
      },
    );

    const result = spawnSync(
      gitleaksExecutable,
      [
        "dir",
        temporaryDirectory,
        "--config",
        path.join(repositoryDirectory, ".gitleaks.toml"),
        "--exit-code",
        String(SEEDED_EXIT_CODE),
        "--no-banner",
        "--no-color",
        "--report-format",
        "json",
        "--report-path",
        reportPath,
      ],
      {
        cwd: repositoryDirectory,
        encoding: "utf8",
        windowsHide: true,
      },
    );

    if (result.error) {
      throw result.error;
    }

    const findings = JSON.parse(await readFile(reportPath, "utf8"));
    assertSeededFindingWasBlocked(result.status, findings);
    console.log("Secret gate rejected the deterministic seeded finding.");
  } finally {
    await rm(temporaryDirectory, {
      force: true,
      recursive: true,
    });
  }
}

const invokedPath = process.argv[1] ? path.resolve(process.argv[1]) : "";
if (invokedPath === fileURLToPath(import.meta.url)) {
  await main();
}
