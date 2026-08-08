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
      
      const compName = `Wrapped${baseName}`;
      
      if (isAsync) {
        wrappers += `const Async${baseName} = withAsync(${baseName} as any);\n`;
        wrappers += `function ${compName}() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  return <Async${baseName} params={Promise.resolve(params)} searchParams={Promise.resolve(searchParamsObj)} />;
}\n`;
      } else {
        wrappers += `function ${compName}() {
  const params = useParams();
  const location = useLocation();
  const searchParamsObj = Object.fromEntries(new URLSearchParams(location.search));
  // Some non-async components still expect params as a Promise in Next.js 15, or as an object. 
  // We just cast to any to satisfy the component if it takes props.
  return <${baseName} params={Promise.resolve(params) as any} searchParams={Promise.resolve(searchParamsObj) as any} />;
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
  console.log('App.tsx generated successfully with ALL components wrapped!');
}

generateRoutes();
