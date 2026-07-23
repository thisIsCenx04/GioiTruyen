import { readdir, readFile } from "node:fs/promises";
import { dirname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDirectory, "../..");
const workflowDirectory = resolve(repositoryRoot, ".github/workflows");
const commitShaPattern = /^[a-f0-9]{40}$/u;
const imageDigestPattern = /^sha256:[a-f0-9]{64}$/u;

export function inspectActionReference(reference) {
  if (reference.startsWith("./")) {
    return null;
  }

  const separator = reference.lastIndexOf("@");
  if (separator < 1) {
    return "action reference has no immutable revision";
  }

  const source = reference.slice(0, separator);
  const revision = reference.slice(separator + 1);
  if (source.startsWith("docker://")) {
    return imageDigestPattern.test(revision)
      ? null
      : "container action is not pinned to a SHA-256 digest";
  }

  return commitShaPattern.test(revision)
    ? null
    : "GitHub Action is not pinned to a full commit SHA";
}

export function inspectWorkflowSource(filePath, source) {
  const violations = [];

  for (const [index, line] of source.split(/\r?\n/u).entries()) {
    const match = line.match(
      /^\s*(?:-\s*)?uses:\s*["']?([^"'#\s]+)["']?/u,
    );
    if (!match) {
      continue;
    }

    const message = inspectActionReference(match[1]);
    if (message) {
      violations.push({
        filePath,
        line: index + 1,
        message,
        reference: match[1],
      });
    }
  }

  return violations;
}

async function main() {
  const violations = [];
  const entries = await readdir(workflowDirectory, {
    withFileTypes: true,
  });

  for (const entry of entries) {
    if (
      !entry.isFile() ||
      (!entry.name.endsWith(".yml") && !entry.name.endsWith(".yaml"))
    ) {
      continue;
    }

    const filePath = resolve(workflowDirectory, entry.name);
    const source = await readFile(filePath, "utf8");
    violations.push(...inspectWorkflowSource(filePath, source));
  }

  if (violations.length > 0) {
    console.error("Mutable GitHub Action references detected:");
    for (const violation of violations) {
      console.error(
        `- ${relative(repositoryRoot, violation.filePath)}:${violation.line} `
          + `${violation.reference}: ${violation.message}`,
      );
    }
    process.exitCode = 1;
    return;
  }

  console.log("GitHub Actions policy passed: all external actions are pinned.");
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : "";
if (invokedPath === fileURLToPath(import.meta.url)) {
  await main();
}
