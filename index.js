import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import apiHandler from './api/index.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default async function handler(req, res) {
  const url = req.url || '/';

  // Route /api/* requests to Serverless API router
  if (url.startsWith('/api/')) {
    return apiHandler(req, res);
  }

  // Determine static file target
  let relPath = url.split('?')[0];
  if (relPath === '/') relPath = '/index.html';

  const filePath = path.join(__dirname, relPath.replace(/^\//, ''));

  // Serve static files if existing
  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    const ext = path.extname(filePath);
    let contentType = 'text/html';
    if (ext === '.js') contentType = 'text/javascript';
    if (ext === '.css') contentType = 'text/css';
    if (ext === '.json' || ext === '.webmanifest') contentType = 'application/manifest+json';
    if (ext === '.svg') contentType = 'image/svg+xml';
    if (ext === '.png') contentType = 'image/png';

    res.setHeader('Content-Type', contentType);
    return res.end(fs.readFileSync(filePath));
  }

  // Default fallback to index.html for single-page routing
  const indexPath = path.join(__dirname, 'index.html');
  if (fs.existsSync(indexPath)) {
    res.setHeader('Content-Type', 'text/html');
    return res.end(fs.readFileSync(indexPath));
  }

  res.statusCode = 404;
  return res.end('404 Not Found');
}
