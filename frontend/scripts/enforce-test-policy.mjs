import { readdir, readFile } from "node:fs/promises";
import { dirname, extname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDirectory, "../..");
const ignoredDirectories = new Set([
  ".git",
  ".gradle",
  ".next",
  ".tools",
  "build",
  "coverage",
  "node_modules",
  "test-results",
]);

const forbiddenPatterns = [
  {
    code: "focused-or-disabled",
    expression:
      /\b(?:describe|it|test)\.(?:only|skip|todo)\s*\(|@Disabled\b/u,
  },
  {
    code: "real-time-wait",
    expression: /\b(?:Thread\.sleep|setTimeout|setInterval)\s*\(/u,
  },
  {
    code: "direct-network",
    expression:
      /\bfetch\s*\(|\bjava\.net\.|\b(?:HttpClient|RestTemplate|WebClient)\b/u,
  },
  {
    code: "unseeded-random",
    expression: /\bMath\.random\s*\(|\bnew\s+Random\s*\(\s*\)/u,
  },
  {
    code: "wall-clock",
    expression:
      /\b(?:Instant\.now|LocalDateTime\.now|Clock\.system\w*)\s*\(/u,
  },
  {
    code: "test-retry",
    expression:
      /\b(?:retry|retries)\s*[:(]\s*[1-9]\d*|\.(?:retry|retries)\s*\(/u,
  },
];

function usesTestApi(source, extension) {
  if (extension === ".java") {
    return /@(?:Test|Property)\b/u.test(source);
  }

  return /\b(?:describe|it|test)\s*\(/u.test(source);
}

function hasValidTestFileName(filePath) {
  if (filePath.endsWith(".java")) {
    return /(?:Test|Tests|Properties)\.java$/u.test(filePath);
  }

  return /\.test\.[cm]?[jt]sx?$/u.test(filePath);
}

export function inspectTestSource(filePath, source) {
  const violations = [];

  if (
    usesTestApi(source, extname(filePath)) &&
    !hasValidTestFileName(filePath)
  ) {
    violations.push({
      code: "test-file-name",
      filePath,
    });
  }

  for (const rule of forbiddenPatterns) {
    if (rule.expression.test(source)) {
      violations.push({
        code: rule.code,
        filePath,
      });
    }
  }

  return violations;
}

async function collectTestFiles(directory) {
  const files = [];

  for (const entry of await readdir(directory, { withFileTypes: true })) {
    if (entry.isDirectory() && ignoredDirectories.has(entry.name)) {
      continue;
    }

    const entryPath = resolve(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await collectTestFiles(entryPath)));
      continue;
    }

    const normalized = entryPath.replaceAll("\\", "/");
    if (
      normalized.includes("/src/test/") ||
      /\.test\.[cm]?[jt]sx?$/u.test(normalized)
    ) {
      files.push(entryPath);
    }
  }

  return files;
}

export async function validateRepository(root = repositoryRoot) {
  const violations = [];

  for (const filePath of await collectTestFiles(root)) {
    const source = await readFile(filePath, "utf8");
    violations.push(...inspectTestSource(filePath, source));
  }

  return violations;
}

async function main() {
  const violations = await validateRepository();

  if (violations.length > 0) {
    console.error("Test policy violations:");
    for (const violation of violations) {
      console.error(
        `- ${violation.code}: ${relative(repositoryRoot, violation.filePath)}`,
      );
    }
    process.exitCode = 1;
    return;
  }

  console.log("Test policy passed: deterministic, offline, zero retry.");
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : "";
if (invokedPath === fileURLToPath(import.meta.url)) {
  await main();
}
