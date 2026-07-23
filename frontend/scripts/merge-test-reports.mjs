import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const frontendRoot = resolve(scriptDirectory, "..");
const repositoryRoot = resolve(frontendRoot, "..");
const outputPath = resolve(frontendRoot, "test-results/merged-junit.xml");

function readNumberAttribute(xml, attribute) {
  const match = xml.match(new RegExp(`\\b${attribute}="([0-9.]+)"`, "u"));
  return match ? Number(match[1]) : 0;
}

export function mergeJUnitSuites(reports) {
  const suites = reports.flatMap((report) => {
    return report.match(/<testsuite\b[\s\S]*?<\/testsuite>/gu) ?? [];
  });

  if (suites.length === 0) {
    throw new Error("No JUnit test suites were found.");
  }

  const totals = suites.reduce(
    (summary, suite) => ({
      errors: summary.errors + readNumberAttribute(suite, "errors"),
      failures: summary.failures + readNumberAttribute(suite, "failures"),
      skipped: summary.skipped + readNumberAttribute(suite, "skipped"),
      tests: summary.tests + readNumberAttribute(suite, "tests"),
      time: summary.time + readNumberAttribute(suite, "time"),
    }),
    { errors: 0, failures: 0, skipped: 0, tests: 0, time: 0 },
  );

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    `<testsuites name="GioiTruyen" tests="${totals.tests}" failures="${totals.failures}" errors="${totals.errors}" skipped="${totals.skipped}" time="${totals.time.toFixed(3)}">`,
    ...suites,
    "</testsuites>",
    "",
  ].join("\n");
}

async function xmlFiles(directory) {
  try {
    return (await readdir(directory, { withFileTypes: true }))
      .filter((entry) => entry.isFile() && entry.name.endsWith(".xml"))
      .map((entry) => resolve(directory, entry.name));
  } catch (error) {
    if (error && typeof error === "object" && error.code === "ENOENT") {
      return [];
    }
    throw error;
  }
}

async function main() {
  const backendTest = resolve(repositoryRoot, "backend/build/test-results/test");
  const backendUnit = resolve(
    repositoryRoot,
    "backend/build/test-results/unitTest",
  );
  const preferredBackendFiles = await xmlFiles(backendTest);
  const backendFiles =
    preferredBackendFiles.length > 0
      ? preferredBackendFiles
      : await xmlFiles(backendUnit);
  const frontendFiles = (await xmlFiles(resolve(frontendRoot, "test-results")))
    .filter((filePath) => filePath !== outputPath);
  const inputFiles = [...backendFiles, ...frontendFiles];

  if (inputFiles.length === 0) {
    throw new Error("Run backend and frontend tests before merging reports.");
  }

  const reports = await Promise.all(
    inputFiles.map((filePath) => readFile(filePath, "utf8")),
  );
  const mergedReport = mergeJUnitSuites(reports);

  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, mergedReport, "utf8");
  console.log(`Merged ${inputFiles.length} JUnit report(s) into ${outputPath}.`);
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : "";
if (invokedPath === fileURLToPath(import.meta.url)) {
  await main();
}
