import fs from 'fs';
import path from 'path';

function walk(dir, callback) {
  if (!fs.existsSync(dir)) return;
  fs.readdirSync(dir).forEach(f => {
    let dirPath = path.join(dir, f);
    let isDirectory = fs.statSync(dirPath).isDirectory();
    isDirectory ? walk(dirPath, callback) : callback(path.join(dir, f));
  });
}

const dir = './apps/web/src';

walk(dir, (filePath) => {
  if (filePath.endsWith('.tsx') || filePath.endsWith('.ts')) {
    let content = fs.readFileSync(filePath, 'utf8');
    let changed = false;

    // Remove next and next/headers imports
    if (content.includes('from "next"') || content.includes("from 'next'")) {
      content = content.replace(/import type \{ Metadata \} from "next";?\n?/g, '');
      content = content.replace(/import \{ Metadata \} from "next";?\n?/g, '');
      content = content.replace(/import type \{.*?\} from "next";?\n?/g, '');
      changed = true;
    }

    if (content.includes('from "next/headers"')) {
      content = content.replace(/import \{.*?\} from "next\/headers";?\n?/g, '');
      changed = true;
    }

    // Remove metadata exports (simple heuristic: remove 'export const metadata: Metadata = { ... };')
    // We can just comment them out to be safe and avoid breaking syntax
    if (content.includes('export const metadata: Metadata')) {
      content = content.replace(/export const metadata: Metadata = \{[\s\S]*?\};/g, '/* metadata removed */');
      changed = true;
    }
    
    // Fix any remaining <Link ... href=
    if (content.includes('<Link ')) {
        const newContent = content.replace(/(<Link\s+[^>]*?)href=/g, '$1to=');
        if (newContent !== content) {
            content = newContent;
            changed = true;
        }
    }

    if (changed) {
      fs.writeFileSync(filePath, content);
      console.log('Fixed', filePath);
    }
  }
});
