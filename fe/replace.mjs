import fs from 'fs';
import path from 'path';

function walk(dir, callback) {
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

    // Replace next/link
    if (content.includes('import Link from "next/link"')) {
      content = content.replace(/import Link from "next\/link";?/g, 'import { Link } from "react-router-dom";');
      content = content.replace(/<Link\s+href=/g, '<Link to=');
      changed = true;
    }

    // Replace next/image
    if (content.includes('import Image from "next/image"')) {
      content = content.replace(/import Image from "next\/image";?/g, '');
      content = content.replace(/<Image/g, '<img');
      changed = true;
    }

    // Replace next/navigation
    if (content.includes('next/navigation')) {
      content = content.replace(/import \{.*?\} from "next\/navigation";?/g, 'import { useNavigate, useLocation, useParams } from "react-router-dom";');
      
      // useRouter -> useNavigate
      content = content.replace(/const router = useRouter\(\);/g, 'const navigate = useNavigate();');
      content = content.replace(/router\.push\(/g, 'navigate(');
      content = content.replace(/router\.replace\(/g, 'navigate(');
      content = content.replace(/router\.back\(\)/g, 'navigate(-1)');
      
      // usePathname -> useLocation
      content = content.replace(/const pathname = usePathname\(\);/g, 'const location = useLocation(); const pathname = location.pathname;');
      
      changed = true;
    }
    
    // notFound -> Navigate to 404
    if (content.includes('notFound()')) {
        content = content.replace(/notFound\(\)/g, 'return <div>Not Found</div>');
        changed = true;
    }
    
    // redirect -> Navigate
    if (content.includes('redirect(')) {
        content = content.replace(/redirect\((.*?)\)/g, 'navigate($1)');
        changed = true;
    }

    if (changed) {
      fs.writeFileSync(filePath, content);
      console.log('Updated', filePath);
    }
  }
});
