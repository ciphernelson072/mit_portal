const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const staticRoot = path.join(__dirname, '../src/main/resources/static');

function listFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? listFiles(file) : [file];
  });
}

test('static pages and generated markup contain no inline code or style attributes', () => {
  const files = listFiles(staticRoot).filter(file => /\.(html|js)$/.test(file));
  const sources = files.map(file => [file, fs.readFileSync(file, 'utf8')]);

  for (const [file, source] of sources) {
    for (const scriptTag of source.matchAll(/<script\b[^>]*>/gi)) {
      assert.match(scriptTag[0], /\bsrc\s*=/i, `${file} should load scripts externally`);
    }
    assert.doesNotMatch(source, /<style\b/i, `${file} should load styles externally`);
    assert.doesNotMatch(
      source,
      /\s(?:onclick|onchange|oninput|onsubmit|onfocus|onblur|onkeyup|onkeypress)\s*=/i,
      `${file} should use CSP action delegation instead of inline handlers`,
    );
    assert.doesNotMatch(source, /\sstyle\s*=/i, `${file} should use external CSS classes`);
  }
});

test('CSP action markup is restricted to registered handlers', () => {
  const authSource = fs.readFileSync(path.join(staticRoot, 'js/auth.js'), 'utf8');
  const allowlistMatch = authSource.match(/const CSP_ACTION_ALLOWLIST = new Set\((\[[\s\S]*?\])\);/);
  assert.ok(allowlistMatch, 'shared auth script should declare the action allowlist');
  const allowlist = new Set(JSON.parse(allowlistMatch[1]));
  const sources = listFiles(staticRoot)
    .filter(file => /\.(html|js)$/.test(file))
    .map(file => fs.readFileSync(file, 'utf8'));

  for (const source of sources) {
    for (const [, action] of source.matchAll(/data-csp-action=["']([^"'$]+)["']/g)) {
      assert.ok(allowlist.has(action), `CSP action "${action}" should be registered`);
    }
  }
});

test('generated performance report uses its external stylesheet', () => {
  const monitor = fs.readFileSync(path.join(staticRoot, 'js/performance-monitor.js'), 'utf8');
  assert.match(monitor, /\/css\/performance-report\.css/);
  assert.ok(fs.existsSync(path.join(staticRoot, 'css/performance-report.css')));
});
