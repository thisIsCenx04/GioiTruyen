import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const allowedFindings = new Set([
  "@img/sharp-win32-x64|Apache-2.0 AND LGPL-3.0-or-later",
  "@img/sharp-libvips-linux-x64|LGPL-3.0-or-later",
]);
const blockingSeverities = new Set(["HIGH", "CRITICAL"]);

export function findBlockingLicenseFindings(report) {
  const findings = (report.Results ?? []).flatMap(
    (result) => result.Licenses ?? [],
  );

  return findings.filter((finding) => {
    if (!blockingSeverities.has(finding.Severity)) {
      return false;
    }

    return !allowedFindings.has(`${finding.PkgName}|${finding.Name}`);
  });
}

async function main() {
  const reportArgument = process.argv[2];
  if (!reportArgument) {
    throw new Error("Path to the Trivy license report is required.");
  }

  const reportPath = path.resolve(reportArgument);
  const report = JSON.parse(await readFile(reportPath, "utf8"));
  const violations = findBlockingLicenseFindings(report);

  if (violations.length > 0) {
    console.error("High or critical restricted licenses detected:");
    for (const violation of violations) {
      console.error(
        `- ${violation.PkgName}: ${violation.Name} `
          + `(${violation.Severity})`,
      );
    }
    process.exitCode = 1;
    return;
  }

  console.log("Dependency license policy passed.");
}

const invokedPath = process.argv[1] ? path.resolve(process.argv[1]) : "";
if (invokedPath === fileURLToPath(import.meta.url)) {
  await main();
}
