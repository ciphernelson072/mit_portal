const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const adminScript = fs.readFileSync(
  path.join(__dirname, '../src/main/resources/static/js/admin-script.js'),
  'utf8',
);

function createAdminScriptContext({ coursesByPage, teachers = [], categories = [] }) {
  const elements = new Map();
  const modal = { html: '' };
  const handlers = new Map();
  const requests = [];

  function element(id) {
    if (!elements.has(id)) {
      elements.set(id, {
        addEventListener(type, handler) {
          handlers.set(`${id}:${type}`, handler);
        },
      });
    }
    return elements.get(id);
  }

  const context = {
    document: {
      addEventListener() {},
      getElementById: element,
    },
    window: {},
    console,
    escapeHtml: value => String(value ?? ''),
    openModal: html => { modal.html = html; },
    closeModal: () => { modal.html = ''; },
    switchModule() {},
    apiGet: async pathValue => {
      requests.push(pathValue);
      if (pathValue.startsWith('/admin/courses?')) {
        const page = Number(new URL(`http://portal${pathValue}`).searchParams.get('page'));
        const content = coursesByPage[page] || [];
        return { content, totalPages: coursesByPage.length };
      }
      if (pathValue.startsWith('/admin/teachers?')) {
        const page = Number(new URL(`http://portal${pathValue}`).searchParams.get('page'));
        return { content: page === 0 ? teachers : [], totalPages: 1 };
      }
      if (pathValue === '/admin/course-categories') return categories;
      throw new Error(`Unexpected API request: ${pathValue}`);
    },
  };

  vm.runInNewContext(adminScript, context, { filename: 'admin-script.js' });
  context.openModal = html => { modal.html = html; };
  context.closeModal = () => { modal.html = ''; };
  return { context, modal, handlers, requests };
}

test('course-unit setup guides administrators to create a course instead of an empty required selector', async () => {
  const state = createAdminScriptContext({
    coursesByPage: [[]],
    teachers: [{ id: 4, fullName: 'Dr. Ada' }],
    categories: [{ id: 2, name: 'Science', active: true }],
  });

  await state.context.openCourseUnitForm();

  assert.match(state.modal.html, /No courses to assign/);
  assert.match(state.modal.html, /Create a course/);
  assert.doesNotMatch(state.modal.html, /name="courseId"/);
  assert.equal(typeof state.handlers.get('createCourseBeforeUnit:click'), 'function');
  assert.deepEqual(state.requests.map(pathValue => pathValue.split('?')[0]), [
    '/admin/courses',
    '/admin/teachers',
  ]);

  state.handlers.get('createCourseBeforeUnit:click')();
  await new Promise(resolve => setImmediate(resolve));
  assert.match(state.modal.html, /id="createCourseForm"/);
  assert.match(state.modal.html, /Choose a category…/);
});

test('course-unit selector includes courses across pages and requires explicit selections', async () => {
  const state = createAdminScriptContext({
    coursesByPage: [
      [{ id: 12, courseCode: 'CSC101', name: 'Foundations' }],
      [{ id: 36, courseCode: 'CSC201', name: 'Systems Design' }],
    ],
    teachers: [{ id: 4, fullName: 'Dr. Ada' }],
  });

  await state.context.openCourseUnitForm();

  assert.match(state.modal.html, /value="12">CSC101 · Foundations/);
  assert.match(state.modal.html, /value="36">CSC201 · Systems Design/);
  assert.match(state.modal.html, /<option value="">Choose a course…<\/option>/);
  assert.match(state.modal.html, /<option value="">Choose an instructor…<\/option>/);
  assert.ok(state.requests.some(pathValue => pathValue.includes('page=1')));
});
