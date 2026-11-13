const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const pack = path.resolve(__dirname, '..');
const imageDir = path.join(pack, 'diagrams');
const esc = value => String(value).replace(/[&<>"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[char]);

function canvas(title, subtitle, width, height, content, footer = 'Code-informed view · 20 October 2026 · selected scope, not exhaustive') {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
  <defs>
    <linearGradient id="bg" x2="0" y2="1"><stop stop-color="#f8fbff"/><stop offset="1" stop-color="#eef4fa"/></linearGradient>
    <filter id="shadow" x="-15%" y="-15%" width="130%" height="140%"><feDropShadow dx="0" dy="7" stdDeviation="9" flood-color="#15324b" flood-opacity=".10"/></filter>
    <marker id="arrow" viewBox="0 0 12 12" refX="10" refY="6" markerWidth="10" markerHeight="10" orient="auto"><path d="M1 1L11 6L1 11Z" fill="#347d91"/></marker>
  </defs>
  <rect width="${width}" height="${height}" fill="url(#bg)"/>
  <text x="64" y="68" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="34" font-weight="700" fill="#17324d">${esc(title)}</text>
  <text x="64" y="104" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="17" fill="#587087">${esc(subtitle)}</text>
  ${content}
  <text x="64" y="${height - 24}" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="13" fill="#718397">${esc(footer)}</text>
</svg>`;
}

function card(x, y, w, h, title, lines = [], tone = 'blue', opts = {}) {
  const colors = {
    blue: ['#e8f2ff', '#2f6f9f'], teal: ['#e3f7f4', '#197d77'],
    gold: ['#fff3d6', '#b37b18'], purple: ['#f1eaff', '#7654a6'],
    gray: ['#edf1f5', '#617487'], white: ['#ffffff', '#2f6f9f'],
    rose: ['#ffe9ec', '#b55c70'],
  };
  const [fill, stroke] = colors[tone] || colors.blue;
  const rx = opts.round ? 24 : 15;
  const titleY = y + 34;
  const body = lines.map((line, index) =>
    `<text x="${x + 20}" y="${titleY + 30 + index * 25}" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="${opts.fontSize || 16}" fill="#40576b">${esc(line)}</text>`
  ).join('');
  return `<g filter="url(#shadow)"><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${fill}" stroke="${stroke}" stroke-width="2"/>
    <text x="${x + 20}" y="${titleY}" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="${opts.titleSize || 19}" font-weight="700" fill="#17324d">${esc(title)}</text>${body}</g>`;
}

function entity(x, y, w, title, fields, tone = 'blue') {
  const height = 44 + fields.length * 25 + 15;
  const colors = { blue: ['#edf5ff', '#4380ad'], teal: ['#e8f8f4', '#20857b'], gold: ['#fff6df', '#b88721'], purple: ['#f2edff', '#7759a7'], gray: ['#eef2f5', '#718394'] };
  const [fill, stroke] = colors[tone] || colors.blue;
  const fieldText = fields.map((field, index) => `<text x="${x + 16}" y="${y + 67 + index * 25}" font-family="Consolas,monospace" font-size="14" fill="#41586d">${esc(field)}</text>`).join('');
  return { x, y, w, h: height, svg: `<g filter="url(#shadow)"><rect x="${x}" y="${y}" width="${w}" height="${height}" rx="12" fill="${fill}" stroke="${stroke}" stroke-width="2"/><path d="M${x + 1} ${y + 48}H${x + w - 1}" stroke="${stroke}" stroke-width="2"/><text x="${x + 16}" y="${y + 32}" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="18" font-weight="700" fill="#17324d">${esc(title)}</text>${fieldText}</g>` };
}

function connect(a, b, label = '', opts = {}) {
  const acx = a.x + a.w / 2, acy = a.y + a.h / 2;
  const bcx = b.x + b.w / 2, bcy = b.y + b.h / 2;
  const dx = bcx - acx, dy = bcy - acy;
  const ar = Math.min((a.w / 2) / Math.max(Math.abs(dx), 1), (a.h / 2) / Math.max(Math.abs(dy), 1));
  const br = Math.min((b.w / 2) / Math.max(Math.abs(dx), 1), (b.h / 2) / Math.max(Math.abs(dy), 1));
  const x1 = acx + dx * ar, y1 = acy + dy * ar;
  const x2 = bcx - dx * br, y2 = bcy - dy * br;
  const mx = (x1 + x2) / 2, my = (y1 + y2) / 2 - 8;
  const line = `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${opts.color || '#56849b'}" stroke-width="${opts.width || 2.5}" ${opts.dashed ? 'stroke-dasharray="9 7"' : ''} ${opts.arrow === false ? '' : 'marker-end="url(#arrow)"'}/>`;
  const labelSvg = label ? `<text x="${mx}" y="${my}" text-anchor="middle" paint-order="stroke" stroke="#f7faff" stroke-width="7" stroke-linejoin="round" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="14" font-weight="600" fill="#365a70">${esc(label)}</text>` : '';
  return line + labelSvg;
}

function actor(x, y, label, color = '#235e7d') {
  return `<g transform="translate(${x} ${y})" stroke="${color}" stroke-width="5" stroke-linecap="round" fill="none">
    <circle cx="0" cy="0" r="15" fill="#e6f3f8"/><path d="M0 16V62M-27 36H27M0 62L-23 94M0 62L23 94"/>
    <text x="0" y="122" text-anchor="middle" stroke="none" fill="#17324d" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="17" font-weight="700">${esc(label)}</text></g>`;
}

function makeArchitecture() {
  const nodes = [
    card(70, 165, 270, 130, 'Student', ['Profile · course · finance'], 'teal'),
    card(400, 165, 270, 130, 'Teacher', ['Roster · grades · attendance'], 'teal'),
    card(730, 165, 300, 130, 'Administrator', ['Setup · review · reporting'], 'teal'),
    card(1110, 150, 350, 160, 'Web browser', ['HTML · CSS · JavaScript', 'Same-origin portal UI and APIs'], 'blue'),
    card(1110, 395, 350, 150, 'HTTPS ingress', ['TLS edge / reverse proxy', 'Loopback-only application origin'], 'purple'),
    card(1110, 650, 350, 190, 'Spring Boot application', ['REST controllers · services', 'Spring Security · validation', 'Flyway-managed schema changes'], 'blue'),
    card(380, 930, 355, 175, 'MySQL-compatible database', ['Local MySQL / TiDB Cloud pilot', 'Users · academic · finance · audit'], 'gold'),
    card(800, 930, 320, 175, 'Private file storage', ['Configured upload directory', 'Profile photos · documents · materials'], 'gold'),
    card(1190, 930, 300, 175, 'Operations', ['Health · metrics · logs', 'Scheduled backup / restore duties'], 'gray'),
    card(1530, 405, 235, 125, 'Quick Tunnel', ['Temporary test ingress', 'Not a production endpoint'], 'rose'),
    card(1530, 665, 235, 145, 'Email provider', ['Optional SMTP / Resend', 'Only when configured'], 'gray'),
  ];
  const edges = [
    connect({ x: 70, y: 165, w: 270, h: 130 }, { x: 1110, y: 150, w: 350, h: 160 }, 'HTTPS portal'),
    connect({ x: 400, y: 165, w: 270, h: 130 }, { x: 1110, y: 150, w: 350, h: 160 }, ''),
    connect({ x: 730, y: 165, w: 300, h: 130 }, { x: 1110, y: 150, w: 350, h: 160 }, ''),
    connect({ x: 1285, y: 310, w: 1, h: 1 }, { x: 1285, y: 395, w: 1, h: 1 }, 'TLS'),
    connect({ x: 1285, y: 545, w: 1, h: 1 }, { x: 1285, y: 650, w: 1, h: 1 }, 'API / UI'),
    connect({ x: 1110, y: 740, w: 10, h: 10 }, { x: 735, y: 1010, w: 10, h: 10 }, 'persistent data'),
    connect({ x: 1285, y: 840, w: 1, h: 1 }, { x: 960, y: 930, w: 1, h: 1 }, 'private files'),
    connect({ x: 1460, y: 740, w: 1, h: 1 }, { x: 1530, y: 735, w: 1, h: 1 }, 'optional'),
    connect({ x: 1460, y: 470, w: 1, h: 1 }, { x: 1530, y: 470, w: 1, h: 1 }, 'pilot only', { dashed: true }),
    connect({ x: 1450, y: 800, w: 1, h: 1 }, { x: 1370, y: 930, w: 1, h: 1 }, 'operations'),
  ].join('');
  return canvas('System architecture', 'Current application shape · deployment options are separated from core application components', 1850, 1200, edges + nodes.join(''),
    'Current pilot uses a temporary Quick Tunnel; the database is not exposed to the browser. Verify production deployment details before presenting.');
}

function makeContext() {
  const people = [
    card(70, 190, 330, 145, 'Student', ['Own profile and academic records', 'Registration · fees · requests'], 'teal'),
    card(70, 440, 330, 145, 'Teacher', ['Assigned-course roster', 'Grades · attendance · materials'], 'teal'),
    card(70, 690, 330, 145, 'Administrator', ['Configuration · approvals', 'Reports · exports · audit'], 'teal'),
    card(620, 400, 560, 245, 'Student Information Portal', ['Browser UI + Spring Boot API', 'Role- and ownership-checked actions', 'Business records and workflow state'], 'blue'),
    card(1460, 155, 320, 145, 'Database', ['Users · academic data', 'Finance · workflow · audit'], 'gold'),
    card(1460, 405, 320, 145, 'Private file store', ['Authorized downloads', 'Uploads and profile photos'], 'gold'),
    card(1460, 655, 320, 145, 'Email provider', ['Optional delivery', 'Only if configured'], 'gray'),
  ];
  const n = { a: { x: 70, y: 190, w: 330, h: 145 }, b: { x: 70, y: 440, w: 330, h: 145 }, c: { x: 70, y: 690, w: 330, h: 145 }, p: { x: 620, y: 400, w: 560, h: 245 }, db: { x: 1460, y: 155, w: 320, h: 145 }, f: { x: 1460, y: 405, w: 320, h: 145 }, m: { x: 1460, y: 655, w: 320, h: 145 } };
  const edges = [
    connect(n.a, n.p, 'Profile · course · payment request'),
    connect(n.p, n.a, 'Own records · status'),
    connect(n.b, n.p, 'Roster · grade · attendance actions'),
    connect(n.p, n.b, 'Assigned-course information'),
    connect(n.c, n.p, 'Admin actions · report request'),
    connect(n.p, n.c, 'Review queues · authorized report'),
    connect(n.p, n.db, 'Read / write records'),
    connect(n.p, n.f, 'Authorized file access'),
    connect(n.p, n.m, 'Configured notification delivery', { dashed: true }),
  ].join('');
  return canvas('DFD 0 · system context', 'The portal is one bounded application; external actors and supporting stores are outside that boundary', 1850, 1020, edges + people.join(''),
    'Bidirectional arrows indicate request/response or read/write exchange; dashed flow denotes optional configured email delivery.');
}

function makeLevel1() {
  const actors = [
    card(55, 405, 260, 130, 'Portal users', ['Student · teacher · admin'], 'teal'),
    card(55, 665, 260, 120, 'Public applicant', ['Application / status request'], 'gray'),
  ];
  const processes = [
    card(420, 150, 360, 160, '1. Authenticate & authorize', ['Session and role checks', 'Ownership / permission checks'], 'blue'),
    card(420, 400, 360, 160, '2. Academic workflows', ['Courses · registration · grades', 'Attendance · assignments'], 'blue'),
    card(420, 650, 360, 160, '3. Finance & reporting', ['Fees · payment references', 'Review · reports · exports'], 'blue'),
    card(420, 900, 360, 160, '4. Files & notifications', ['Uploads · authorized downloads', 'In-app / configured email'], 'blue'),
  ];
  const stores = [
    card(1050, 135, 360, 135, 'D1 Identity', ['Users · roles · preferences'], 'gold'),
    card(1050, 330, 360, 135, 'D2 Academic records', ['Students · courses · registration'], 'gold'),
    card(1050, 525, 360, 135, 'D3 Learning activity', ['Grades · attendance · assignments'], 'gold'),
    card(1050, 720, 360, 135, 'D4 Finance & audit', ['Fees · attempts · reconciliation'], 'gold'),
    card(1050, 915, 360, 135, 'D5 File / notice store', ['Private uploads · notifications'], 'gold'),
    card(1515, 470, 250, 145, 'Optional email', ['External provider', 'Configuration required'], 'gray'),
  ];
  const p = [
    { x: 420, y: 150, w: 360, h: 160 }, { x: 420, y: 400, w: 360, h: 160 },
    { x: 420, y: 650, w: 360, h: 160 }, { x: 420, y: 900, w: 360, h: 160 },
  ];
  const s = [
    { x: 1050, y: 135, w: 360, h: 135 }, { x: 1050, y: 330, w: 360, h: 135 },
    { x: 1050, y: 525, w: 360, h: 135 }, { x: 1050, y: 720, w: 360, h: 135 },
    { x: 1050, y: 915, w: 360, h: 135 },
  ];
  const u = { x: 55, y: 405, w: 260, h: 130 }, publicActor = { x: 55, y: 665, w: 260, h: 120 };
  const edges = [
    connect(u, p[0], 'credentials / requests'),
    connect(u, p[1], 'academic actions'),
    connect(u, p[2], 'finance / report actions'),
    connect(u, p[3], 'file / notification actions'),
    connect(publicActor, p[2], 'admissions / status'),
    connect(p[0], s[0], 'identity and permissions'),
    connect(p[1], s[1], 'academic records'),
    connect(p[1], s[2], 'learning activity'),
    connect(p[2], s[3], 'finance and audit'),
    connect(p[3], s[4], 'files and notices'),
    connect(p[3], { x: 1515, y: 470, w: 250, h: 145 }, 'if enabled', { dashed: true }),
  ].join('');
  return canvas('DFD 1 · major processes and data stores', 'A logical decomposition of the principal portal flows; it is not a deployment topology', 1850, 1180, edges + actors.join('') + processes.join('') + stores.join(''),
    'Authorization is applied to protected requests. The data-store labels group selected domains; they are not physical database partitions.');
}

function makeAcademicErd() {
  const specs = [
    [65, 145, 'USER', ['PK id', 'UK username', 'role · is_active'], 'purple'],
    [475, 145, 'STUDENT', ['PK id', 'FK/UK user_id', 'UK registration_number', 'full_name · cgpa'], 'teal'],
    [1110, 145, 'TEACHER', ['PK id', 'FK/UK user_id', 'FK department_id', 'full_name'], 'teal'],
    [65, 430, 'COURSE_CATEGORY', ['PK id', 'name'], 'gray'],
    [475, 430, 'COURSE', ['PK id', 'FK category_id', 'FK teacher_id', 'UK course_code'], 'blue'],
    [1110, 430, 'ACADEMIC_YEAR', ['PK id', 'UK code'], 'gold'],
    [1110, 680, 'ACADEMIC_TERM', ['PK id', 'FK academic_year_id', 'UK code', 'registration_open'], 'gold'],
    [475, 720, 'COURSE_SECTION', ['PK id', 'FK course_id', 'FK academic_term_id', 'section_code · capacity'], 'blue'],
    [65, 760, 'COURSE_REGISTRATION', ['PK id', 'FK student_id', 'FK section_id', 'status'], 'purple'],
    [65, 1040, 'GRADE', ['PK id', 'FK student_id · course_id', 'grade_value · status', 'submitted / approved'], 'purple'],
    [720, 1040, 'ATTENDANCE', ['PK id', 'FK student_id · course_id', 'date · present · late'], 'gray'],
  ];
  const entities = specs.map(([x, y, name, fields, tone]) => entity(x, y, 340, name, fields, tone));
  const by = name => entities[specs.findIndex(item => item[2] === name)];
  const edges = [
    connect(by('USER'), by('STUDENT'), '1 : 0..1', { arrow: false }),
    connect(by('USER'), by('TEACHER'), '1 : 0..1', { arrow: false }),
    connect(by('TEACHER'), by('COURSE'), '1 : many'),
    connect(by('COURSE_CATEGORY'), by('COURSE'), '1 : many'),
    connect(by('COURSE'), by('COURSE_SECTION'), '1 : many'),
    connect(by('ACADEMIC_YEAR'), by('ACADEMIC_TERM'), '1 : many'),
    connect(by('ACADEMIC_TERM'), by('COURSE_SECTION'), '1 : many'),
    connect(by('STUDENT'), by('COURSE_REGISTRATION'), '1 : many'),
    connect(by('COURSE_SECTION'), by('COURSE_REGISTRATION'), '1 : many'),
    connect(by('STUDENT'), by('GRADE'), '1 : many'),
    connect(by('COURSE'), by('GRADE'), '1 : many'),
    connect(by('STUDENT'), by('ATTENDANCE'), '1 : many'),
    connect(by('COURSE'), by('ATTENDANCE'), '1 : many'),
  ].join('');
  const note = `<rect x="1110" y="950" width="640" height="100" rx="12" fill="#fff" stroke="#9aaabd"/><text x="1132" y="986" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="16" fill="#40576b">Keys: PK primary · FK foreign · UK unique.</text><text x="1132" y="1018" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="16" fill="#40576b">Registration connects students to term-specific sections.</text>`;
  return canvas('ERD 1 · identity and academic core', 'Selected relational entities and cardinalities, based on JPA mappings and migrations', 1850, 1290, edges + entities.map(item => item.svg).join('') + note,
    'Entity set is intentionally partial. Student–course registration is mediated by COURSE_SECTION and COURSE_REGISTRATION.');
}

function makeFinanceErd() {
  const specs = [
    [65, 170, 'DEPARTMENT', ['PK id', 'FK faculty_id', 'name'], 'gray'],
    [475, 170, 'PROGRAM', ['PK id', 'FK department_id', 'UK code · name'], 'blue'],
    [1110, 170, 'ACADEMIC_YEAR', ['PK id', 'UK code'], 'gold'],
    [65, 500, 'ADMISSION_APPLICATION', ['PK id', 'UK application_number', 'FK program_id · academic_year_id', 'status · normalized_email'], 'purple'],
    [475, 500, 'STUDENT', ['PK id', 'FK/UK user_id', 'UK registration_number'], 'teal'],
    [1110, 500, 'STUDENT_PROGRAM', ['PK id', 'FK student_id · program_id', 'FK academic_year_id', 'lifecycle_status'], 'teal'],
    [65, 870, 'FEE', ['PK id', 'FK student_id', 'amount · paid · due_date'], 'gold'],
    [475, 870, 'PAYMENT_ATTEMPT', ['PK id', 'FK student_id · fee_id', 'UK provider_reference', 'channel · status'], 'purple'],
    [1110, 870, 'BANK_RECONCILIATION_ENTRY', ['PK id', 'FK matched_payment_attempt_id', 'provider_reference · amount'], 'gray'],
  ];
  const entities = specs.map(([x, y, name, fields, tone]) => entity(x, y, 410, name, fields, tone));
  const by = name => entities[specs.findIndex(item => item[2] === name)];
  const edges = [
    connect(by('DEPARTMENT'), by('PROGRAM'), '1 : many'),
    connect(by('PROGRAM'), by('ADMISSION_APPLICATION'), '1 : many'),
    connect(by('ACADEMIC_YEAR'), by('ADMISSION_APPLICATION'), '1 : many'),
    connect(by('STUDENT'), by('STUDENT_PROGRAM'), '1 : many'),
    connect(by('PROGRAM'), by('STUDENT_PROGRAM'), '1 : many'),
    connect(by('ACADEMIC_YEAR'), by('STUDENT_PROGRAM'), '1 : many'),
    connect(by('STUDENT'), by('FEE'), '1 : many'),
    connect(by('STUDENT'), by('PAYMENT_ATTEMPT'), '1 : many'),
    connect(by('FEE'), by('PAYMENT_ATTEMPT'), '1 : many'),
    connect(by('PAYMENT_ATTEMPT'), by('BANK_RECONCILIATION_ENTRY'), '1 : many'),
  ].join('');
  const note = `<rect x="65" y="1110" width="1650" height="80" rx="12" fill="#fff" stroke="#9aaabd"/><text x="88" y="1144" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="16" fill="#40576b">An admission application is linked to program and intake year; account/student creation is a workflow outcome, not a direct application→student FK.</text><text x="88" y="1172" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="16" fill="#40576b">Payment attempts reference a fee and student; bank reconciliation entries match attempts for manual reconciliation.</text>`;
  return canvas('ERD 2 · admissions and finance', 'Selected lifecycle and payment entities; transaction processing is not equivalent to a live provider integration', 1850, 1290, edges + entities.map(item => item.svg).join('') + note,
    'Foreign keys and unique constraints are indicated where present; this view omits supporting allocations, refunds, waivers, and status-history entities.');
}

function makeUseCases() {
  const width = 1850, height = 1200;
  const boundary = `<rect x="500" y="140" width="1260" height="920" rx="20" fill="#fafdff" stroke="#416d8b" stroke-width="3" stroke-dasharray="12 7"/><text x="535" y="180" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="21" font-weight="700" fill="#17324d">Student Information Portal</text>`;
  const use = (x, y, w, label, sub = '') => `<g><ellipse cx="${x + w / 2}" cy="${y + 34}" rx="${w / 2}" ry="34" fill="#e8f2ff" stroke="#3f7ea5" stroke-width="2"/><text x="${x + w / 2}" y="${y + (sub ? 29 : 40)}" text-anchor="middle" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="16" font-weight="600" fill="#17324d">${esc(label)}</text>${sub ? `<text x="${x + w / 2}" y="${y + 51}" text-anchor="middle" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="13" fill="#4b6276">${esc(sub)}</text>` : ''}</g>`;
  const cases = [
    [570, 230, 350, 'Authenticate and manage profile'],
    [1010, 230, 340, 'View courses, grades, attendance'],
    [1420, 230, 300, 'Register for course section'],
    [570, 430, 350, 'View fees / submit payment reference'],
    [1010, 430, 340, 'Message, request, notification'],
    [1420, 430, 300, 'Submit admission / check status'],
    [570, 650, 350, 'View assigned-course roster'],
    [1010, 650, 340, 'Record grades / attendance'],
    [1420, 650, 300, 'Publish materials / assignments'],
    [570, 870, 350, 'Manage users, courses, setup'],
    [1010, 870, 340, 'Review admissions, grades, payments'],
    [1420, 870, 300, 'Reports, exports, audit'],
  ];
  const links = [
    [[360, 340], [745, 264]], [[360, 340], [1180, 264]], [[360, 340], [1570, 264]],
    [[360, 340], [745, 464]], [[360, 340], [1180, 464]],
    [[360, 620], [745, 684]], [[360, 620], [1180, 684]], [[360, 620], [1570, 684]],
    [[360, 900], [745, 904]], [[360, 900], [1180, 904]], [[360, 900], [1570, 904]],
    [[360, 1040], [1570, 464]],
  ];
  const associations = links.map(([[x1, y1], [x2, y2]]) => `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="#91a8b8" stroke-width="1.8"/>`).join('');
  const actorSvg = [
    actor(350, 280, 'Student', '#197d77'),
    actor(350, 560, 'Teacher', '#197d77'),
    actor(350, 840, 'Administrator', '#b37b18'),
    actor(350, 980, 'Applicant', '#617487'),
  ].join('');
  return canvas('UML use-case view · role capabilities', 'High-level use cases only; authority is enforced by backend authorization, not UI visibility', width, height, associations + boundary + actorSvg + cases.map(item => use(...item)).join(''),
    'Use cases are representative. Applicant is unauthenticated for public admissions; protected use cases require role/ownership checks.');
}

function makeSequence(title, subtitle, participants, messages, footer) {
  const width = 1850, height = 1190;
  const gap = 1780 / participants.length;
  const xs = participants.map((_, index) => 60 + gap * (index + .5));
  let content = '';
  participants.forEach((name, index) => {
    const x = xs[index];
    content += `<rect x="${x - 120}" y="150" width="240" height="74" rx="12" fill="${index % 2 ? '#e3f7f4' : '#e8f2ff'}" stroke="${index % 2 ? '#197d77' : '#2f6f9f'}" stroke-width="2"/><text x="${x}" y="180" text-anchor="middle" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="16" font-weight="700" fill="#17324d">${esc(name)}</text><line x1="${x}" y1="224" x2="${x}" y2="1075" stroke="#7892a5" stroke-width="2" stroke-dasharray="8 7"/>`;
  });
  messages.forEach((message, index) => {
    const [from, to, label, kind = 'solid'] = message;
    const y = 280 + index * 38;
    const x1 = xs[from], x2 = xs[to];
    const right = x2 > x1;
    content += `<line x1="${x1}" y1="${y}" x2="${x2}" y2="${y}" stroke="${kind === 'error' ? '#b55c70' : '#347d91'}" stroke-width="2.5" ${kind === 'return' || kind === 'optional' ? 'stroke-dasharray="8 6"' : ''} marker-end="url(#arrow)"/><text x="${(x1 + x2) / 2}" y="${y - 10}" text-anchor="middle" paint-order="stroke" stroke="#f7faff" stroke-width="7" stroke-linejoin="round" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="15" fill="#365a70">${esc(label)}</text>`;
    if (!right && kind === 'return') content += `<circle cx="${x1 - 6}" cy="${y}" r="4" fill="#347d91"/>`;
  });
  const altY = 285 + messages.length * 38;
  content += `<rect x="85" y="${altY}" width="${width - 170}" height="110" rx="12" fill="#fff" stroke="#9aaabd" stroke-width="2" stroke-dasharray="8 6"/><text x="108" y="${altY + 30}" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="15" font-weight="700" fill="#40576b">Decision / boundary</text><text x="108" y="${altY + 60}" font-family="Aptos,Segoe UI,Arial,sans-serif" font-size="15" fill="#40576b">${esc(footer)}</text>`;
  return canvas(title, subtitle, width, height, content, 'UML sequence view · representative successful and denied paths; exact API contracts may evolve.');
}

async function main() {
  fs.mkdirSync(imageDir, { recursive: true });
  const diagrams = {
    '01-system-architecture': makeArchitecture(),
    '02-dfd-context': makeContext(),
    '03-dfd-level1': makeLevel1(),
    '04-erd-academic-core': makeAcademicErd(),
    '05-erd-admissions-finance': makeFinanceErd(),
    '06-uml-use-cases': makeUseCases(),
    '07-uml-sequence-authentication': makeSequence(
      'UML sequence · authentication and access control',
      'Credential validation precedes cookie issuance; each protected request is checked again',
      ['User', 'Browser', 'Security / API', 'Auth service', 'User database'],
      [
        [0, 1, 'Enter credentials'], [1, 2, 'POST /api/auth/login'], [2, 3, 'Validate credentials / state'],
        [3, 4, 'Find normalized account'], [4, 3, 'Account · role · MFA state', 'return'],
        [3, 2, 'Authentication result', 'return'], [2, 1, 'Set HttpOnly cookie on success', 'return'],
        [1, 2, 'Protected request + cookie'], [2, 3, 'Validate token + authority'],
        [3, 4, 'Resolve current account'], [4, 3, 'Identity / active status', 'return'],
        [3, 2, 'Subject + authorities', 'return'], [2, 1, 'Authorized response or denial', 'return'],
      ],
      'If MFA is required, the challenge is verified before an authenticated cookie is issued; invalid credentials or roles are rejected.'
    ),
    '08-uml-sequence-teacher-workflow': makeSequence(
      'UML sequence · teacher roster photo and CSV',
      'Course ownership gates roster data; student-photo access is scoped to students the teacher teaches',
      ['Teacher', 'Browser UI', 'Spring API', 'Access checks', 'Database / files'],
      [
        [0, 1, 'Choose assigned course'], [1, 2, 'GET assigned-course roster'],
        [2, 3, 'Verify teacher owns course'], [3, 4, 'Load course + registered students'],
        [4, 3, 'Roster DTO + photo paths', 'return'], [3, 2, 'Authorized roster', 'return'],
        [2, 1, 'Render roster'], [1, 2, 'GET profile photo'],
        [2, 3, 'Check teacher teaches student'], [3, 4, 'Read file only if authorized'],
        [4, 3, 'Image bytes', 'return'], [3, 1, 'Inline image / denial', 'return'],
        [0, 1, 'Click roster / grade CSV'], [1, 2, 'Request selected-course CSV'],
        [2, 3, 'Verify teacher owns course'], [3, 4, 'Load permitted fields'],
        [4, 3, 'Selected-course records', 'return'], [3, 2, 'Authorized CSV response', 'return'],
        [2, 1, 'Download CSV', 'return'],
      ],
      'CSV output is generated from the current selected-course data; it does not expose the administrator-wide dataset export.'
    ),
  };

  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1850, height: 1290 }, deviceScaleFactor: 1 });
    for (const [name, svg] of Object.entries(diagrams)) {
      const svgPath = path.join(imageDir, `${name}.svg`);
      const pngPath = path.join(imageDir, `${name}.png`);
      fs.writeFileSync(svgPath, svg, 'utf8');
      await page.setContent(`<html><body style="margin:0">${svg}</body></html>`);
      await page.locator('svg').screenshot({ path: pngPath });
      console.log(`Rendered ${name}`);
    }
  } finally {
    await browser.close();
  }
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
