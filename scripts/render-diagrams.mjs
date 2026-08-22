// Renders every ```mermaid block in the docs to an SVG beside them.
//
// The markdown keeps the mermaid source - that is what stays reviewable in a
// diff - and the SVG is generated from it, so the picture can never drift away
// from the definition it came from.
import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync, readdirSync, writeFileSync, rmSync } from "node:fs";
import { basename, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = fileURLToPath(new URL(".", import.meta.url));
const DOCS = join(HERE, "..");

const TMP = "./mmd";

rmSync(TMP, { force: true, recursive: true });
mkdirSync(TMP, { recursive: true });

/** A stable, readable file name from the heading the diagram sits under. */
function slug(text) {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/gu, "")
    .replace(/đ/giu, "d")
    .toLowerCase()
    .replace(/[^a-z0-9]+/gu, "-")
    .replace(/^-+|-+$/gu, "")
    .slice(0, 48);
}

let total = 0;

for (const folder of ["architecture", "featured"]) {
  const dir = join(DOCS, folder);
  const images = join(dir, "images");
  mkdirSync(images, { recursive: true });

  for (const file of readdirSync(dir).filter((name) => name.endsWith(".md"))) {
    const source = readFileSync(join(dir, file), "utf8");
    const lines = source.split("\n");

    let heading = basename(file, ".md");
    let index = 0;
    let block = null;

    for (const line of lines) {
      const isHeading = /^#{1,3}\s+/u.test(line);
      if (isHeading && block == null) {
        heading = line.replace(/^#+\s+/u, "").trim();
      }
      if (line.trim() === "```mermaid") {
        block = [];
        continue;
      }
      if (block != null && line.trim() === "```") {
        index += 1;
        const name = `${basename(file, ".md")}-${index}-${slug(heading)}`;
        const input = join(TMP, `${name}.mmd`);
        writeFileSync(input, block.join("\n"), "utf8");
        const output = join(images, `${name}.png`);
        // PNG, not SVG. mermaid-cli puts label text inside <foreignObject>, which a
        // browser renders but GitHub's SVG sanitiser strips - the diagrams arrived as
        // empty boxes. A raster image has the text baked in and shows anywhere.
        // windowsHide: mỗi lần gọi npx qua shell trên Windows bật một cửa sổ
        // console nhấp nháy - với 25 sơ đồ là 25 cửa sổ nảy lên trước mặt
        // người đang chạy script. Cờ này giấu chúng đi.
        //
        // shell vẫn cần: Node 24 từ chối spawn thẳng một .cmd (EINVAL), mà npx
        // trên Windows chính là npx.cmd.
        execFileSync("npx", [
          "--yes", "@mermaid-js/mermaid-cli@11",
          "-i", input, "-o", output,
          "-b", "white", "-c", join(HERE, "mermaid-config.json"), "-w", "1600", "-s", "2",
        ], { shell: true, stdio: "pipe", windowsHide: true });
        console.log(`  ${folder}/images/${basename(output)}`);
        total += 1;
        block = null;
        continue;
      }
      if (block != null) block.push(line);
    }
  }
}

rmSync(TMP, { force: true, recursive: true });
console.log(`\n${total} sơ đồ đã render.`);
