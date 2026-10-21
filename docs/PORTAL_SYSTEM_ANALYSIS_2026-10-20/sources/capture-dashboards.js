const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright');

const projectRoot = path.resolve(__dirname, '..', '..', '..');
const staticRoot = path.join(projectRoot, 'src', 'main', 'resources', 'static');
const outputRoot = path.resolve(__dirname, '..', 'screenshots');
const roles = [
  { route: '/dashboard.html', output: 'student-dashboard.png', fill: fillStudent, height: 900 },
  { route: '/teacher-dashboard.html', output: 'teacher-dashboard.png', fill: fillTeacher, height: 900 },
  { route: '/admin-dashboard.html', output: 'administrator-dashboard.png', fill: fillAdmin, height: 900 },
];

function fillStudent() {
  document.querySelector('.student-name').textContent = 'Demo Learner';
  document.querySelector('#currentDate').textContent = 'Tuesday, 20 October 2026 · Semester 1';
  const stats = document.querySelectorAll('.quick-stats .stat-value');
  ['3.72', '94%', '48', 'Semester 1'].forEach((value, index) => { stats[index].textContent = value; });
  const profile = document.querySelector('.program-info');
  profile.innerHTML = '<h3>Program Information</h3><p><strong>Reg No:</strong> DEMO-2026-0142</p><p><strong>Program:</strong> BSc Information Technology</p><p><strong>Enrollment:</strong> Year 2</p><p><strong>Status:</strong> <span>Active</span></p>';
  const cards = [...document.querySelectorAll('.dashboard-grid .card-content')];
  const content = [
    '<ul><li>Database Systems <span>CS 2201</span></li><li>Web Application Development <span>CS 2204</span></li><li>Systems Analysis <span>CS 2207</span></li></ul>',
    '<ul><li>Database Systems <strong>A−</strong></li><li>Discrete Mathematics <strong>B+</strong></li></ul>',
    '<p><strong>94%</strong> attendance across 32 recorded classes</p>',
    '<ul><li>Systems Analysis · project brief <span>23 Oct</span></li><li>Database Systems · lab report <span>27 Oct</span></li></ul>',
    '<p>Tuition balance: <strong>UGX 450,000</strong></p><p>Next due date: 30 October 2026</p>',
    '<p><strong>Registration window open</strong></p><p>Semester 1 course registration is available until 31 October.</p>',
  ];
  cards.forEach((card, index) => { card.innerHTML = content[index]; });
  const links = document.querySelector('#personalQuickLinksList');
  if (links) links.innerHTML = '<a class="module-card" href="#"><div class="module-icon">📅</div><h3>My timetable</h3><p>Today · 2 scheduled classes</p></a>';
}

function fillTeacher() {
  document.querySelector('#teacherWelcome').textContent = 'Welcome, Demo Lecturer · School of Computing';
  const stats = {
    assignedCourseCount: '4',
    enrolledStudentCount: '126',
    pendingGradeCount: '8',
    upcomingAssignmentCount: '3',
    pendingAttendanceCount: '2',
  };
  for (const [key, value] of Object.entries(stats)) {
    const node = document.querySelector(`[data-teacher-stat="${key}"]`);
    if (node) node.textContent = value;
  }
  const course = document.querySelector('#rosterCourseSelect');
  course.innerHTML = '<option value="demo" selected>Database Systems · CS 2201</option>';
  const table = document.querySelector('#rosterTable');
  table.hidden = false;
  table.querySelector('tbody').innerHTML = '<tr><td>DL</td><td>Demo Learner One</td><td>DEMO-2026-0142</td><td>3.72</td><td>View</td></tr><tr><td>DT</td><td>Demo Learner Two</td><td>DEMO-2026-0198</td><td>3.48</td><td>View</td></tr><tr><td>DR</td><td>Demo Learner Three</td><td>DEMO-2026-0215</td><td>3.61</td><td>View</td></tr>';
  const rosterExport = document.querySelector('#exportTeacherRoster');
  if (rosterExport) rosterExport.disabled = false;
  const gradeList = document.querySelector('#teacherGradeList');
  if (gradeList) gradeList.innerHTML = '<p>Recent submissions · Database Systems</p><ul><li>Demo Learner One · A− · Pending review</li><li>Demo Learner Two · B+ · Approved</li></ul>';
}

function fillAdmin() {
  const values = ['1,248', '42', '86', 'UGX 284.6M'];
  document.querySelectorAll('#dashboardModule .stats-grid .stat-value').forEach((node, index) => {
    if (values[index]) node.textContent = values[index];
  });
  const changes = ['+18 this intake', '36 published', '4 departments', 'Semester 1'];
  document.querySelectorAll('#dashboardModule .stats-grid .stat-change').forEach((node, index) => {
    if (changes[index]) node.textContent = changes[index];
  });
  const rows = document.querySelector('#dashboardModule .dashboard-grid .table tbody');
  rows.innerHTML = '<tr><td>Demo Learner One</td><td>DEMO-2026-0142</td><td>Year 2</td><td><span class="status-badge success">Active</span></td></tr><tr><td>Demo Learner Two</td><td>DEMO-2026-0198</td><td>Year 1</td><td><span class="status-badge success">Active</span></td></tr><tr><td>Demo Learner Three</td><td>DEMO-2026-0215</td><td>Year 3</td><td><span class="status-badge pending">Review</span></td></tr>';
  document.querySelector('#pendingTaskCount').textContent = '5';
  document.querySelector('#pendingActionsList').innerHTML = '<div class="task-item"><div class="task-info"><p class="task-title">3 admission applications awaiting review</p></div></div><div class="task-item"><div class="task-info"><p class="task-title">2 grade submissions awaiting approval</p></div></div>';
  for (const id of ['databaseHealthStatus', 'apiHealthStatus']) {
    const node = document.getElementById(id);
    node.textContent = 'Available';
    node.classList.add('success');
  }
  for (const id of ['emailHealthStatus', 'backupHealthStatus', 'uploadBackupHealthStatus']) {
    const node = document.getElementById(id);
    node.textContent = 'Not verified';
    node.classList.add('warning');
  }
  const email = document.querySelector('#adminProfileEmail');
  if (email) email.textContent = 'admin.demo@example.invalid';
}

function startStaticServer() {
  const mime = {
    '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8', '.jpg': 'image/jpeg',
    '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2',
  };
  const server = http.createServer((request, response) => {
    const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    if (pathname.startsWith('/js/')) {
      response.writeHead(200, { 'Content-Type': 'text/javascript; charset=utf-8' });
      response.end('');
      return;
    }
    const target = path.resolve(staticRoot, `.${pathname}`);
    if (!target.startsWith(staticRoot + path.sep) && target !== staticRoot) {
      response.writeHead(403).end();
      return;
    }
    fs.readFile(target, (error, body) => {
      if (error) {
        response.writeHead(404).end();
        return;
      }
      response.writeHead(200, { 'Content-Type': mime[path.extname(target).toLowerCase()] || 'application/octet-stream' });
      response.end(body);
    });
  });
  return new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve(server)));
}

async function main() {
  fs.mkdirSync(outputRoot, { recursive: true });
  const server = await startStaticServer();
  const address = server.address();
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1600, height: 900 }, deviceScaleFactor: 1 });
    for (const role of roles) {
      await page.setViewportSize({ width: 1600, height: role.height });
      await page.goto(`http://127.0.0.1:${address.port}${role.route}`, { waitUntil: 'networkidle' });
      if (role.route === '/teacher-dashboard.html') {
        await page.addStyleTag({ content: '.teacher-content > section { flex: 0 0 auto; overflow: visible; } .teacher-summary #teacherDashboardStatus:empty { display: none; }' });
      }
      await page.evaluate(role.fill);
      await page.evaluate(() => {
        window.scrollTo(0, 0);
        document.documentElement.scrollTop = 0;
        document.body.scrollTop = 0;
        document.querySelector('.teacher-content')?.scrollTo(0, 0);
        const label = document.createElement('div');
        label.textContent = 'ILLUSTRATIVE MOCK DATA · NOT A LIVE USER ACCOUNT';
        label.style.cssText = 'position:fixed;z-index:99999;right:18px;bottom:14px;padding:9px 13px;border-radius:7px;background:#17324d;color:#fff;font:700 12px Segoe UI,Arial,sans-serif;letter-spacing:.04em;box-shadow:0 3px 12px #0003';
        document.body.append(label);
      });
      const output = path.join(outputRoot, role.output);
      await page.screenshot({ path: output, fullPage: false, animations: 'disabled' });
      console.log(`Captured ${role.output}`);
    }
  } finally {
    await browser.close();
    await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  }
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
