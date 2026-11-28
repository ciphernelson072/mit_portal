const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');

const teacherScript = fs.readFileSync(
  path.join(__dirname, '../src/main/resources/static/js/teacher-script.js'),
  'utf8',
);

function createTeacherContext(apiGet) {
  const elements = new Map();
  const rosterBodies = new Map();
  const gradeSubmit = { disabled: false };
  const document = {
    addEventListener() {},
    getElementById(id) {
      if (!elements.has(id)) {
        const body = { innerHTML: '' };
        let innerHTML = '';
        const element = {
          value: '',
          textContent: '',
          className: '',
          hidden: false,
          disabled: false,
          querySelector: selector => selector === 'tbody' ? body : null,
        };
        Object.defineProperty(element, 'innerHTML', {
          get: () => innerHTML,
          set: value => {
            innerHTML = value;
            if (id.endsWith('Select')) {
              element.value = value.match(/<option value="([^"]*)"/)?.[1] || '';
            }
          },
        });
        if (id === 'rosterTable' || id === 'teacherCourseUnitRosterTable') {
          rosterBodies.set(id, body);
        }
        elements.set(id, element);
      }
      return elements.get(id);
    },
    querySelector: () => gradeSubmit,
  };
  const context = {
    document,
    window: {},
    apiGet,
    escapeHtml: value => String(value ?? '')
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'),
    console,
  };
  vm.runInNewContext(teacherScript, context);
  document.getElementById('rosterTable');
  document.getElementById('teacherCourseUnitRosterTable');

  return {
    context,
    elements,
    rosterBody: rosterBodies.get('rosterTable'),
    courseUnitRosterBody: rosterBodies.get('teacherCourseUnitRosterTable'),
    gradeSubmit,
  };
}

test('assigned course-unit roster loads automatically when course lookup fails', async () => {
  const { context, elements, courseUnitRosterBody } = createTeacherContext(path => {
    if (path === '/teacher/my-courses') return Promise.reject(new Error('Course service is unavailable.'));
    if (path === '/teacher/course-units') {
      return Promise.resolve([{ id: 8, unitCode: 'MAT101A', name: 'Algebra', courseCode: 'MAT101' }]);
    }
    if (path === '/teacher/course-units/8/students') {
      return Promise.resolve([{ id: 81, fullName: 'Unit Student', registrationNumber: 'MIT-81' }]);
    }
    throw new Error(`Unexpected API request: ${path}`);
  });
  context.renderTeacherRoster = () => {};
  context.resetTeacherGradeWorkspace = () => {};

  await context.loadCourses();

  assert.match(elements.get('rosterCourseSelect').innerHTML, /Unable to load courses/);
  assert.match(elements.get('rosterCourseUnitSelect').innerHTML, /MAT101A/);
  assert.equal(elements.get('rosterStatus').textContent, 'Course service is unavailable.');
  assert.match(elements.get('courseUnitRosterStatus').textContent, /1 enrolled student/);
  assert.match(courseUnitRosterBody.innerHTML, /Unit Student/);
});

test('unit roster loads separately without resetting the assigned course workspace', async () => {
  const { context, elements, rosterBody, courseUnitRosterBody } = createTeacherContext(path => {
    if (path === '/teacher/my-courses') {
      return Promise.resolve([{ id: 3, courseCode: 'CSC103', name: 'Programming' }]);
    }
    if (path === '/teacher/course-units') {
      return Promise.resolve([{ id: 8, unitCode: 'MAT101A', name: 'Algebra', courseCode: 'MAT101' }]);
    }
    if (path === '/teacher/course-units/8/students') {
      return Promise.resolve([{ id: 81, fullName: 'Unit Student', registrationNumber: 'MIT-81' }]);
    }
    throw new Error(`Unexpected API request: ${path}`);
  });
  let gradeWorkspaceResets = 0;
  context.loadRoster = async () => {
    rosterBody.innerHTML = '<tr><td>Course Student</td><td>MIT-3</td></tr>';
  };
  context.loadAttendanceRoster = async () => {};
  context.resetTeacherGradeWorkspace = () => { gradeWorkspaceResets += 1; };

  await context.loadCourses();

  assert.match(rosterBody.innerHTML, /Course Student/);
  assert.match(courseUnitRosterBody.innerHTML, /Unit Student/);
  assert.equal(gradeWorkspaceResets, 0);
  assert.match(elements.get('courseUnitRosterStatus').textContent, /1 enrolled student/);
});

test('assigned courses still load when the course-unit endpoint fails', async () => {
  const loaded = [];
  const { context, elements } = createTeacherContext(path => {
    if (path === '/teacher/my-courses') {
      return Promise.resolve([{ id: 3, courseCode: 'CSC103', name: 'Programming' }]);
    }
    if (path === '/teacher/course-units') {
      return Promise.reject(new Error('Course-unit service is unavailable.'));
    }
    throw new Error(`Unexpected API request: ${path}`);
  });
  context.loadRoster = async () => loaded.push('course roster');
  context.loadAttendanceRoster = async () => loaded.push('attendance roster');

  await context.loadCourses();

  assert.match(elements.get('rosterCourseSelect').innerHTML, /CSC103/);
  assert.match(elements.get('attendanceCourseSelect').innerHTML, /CSC103/);
  assert.deepEqual(loaded, ['course roster', 'attendance roster']);
  assert.equal(elements.get('courseUnitRosterStatus').textContent, 'Course-unit service is unavailable.');
});

test('late course-roster responses do not replace the currently selected course', async () => {
  let resolveFirstCourse;
  const { context, elements, rosterBody } = createTeacherContext(path => {
    if (path === '/teacher/courses/1/students') {
      return new Promise(resolve => { resolveFirstCourse = resolve; });
    }
    if (path === '/teacher/courses/2/students') {
      return Promise.resolve([{ id: 22, fullName: 'Current Student', registrationNumber: 'MIT-22' }]);
    }
    throw new Error(`Unexpected API request: ${path}`);
  });
  context.loadTeacherGrades = async () => {};
  context.loadAssessmentComponents = async () => {};
  context.document.getElementById('rosterCourseSelect').value = '1';

  const firstRequest = context.loadRoster();
  context.document.getElementById('rosterCourseSelect').value = '2';
  await context.loadRoster();
  resolveFirstCourse([{ id: 11, fullName: 'Stale Student', registrationNumber: 'MIT-11' }]);
  await firstRequest;

  assert.match(rosterBody.innerHTML, /Current Student/);
  assert.doesNotMatch(rosterBody.innerHTML, /Stale Student/);
  assert.match(elements.get('rosterStatus').textContent, /1 enrolled student/);
});
