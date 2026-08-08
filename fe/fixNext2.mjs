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

    if (content.includes('as Route')) {
      content = content.replace(/as Route/g, 'as string');
      changed = true;
    }

    if (content.includes('generateMetadata')) {
      content = content.replace(/export async function generateMetadata[\s\S]*?\n\}/g, '/* generateMetadata removed */');
      changed = true;
    }
    
    if (content.includes('export function generateMetadata')) {
      content = content.replace(/export function generateMetadata[\s\S]*?\n\}/g, '/* generateMetadata removed */');
      changed = true;
    }
    
    if (content.includes('cookies()')) {
        content = content.replace(/cookies\(\)/g, '{ get: () => undefined }');
        changed = true;
    }

    if (changed) {
      fs.writeFileSync(filePath, content);
      console.log('Fixed', filePath);
    }
  }
});
