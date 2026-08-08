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

function generateRoutes() {
  const pagesDir = path.resolve('apps/web/src/pages');
  let imports = `import { BrowserRouter as Router, Routes, Route, Navigate, useParams, useLocation } from 'react-router-dom';\nimport Layout from './pages/layout';\nimport { withAsync } from './lib/withAsync';\n\n`;
  let wrappers = '';
  let routesMap = [];

  walk(pagesDir, (filePath) => {
    if (filePath.endsWith('page.tsx') && !filePath.includes('api\\') && !filePath.includes('api/')) {
      const relPath = path.relative(pagesDir, filePath);
      let routePath = relPath.replace(/\\/g, '/').replace(/\/page\.tsx$/, '').replace(/^page\.tsx$/, '');
      
      routePath = routePath.replace(/\[\.\.\.(.*?)\]/g, '*');
      routePath = routePath.replace(/\[(.*?)\]/g, ':$1');
      
      if (routePath === '') routePath = '/';
      else routePath = '/' + routePath;

      const baseName = 'Page' + Buffer.from(routePath).toString('base64').replace(/[^a-zA-Z0-9]/g, '');
      const importPath = './pages/' + relPath.replace(/\\/g, '/').replace(/\.tsx$/, '');

      imports += `import ${baseName} from '${importPath}';\n`;
      
      const content = fs.readFileSync(filePath, 'utf8');
      const isAsync = content.includes('export default async function') || content.includes('export default async (');
      
      const compName = isAsync ? `Wrapped${baseName}` : baseName;
      
      if (isAsync) {
        wrappers += `const Async${baseName} = withAsync(${baseName} as any);\n`;
        wrappers += `function ${compName}() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <Async${baseName} params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}\n`;
      }
      
      routesMap.push(`<Route path="${routePath}" element={<${compName} />} />`);
    }
  });

  const appContent = `${imports}\n${wrappers}
function App() {
  return (
    <Router>
      <Routes>
        <Route element={<Layout />}>
          ${routesMap.join('\n          ')}
        </Route>
      </Routes>
    </Router>
  );
}
export default App;
`;

  fs.writeFileSync(path.resolve('apps/web/src/App.tsx'), appContent);
  console.log('App.tsx fixed!');
}
generateRoutes();

walk('./apps/web/src', (filePath) => {
  if (filePath.endsWith('.tsx') || filePath.endsWith('.ts')) {
    let content = fs.readFileSync(filePath, 'utf8');
    let changed = false;

    // chapter-unlock duplicate navigate
    if (filePath.includes('chapter-unlock.tsx') && content.includes('import { Link, useNavigate } from "react-router-dom";')) {
        content = content.replace(/import \{ Link, useNavigate \} from "react-router-dom";\n?/g, 'import { Link } from "react-router-dom";\n');
        changed = true;
    }
    
    // Route
    if (content.includes('as Route')) {
        content = content.replace(/as Route/g, 'as string');
        changed = true;
    }
    
    // Admin CRUD workspaces
    if (content.includes('next/navigation')) {
        content = content.replace(/import \{ usePathname, useRouter, useSearchParams \} from 'next\/navigation';/, "import { useLocation as usePathname, useNavigate as useRouter, useSearchParams } from 'react-router-dom';");
        content = content.replace(/import \{ useRouter, usePathname, useSearchParams \} from 'next\/navigation';/, "import { useNavigate as useRouter, useLocation as usePathname, useSearchParams } from 'react-router-dom';");
        content = content.replace(/import \{ useRouter \} from 'next\/navigation';/, "import { useNavigate as useRouter } from 'react-router-dom';");
        changed = true;
    }
    
    // monetization-review-console
    if (content.includes('next/link')) {
        content = content.replace(/import Link from 'next\/link';/g, "import { Link } from 'react-router-dom';");
        content = content.replace(/ href=/g, ' to=');
        changed = true;
    }
    
    // cookies
    if (content.includes('{ get: (name: string) => undefined }')) {
        content = content.replace(/\{ get: \(name: string\) => undefined \}/g, '{ get: (name: string) => ({ value: undefined as string | undefined }) }');
        changed = true;
    }
    if (content.includes('{ get: () => undefined }')) {
        content = content.replace(/\{ get: \(\) => undefined \}/g, '{ get: (name: string) => ({ value: undefined as string | undefined }) }');
        changed = true;
    }
    
    // generateMetadata import
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
