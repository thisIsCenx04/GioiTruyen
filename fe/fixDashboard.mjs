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

const dir = './apps/web/src/pages/dashboard';

walk(dir, (filePath) => {
  if (filePath.endsWith('.tsx') || filePath.endsWith('.ts')) {
    let content = fs.readFileSync(filePath, 'utf8');
    let changed = false;

    // Fix component imports
    if (content.includes('../../../components/')) {
      content = content.replace(/\.\.\/\.\.\/\.\.\/components\//g, '../components/');
      changed = true;
    }
    if (content.includes('../../components/')) {
        // Only replace if it's pointing outside dashboard. We'll just replace with alias
        content = content.replace(/\.\.\/\.\.\/components\//g, '@/pages/dashboard/components/');
        changed = true;
    }
    if (content.includes('../components/')) {
        content = content.replace(/\.\.\/components\//g, '@/pages/dashboard/components/');
        changed = true;
    }
    
    // next/link -> react-router-dom Link
    if (content.includes('next/link')) {
        content = content.replace(/import Link from 'next\/link';?/g, "import { Link } from 'react-router-dom';");
        content = content.replace(/ href=/g, " to=");
        changed = true;
    }
    
    // next/navigation -> react-router-dom
    if (content.includes('next/navigation')) {
        content = content.replace(/import \{ useRouter, usePathname, useSearchParams \} from 'next\/navigation';?/g, "import { useNavigate as useRouter, useLocation, useSearchParams } from 'react-router-dom';");
        content = content.replace(/import \{ useRouter \} from 'next\/navigation';?/g, "import { useNavigate as useRouter } from 'react-router-dom';");
        content = content.replace(/import \{ usePathname \} from 'next\/navigation';?/g, "import { useLocation } from 'react-router-dom';");
        changed = true;
    }
    
    // Route -> string
    if (content.includes('as Route')) {
        content = content.replace(/as Route/g, 'as string');
        changed = true;
    }

    if (changed) {
      fs.writeFileSync(filePath, content);
      console.log('Fixed', filePath);
    }
  }
});
