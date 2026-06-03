const apiBase = '/api';
const alertContainer = document.getElementById('alertContainer');
const loginSection = document.getElementById('loginSection');
const dashboardSection = document.getElementById('dashboardSection');
const dashboardTitle = document.getElementById('dashboardTitle');
const dashboardSubtitle = document.getElementById('dashboardSubtitle');
const dashboardContent = document.getElementById('dashboardContent');
const loginButton = document.getElementById('loginButton');
const logoutButton = document.getElementById('logoutButton');

const tokenKey = 'schoolPortalToken';
const roleKey = 'schoolPortalRole';

// --- Utility & Session Management ---
function showAlert(message, type = 'info') {
    if (!alertContainer) return;
    alertContainer.innerHTML = `<div class="alert alert-${type} alert-dismissible fade show" role="alert">
        ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    </div>`;
}

function getToken() { return localStorage.getItem(tokenKey); }
function getRole() { return localStorage.getItem(roleKey); }
function setSession(token, role) {
    localStorage.setItem(tokenKey, token);
    localStorage.setItem(roleKey, role);
}
function clearSession() {
    localStorage.removeItem(tokenKey);
    localStorage.removeItem(roleKey);
}

// --- Tab & Chart Logic for PWA ---
window.switchTab = function(tabId) {
    document.getElementById('content-overview')?.classList.toggle('d-none', tabId !== 'overview');
    document.getElementById('content-charts')?.classList.toggle('d-none', tabId !== 'charts');
    
    document.querySelectorAll('#dashboardTabs .nav-link').forEach(btn => {
        btn.classList.toggle('active', btn.getAttribute('onclick').includes(tabId));
    });

    if (tabId === 'charts') initCharts();
};

let chartsRendered = false;
function initCharts() {
    if (chartsRendered) return; 
    const enrollCtx = document.getElementById('enrollmentChart');
    if (enrollCtx) {
        new Chart(enrollCtx, {
            type: 'bar',
            data: { labels: ['Q1', 'Q2', 'Q3', 'Q4'], datasets: [{ label: 'Enrollments', data: [120, 150, 180, 210], backgroundColor: '#0d6efd' }] }
        });
        chartsRendered = true;
    }
}

// --- API Wrapper ---
async function apiFetch(path, options = {}) {
    const token = getToken();
    options.headers = {
        'Content-Type': 'application/json',
        ...(options.headers || {}),
    };
    if (token) {
        options.headers.Authorization = `Bearer ${token}`;
    }
    const response = await fetch(`${apiBase}${path}`, options);
    if (response.status === 401) {
        clearSession();
        if (alertContainer) alertContainer.innerHTML = '';
        showLogin();
        throw new Error('Unauthorized');
    }
    return response;
}

// --- Navigation ---
function showLogin() {
    if (alertContainer) alertContainer.innerHTML = ''; // Clears lingering errors
    loginSection?.classList.remove('d-none');
    dashboardSection?.classList.add('d-none');
    dashboardContent.innerHTML = '';
}

async function showDashboard() {
    const role = getRole();
    if (!role) return showLogin();

    if (alertContainer) alertContainer.innerHTML = ''; // Clears lingering errors

    loginSection?.classList.add('d-none');
    dashboardSection?.classList.remove('d-none');
    dashboardTitle.textContent = `${role.charAt(0).toUpperCase()}${role.slice(1)} Dashboard`;
    dashboardSubtitle.textContent = 'Connected to SIS Database';
    
    loadAnnouncements();
    
    if (role === 'ADMIN') await renderAdminDashboard();
    else if (role === 'TEACHER') await renderTeacherDashboard();
    else await renderStudentDashboard();
}

// --- Authentication ---
async function login(e) {
    if (e) e.preventDefault(); 
    
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value.trim();
    if (!username || !password) {
        showAlert('Please enter credentials.', 'warning');
        return;
    }

    try {
        const response = await fetch(`${apiBase}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        if (!response.ok) throw new Error('Auth failed');
        
        const data = await response.json();
        setSession(data.token, data.role);
        showAlert('Login successful.', 'success');
        await showDashboard();
    } catch (error) {
        showAlert('Login failed. Ensure the database has an admin user.', 'danger');
    }
}

// --- Role Dashboards ---
async function renderTeacherDashboard() {
    dashboardContent.innerHTML = `
        <div class="row g-4">
            <div class="col-md-8">
                <div class="card p-3 mb-4 border-0 shadow-sm">
                    <h3 class="h6">📚 My Courses</h3>
                    <div id="teacherCourses" class="list-group list-group-flush mt-2"></div>
                </div>
                <div class="card p-3 border-0 shadow-sm">
                    <h3 class="h6">📝 Input Grades</h3>
                    <form id="gradeForm" class="row g-2 mt-2">
                        <div class="col-5"><input id="gradeStudentId" class="form-control" placeholder="Student ID" required></div>
                        <div class="col-4"><input id="gradeValue" class="form-control" placeholder="Grade (e.g. A, B+)" required></div>
                        <div class="col-3"><button type="submit" class="btn btn-success w-100">Save</button></div>
                    </form>
                </div>
            </div>
            <div class="col-md-4">
                <div class="card p-3 h-100 border-0 shadow-sm">
                    <h6 class="border-bottom pb-2">📅 Quick Attendance</h6>
                    <div id="attendanceList" class="small text-muted mt-2">Select a course to mark attendance.</div>
                </div>
            </div>
        </div>`;

    await loadTeacherData();

    document.getElementById('gradeForm')?.addEventListener('submit', async e => {
        e.preventDefault();
        await submitGrade();
    });
}

async function renderStudentDashboard() {
    dashboardContent.innerHTML = `
        <div class="row g-4">
            <div class="col-md-6">
                <div class="card p-3 border-0 shadow-sm bg-primary text-white">
                    <h3 class="h6">💰 Fee Status</h3>
                    <div id="studentFees" class="display-6 my-2">Loading...</div>
                    <p class="small mb-0" id="feeDeadline"></p>
                </div>
                <div class="card p-3 mt-4 border-0 shadow-sm">
                    <h3 class="h6">📊 My Grades</h3>
                    <div id="studentGrades" class="mt-2"></div>
                </div>
            </div>
            <div class="col-md-6">
                <div class="card p-3 h-100 border-0 shadow-sm">
                    <h3 class="h6 border-bottom pb-2">📅 Attendance History</h3>
                    <div id="studentAttendance" class="small mt-2">Loading...</div>
                </div>
            </div>
        </div>`;

    await loadStudentData();
}

async function renderAdminDashboard() {
    dashboardContent.innerHTML = `
        <div class="row g-4">
            <div class="col-md-4"><div class="card h-100 border-0 shadow-sm p-2"><h3 class="h6 mb-3 px-2 mt-2">👥 Students</h3><div id="adminStudents" class="overflow-auto px-2" style="max-height:300px"></div></div></div>
            <div class="col-md-4"><div class="card h-100 border-0 shadow-sm p-2"><h3 class="h6 mb-3 px-2 mt-2">🏫 Teachers</h3><div id="adminTeachers" class="overflow-auto px-2" style="max-height:300px"></div></div></div>
            <div class="col-md-4"><div class="card h-100 border-0 shadow-sm p-2"><h3 class="h6 mb-3 px-2 mt-2">📚 Courses</h3><div id="adminCourses" class="overflow-auto px-2" style="max-height:300px"></div></div></div>
        </div>
        <div class="row g-4 mt-2">
            <div class="col-md-6 col-lg-3">
                <div class="card border-0 shadow-sm p-3">
                    <h3 class="h6 mb-3">➕ Create Student</h3>
                    <form id="createStudentForm" class="d-grid gap-2">
                        <input id="studentUsername" class="form-control form-control-sm" placeholder="Username" required>
                        <input id="studentPassword" type="password" class="form-control form-control-sm" placeholder="Password" required>
                        <input id="studentFullName" class="form-control form-control-sm" placeholder="Full Name" required>
                        <input id="studentClassName" class="form-control form-control-sm" placeholder="Class" required>
                        <button type="submit" class="btn btn-sm btn-primary">Create Student</button>
                    </form>
                </div>
            </div>
            <div class="col-md-6 col-lg-3">
                <div class="card border-0 shadow-sm p-3 h-100">
                    <h3 class="h6 mb-3">➕ Create Course</h3>
                    <form id="createCourseForm" class="d-grid gap-2">
                        <input id="courseName" class="form-control form-control-sm" placeholder="Course Name" required>
                        <button type="submit" class="btn btn-sm btn-info text-white">Create Course</button>
                    </form>
                </div>
            </div>
            <div class="col-md-6 col-lg-3">
                <div class="card border-0 shadow-sm p-3 h-100">
                    <h3 class="h6 mb-3">📢 Announcement</h3>
                    <form id="createAnnouncementForm" class="d-grid gap-2">
                        <input id="announcementTitle" class="form-control form-control-sm" placeholder="Title" required>
                        <textarea id="announcementContent" class="form-control form-control-sm" placeholder="Content..." rows="2" required></textarea>
                        <button type="submit" class="btn btn-sm btn-warning">Post</button>
                    </form>
                </div>
            </div>
            <div class="col-md-6 col-lg-3">
                <div class="card border-0 shadow-sm p-3 h-100">
                    <h3 class="h6 mb-3">📊 Stats</h3>
                    <div id="analyticsData" class="small"></div>
                </div>
            </div>
        </div>`;

    await loadAdminLists();
    await loadAnalytics();

    document.getElementById('createStudentForm').addEventListener('submit', createStudent);
    document.getElementById('createCourseForm').addEventListener('submit', createCourse);
    document.getElementById('createAnnouncementForm').addEventListener('submit', createAnnouncement);
}

// --- Admin Actions (With Immediate Refresh) ---
async function createStudent(e) {
    e.preventDefault();
    const data = {
        username: document.getElementById('studentUsername').value.trim(),
        password: document.getElementById('studentPassword').value.trim(),
        fullName: document.getElementById('studentFullName').value.trim(),
        className: document.getElementById('studentClassName').value.trim()
    };
    try {
        const response = await apiFetch('/admin/students', { method: 'POST', body: JSON.stringify(data) });
        if (response.ok) {
            showAlert('Student added to database.', 'success');
            document.getElementById('createStudentForm').reset();
            await loadAdminLists(); // Immediate refresh
        } else {
            const errorData = await response.json();
            showAlert(errorData.message || 'Failed to create student.', 'danger');
        }
    } catch (e) {
        showAlert('Network error. Check if the server is running.', 'danger');
    }
}

async function createCourse(e) {
    e.preventDefault();
    const data = {
        name: document.getElementById('courseName').value.trim()
    };
    try {
        const response = await apiFetch('/admin/courses', { method: 'POST', body: JSON.stringify(data) });
        if (response.ok) {
            showAlert('Course created successfully!', 'success');
            document.getElementById('createCourseForm').reset();
            await loadAdminLists(); // Immediate refresh
        } else {
            const errorData = await response.json();
            showAlert(errorData.message || 'Failed to create course.', 'danger');
        }
    } catch (e) {
        showAlert('Network error.', 'danger');
    }
}

async function createAnnouncement(e) {
    e.preventDefault();
    const data = {
        title: document.getElementById('announcementTitle').value.trim(),
        content: document.getElementById('announcementContent').value.trim()
    };
    try {
        const response = await apiFetch('/announcements', { method: 'POST', body: JSON.stringify(data) });
        if (response.ok) {
            showAlert('Announcement posted!', 'success');
            document.getElementById('createAnnouncementForm').reset();
            loadAnnouncements(); // Immediate refresh
        } else {
            const errorData = await response.json();
            showAlert(errorData.message || 'Failed to post announcement.', 'danger');
        }
    } catch (e) { 
        showAlert('Network error.', 'danger'); 
    }
}

// --- Teacher Actions ---
async function submitGrade() {
    const data = {
        studentId: document.getElementById('gradeStudentId').value.trim(),
        grade: document.getElementById('gradeValue').value.trim()
    };
    try {
        const response = await apiFetch('/teacher/grades', { method: 'POST', body: JSON.stringify(data) });
        if (response.ok) {
            showAlert('Grade saved successfully.', 'success');
            document.getElementById('gradeForm').reset();
        } else {
            const err = await response.json();
            showAlert(err.message || 'Failed to save grade.', 'danger');
        }
    } catch (e) {
        showAlert('Network error.', 'danger');
    }
}

// --- Data Fetching Helpers ---
async function loadAdminLists() {
    const [students, teachers, courses] = await Promise.all([
        apiFetch('/admin/students').then(res => res.json()).catch(() => []),
        apiFetch('/admin/teachers').then(res => res.json()).catch(() => []),
        apiFetch('/admin/courses').then(res => res.json()).catch(() => [])
    ]);
    document.getElementById('adminStudents').innerHTML = itemsToRows(students, ['fullName', 'className']);
    document.getElementById('adminTeachers').innerHTML = itemsToRows(teachers, ['fullName']);
    document.getElementById('adminCourses').innerHTML = itemsToRows(courses, ['name']);
}

function itemsToRows(items, keys) {
    if (!items || !items.length) return '<p class="text-muted p-2 small">No records found.</p>';
    return items.map(item => `
        <div class="p-2 border-bottom small">
            ${keys.map(k => item[k] || '—').join(' • ')}
        </div>`).join('');
}

function loadAnnouncements() {
    const list = document.getElementById('announcementsList');
    if (!list) return;
    apiFetch('/announcements').then(res => res.json()).then(data => {
        list.innerHTML = data.map(ann => `
            <div class="mb-3 p-2 bg-light rounded border-start border-primary border-4">
                <h6 class="mb-1">${ann.title}</h6>
                <p class="small mb-0 text-muted">${ann.content}</p>
            </div>`).join('');
    }).catch(() => { list.innerHTML = '<p class="small text-muted">No updates.</p>'; });
}

async function loadAnalytics() {
    const stats = await apiFetch('/admin/analytics').then(res => res.json()).catch(() => ({}));
    const container = document.getElementById('analyticsData');
    if (container) {
        container.innerHTML = Object.entries(stats).map(([k, v]) => `
            <div class="d-flex justify-content-between border-bottom py-1">
                <span>${k}:</span> <strong>${v}</strong>
            </div>`).join('');
    }
}

async function loadTeacherData() {
    const courses = await apiFetch('/teacher/my-courses').then(res => res.json()).catch(() => []);
    const container = document.getElementById('teacherCourses');
    if (container) {
        container.innerHTML = courses.map(c => `
            <div class="list-group-item d-flex justify-content-between align-items-center">
                ${c.name} <span class="badge bg-primary rounded-pill">${c.studentCount || 0} Students</span>
            </div>`).join('') || '<p class="small p-2 text-muted">No courses assigned.</p>';
    }
}

async function loadStudentData() {
    const data = await apiFetch('/student/my-status').then(res => res.json()).catch(() => ({}));
    
    // Fees
    const feesEl = document.getElementById('studentFees');
    if (feesEl) feesEl.textContent = `$${data.balance || '0.00'}`;
    const deadlineEl = document.getElementById('feeDeadline');
    if (deadlineEl) deadlineEl.textContent = data.deadline ? `Due: ${data.deadline}` : 'No pending fees';
    
    // Grades
    const gradesContainer = document.getElementById('studentGrades');
    if (gradesContainer) {
        gradesContainer.innerHTML = (data.grades || []).map(g => `
            <div class="d-flex justify-content-between border-bottom py-2">
                <span>${g.courseName}</span>
                <span class="fw-bold text-primary">${g.score}</span>
            </div>`).join('') || '<p class="small text-muted mt-2">No grades posted yet.</p>';
    }

    // Attendance
    const attendanceContainer = document.getElementById('studentAttendance');
    if (attendanceContainer) {
         attendanceContainer.innerHTML = (data.attendance || []).map(a => `
            <div class="d-flex justify-content-between border-bottom py-2">
                <span>${a.date}</span>
                <span class="badge ${a.status === 'Present' ? 'bg-success' : 'bg-danger'}">${a.status}</span>
            </div>`).join('') || '<p class="small text-muted mt-2">No attendance records found.</p>';
    }
}

// --- Initialization ---
document.getElementById('loginForm')?.addEventListener('submit', login); 
logoutButton?.addEventListener('click', () => { clearSession(); showLogin(); });

window.addEventListener('load', async () => {
    if (getToken()) await showDashboard();

    // Register Service Worker for PWA functionality - CORRECTED PATH
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('./service-worker.js')
            .then(() => console.log("Service Worker Registered successfully"))
            .catch(err => console.log("Service Worker Registration Failed", err));
    }
});