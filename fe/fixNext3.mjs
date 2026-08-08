import fs from 'fs';
import path from 'path';

// Fix main.tsx
let mainTsx = fs.readFileSync('./apps/web/src/main.tsx', 'utf8');
mainTsx = mainTsx.replace(/from '\.\/App\.tsx'/g, "from './App'");
fs.writeFileSync('./apps/web/src/main.tsx', mainTsx);

// Fix read page
const readPagePaths = [
  './apps/web/src/pages/read/[chapterId]/page.tsx',
  './apps/web/src/pages/truyen/[storySlug]/[chapterSlug]/page.tsx',
  './apps/web/src/pages/stories/[idOrSlug]/page.tsx',
  './apps/web/src/pages/audio/[idOrSlug]/page.tsx',
];

readPagePaths.forEach(p => {
  if (fs.existsSync(p)) {
    let content = fs.readFileSync(p, 'utf8');
    content = content.replace(/return <div>Not Found<\/div>;?/g, 'throw new Error("Not Found");');
    fs.writeFileSync(p, content);
  }
});

// Remove Next.js test file
if (fs.existsSync('./apps/web/src/lib/edge-cache.test.ts')) {
  fs.unlinkSync('./apps/web/src/lib/edge-cache.test.ts');
}

// Add CSS Module types to UI package tsconfig
let uiTsconfig = JSON.parse(fs.readFileSync('./packages/ui/tsconfig.json', 'utf8'));
if (!uiTsconfig.include) uiTsconfig.include = [];
if (!uiTsconfig.include.includes('src/env.d.ts')) {
  uiTsconfig.include.push('src/env.d.ts');
  fs.writeFileSync('./packages/ui/tsconfig.json', JSON.stringify(uiTsconfig, null, 2));
}

console.log('Fixes applied');
