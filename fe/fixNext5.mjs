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

walk('./apps/web/src', (filePath) => {
  if (filePath.endsWith('.tsx') || filePath.endsWith('.ts')) {
    let content = fs.readFileSync(filePath, 'utf8');
    let changed = false;

    // 1. auth-journeys.tsx
    if (filePath.includes('auth-journeys.tsx')) {
        if (content.includes('const searchParams = useSearchParams();')) {
            content = content.replace(/const searchParams = useSearchParams\(\);/g, 'const [searchParams] = useSearchParams();');
            changed = true;
        }
    }
    
    // 2. chapter-unlock.tsx
    if (filePath.includes('chapter-unlock.tsx')) {
        if (content.includes('router') && !content.includes('const router')) {
            content = content.replace(/import \{ Link \} from "react-router-dom";/, 'import { Link, useNavigate } from "react-router-dom";');
            content = content.replace(/export function ChapterUnlock\(.*?\{/g, '$&\n  const router = useNavigate();');
            // If already replaced router with navigate in dependency array, we might still have `router.push` somewhere?
            content = content.replace(/router\.push/g, 'router');
            changed = true;
        }
    }
    
    // 3. Route
    if (content.includes('as Route')) {
        content = content.replace(/as Route/g, 'as string');
        changed = true;
    }
    
    // 4. Admin CRUD workspaces
    if (filePath.includes('admin-crud-workspaces.tsx') && content.includes('next/navigation')) {
        content = content.replace(/import \{ usePathname, useRouter, useSearchParams \} from 'next\/navigation';/, "import { useLocation as usePathname, useNavigate as useRouter, useSearchParams } from 'react-router-dom';");
        changed = true;
    }
    
    // 5. moderation-console.tsx
    if (filePath.includes('moderation-console.tsx') && content.includes('<a') && content.includes('to=')) {
        content = content.replace(/<a([^>]*)to=/g, '<a$1href=');
        changed = true;
    }
    
    // 6. monetization-review-console.tsx
    if (filePath.includes('monetization-review-console.tsx') && content.includes('next/link')) {
        content = content.replace(/import Link from 'next\/link';/, "import { Link } from 'react-router-dom';");
        content = content.replace(/href=/g, 'to=');
        changed = true;
    }
    
    // 7. cookies
    if (content.includes('{ get: () => undefined }')) {
        content = content.replace(/\{ get: \(\) => undefined \}/g, '{ get: (name: string) => undefined }');
        changed = true;
    }
    
    // 8. rankings
    if (filePath.includes('rankings') && content.includes('priority')) {
        content = content.replace(/ priority/g, '');
        content = content.replace(/priority=\{true\}/g, '');
        changed = true;
    }
    
    // 9. generateMetadata import
    if (filePath.includes('truyen') && content.includes('generateMetadata')) {
        content = content.replace(/import \{ generateMetadata \} from ".*";\n?/g, '');
        content = content.replace(/import generateMetadata from ".*";\n?/g, '');
        changed = true;
    }

    if (changed) {
      fs.writeFileSync(filePath, content);
      console.log('Fixed', filePath);
    }
  }
});
