/**
 * Admin Dashboard JavaScript
 * Handles all interactive functionality including Fetch API calls for CRUD operations
 * Without page reloads - uses AJAX for seamless experience
 */

const API_BASE = '/api/admin';
let currentEditingId = null;

// ============================================================================
// INITIALIZATION & EVENT LISTENERS
// ============================================================================

document.addEventListener('DOMContentLoaded', function() {
    // Get auth token from localStorage
    const token = localStorage.getItem('authToken');
    if (!token) {
        // Redirect to login if not authenticated
        window.location.href = '/index.html';
        return;
    }

    // Initialize event listeners
    initializeEventListeners();
    
    // Load initial dashboard data
    loadDashboardStatistics();
    loadStudents();
    loadTeachers();
    loadUsers();
});

/**
 * Initialize all event listeners for buttons and forms
 */
function initializeEventListeners() {
    // Tab navigation
    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            const tab = this.dataset.tab;
            switchTab(tab);
        });
    });

    // Student Management
    document.getElementById('admitStudentBtn')?.addEventListener('click', () => {
        document.getElementById('admitStudentForm').reset();
        openModal('admitStudentModal');
    });
    document.getElementById('admitStudentForm')?.addEventListener('submit', admitStudent);

    // Teacher Management
    document.getElementById('hireTeacherBtn')?.addEventListener('click', () => {
        document.getElementById('hireTeacherForm').reset();
        openModal('hireTeacherModal');
    });
    document.getElementById('hireTeacherForm')?.addEventListener('submit', hireTeacher);

    // User Management
    document.getElementById('createUserBtn')?.addEventListener('click', () => {
        document.getElementById('createUserForm').reset();
        openModal('createUserModal');
    });
    document.getElementById('createUserForm')?.addEventListener('submit', createUser);

    // Edit Student Form
    document.getElementById('editStudentForm')?.addEventListener('submit', updateStudent);

    // Logout
    document.getElementById('logoutBtn')?.addEventListener('click', logout);
}

// ============================================================================
// TAB NAVIGATION & MODAL FUNCTIONS
// ============================================================================

/**
 * Switch between dashboard tabs
 * @param {string} tabName - The tab to display
 */
function switchTab(tabName) {
    // Hide all tabs
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });

    // Remove active class from all nav links
    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.remove('active');
    });

    // Show selected tab
    const selectedTab = document.getElementById(tabName);
    if (selectedTab) {
        selectedTab.classList.add('active');
    }

    // Mark nav link as active
    document.querySelector(`[data-tab="${tabName}"]`)?.classList.add('active');

    // Reload data for the selected tab
    if (tabName === 'students') loadStudents();
    if (tabName === 'teachers') loadTeachers();
    if (tabName === 'users') loadUsers();
}

/**
 * Open a modal dialog
 * @param {string} modalId - The ID of the modal to open
 */
function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.classList.add('show');
        modal.style.display = 'flex';
    }
}

/**
 * Close a modal dialog
 * @param {string} modalId - The ID of the modal to close
 */
function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.classList.remove('show');
        modal.style.display = 'none';
    }
}

/**
 * Show a confirmation dialog
 * @param {string} message - The confirmation message
 * @param {function} onConfirm - Callback function if user confirms
 */
function showConfirmation(message, onConfirm) {
    document.getElementById('confirmMessage').textContent = message;
    openModal('confirmModal');

    const confirmBtn = document.getElementById('confirmYesBtn');
    confirmBtn.onclick = () => {
        closeModal('confirmModal');
        onConfirm();
    };
}

// ============================================================================
// NOTIFICATION SYSTEM
// ============================================================================

/**
 * Show a notification toast message
 * @param {string} message - The message to display
 * @param {string} type - Type of notification: 'success', 'error', 'warning', 'info'
 * @param {number} duration - How long to display (milliseconds)
 */
function showNotification(message, type = 'info', duration = 3000) {
    const toast = document.getElementById('notificationToast');
    toast.textContent = message;
    toast.className = `notification-toast ${type} show`;

    setTimeout(() => {
        toast.classList.remove('show');
    }, duration);
}

// ============================================================================
// DASHBOARD STATISTICS
// ============================================================================

/**
 * Load and display dashboard statistics
 */
async function loadDashboardStatistics() {
    try {
        const response = await apiFetch(`${API_BASE}/statistics`);
        
        document.getElementById('totalStudents').textContent = response.totalStudents || 0;
        document.getElementById('totalTeachers').textContent = response.totalTeachers || 0;
        document.getElementById('totalUsers').textContent = response.totalUsers || 0;
        document.getElementById('deletedUsers').textContent = response.deletedUsersCount || 0;
    } catch (error) {
        console.error('Error loading statistics:', error);
        showNotification('Failed to load statistics', 'error');
    }
}

// ============================================================================
// STUDENT MANAGEMENT - CRUD OPERATIONS
// ============================================================================

/**
 * Load and display all students in the table
 */
async function loadStudents() {
    try {
        const students = await apiFetch(`${API_BASE}/students`);
        const tbody = document.getElementById('studentsTableBody');
        
        if (!students || students.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align: center;">No students found</td></tr>';
            return;
        }

        tbody.innerHTML = students.map(student => `
            <tr>
                <td>${student.id}</td>
                <td>${student.fullName || '-'}</td>
                <td>${student.className || '-'}</td>
                <td><strong>${(student.cgpa || 0).toFixed(2)}</strong></td>
                <td>${student.email || '-'}</td>
                <td>${student.phone || '-'}</td>
                <td>
                    <div class="action-buttons">
                        <button class="btn btn-info" onclick="viewStudentCGPA(${student.id}, '${student.fullName}')">CGPA</button>
                        <button class="btn btn-warning" onclick="editStudent(${student.id})">Edit</button>
                        <button class="btn btn-danger" onclick="confirmRemoveStudent(${student.id})">Remove</button>
                    </div>
                </td>
            </tr>
        `).join('');
    } catch (error) {
        console.error('Error loading students:', error);
        showNotification('Failed to load students', 'error');
    }
}

/**
 * Admit (create) a new student
 */
async function admitStudent(e) {
    e.preventDefault();
    
    const form = e.target;
    const formData = new FormData(form);
    const student = Object.fromEntries(formData);

    try {
        const response = await apiFetch(`${API_BASE}/students/admit`, {
            method: 'POST',
            body: JSON.stringify({
                user: {
                    username: student.username,
                    password: student.password,
                    email: student.email,
                    phone: student.phone,
                    role: 'STUDENT',
                    isActive: true
                },
                fullName: student.fullName,
                className: student.className,
                email: student.email,
                phone: student.phone
            })
        });

        closeModal('admitStudentModal');
        showNotification('Student admitted successfully!', 'success');
        loadStudents();
        loadDashboardStatistics();
        form.reset();
    } catch (error) {
        showNotification(error.message || 'Failed to admit student', 'error');
    }
}

/**
 * Open edit student modal and populate form
 */
async function editStudent(studentId) {
    try {
        const student = await apiFetch(`${API_BASE}/students/${studentId}`);
        
        document.getElementById('editStudentId').value = student.id;
        document.getElementById('editStudentFullName').value = student.fullName || '';
        document.getElementById('editStudentClassName').value = student.className || '';
        document.getElementById('editStudentEmail').value = student.email || '';
        document.getElementById('editStudentPhone').value = student.phone || '';
        document.getElementById('editStudentAddress').value = student.address || '';
        
        openModal('editStudentModal');
    } catch (error) {
        showNotification(error.message || 'Failed to load student', 'error');
    }
}

/**
 * Update student information
 */
async function updateStudent(e) {
    e.preventDefault();
    
    const studentId = document.getElementById('editStudentId').value;
    const form = e.target;
    const formData = new FormData(form);

    const studentData = {
        fullName: formData.get('fullName'),
        className: formData.get('className'),
        email: formData.get('email'),
        phone: formData.get('phone'),
        address: formData.get('address')
    };

    try {
        await apiFetch(`${API_BASE}/students/${studentId}`, {
            method: 'PUT',
            body: JSON.stringify(studentData)
        });

        closeModal('editStudentModal');
        showNotification('Student updated successfully!', 'success');
        loadStudents();
    } catch (error) {
        showNotification(error.message || 'Failed to update student', 'error');
    }
}

/**
 * Confirm removal of student (soft delete)
 */
function confirmRemoveStudent(studentId) {
    showConfirmation('Are you sure you want to remove this student?', () => {
        removeStudent(studentId);
    });
}

/**
 * Remove (soft delete) a student
 */
async function removeStudent(studentId) {
    try {
        await apiFetch(`${API_BASE}/students/${studentId}`, {
            method: 'DELETE'
        });

        showNotification('Student removed successfully!', 'success');
        loadStudents();
        loadDashboardStatistics();
    } catch (error) {
        showNotification(error.message || 'Failed to remove student', 'error');
    }
}

/**
 * View student's CGPA details
 */
async function viewStudentCGPA(studentId, studentName) {
    try {
        const response = await apiFetch(`${API_BASE}/students/${studentId}/cgpa`);
        
        const cgpaDetails = document.getElementById('cgpaDetails');
        cgpaDetails.innerHTML = `
            <p><strong>Student:</strong> ${response.fullName}</p>
            <p><strong>CGPA:</strong> <span style="font-size: 1.5rem; color: #3498db;">${response.cgpa.toFixed(2)}</span></p>
        `;
        
        openModal('viewCGPAModal');
    } catch (error) {
        showNotification(error.message || 'Failed to load CGPA', 'error');
    }
}

// ============================================================================
// TEACHER MANAGEMENT - CRUD OPERATIONS
// ============================================================================

/**
 * Load and display all teachers in the table
 */
async function loadTeachers() {
    try {
        const teachers = await apiFetch(`${API_BASE}/teachers`);
        const tbody = document.getElementById('teachersTableBody');
        
        if (!teachers || teachers.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align: center;">No teachers found</td></tr>';
            return;
        }

        tbody.innerHTML = teachers.map(teacher => `
            <tr>
                <td>${teacher.id}</td>
                <td>${teacher.fullName || '-'}</td>
                <td>${teacher.department || '-'}</td>
                <td>${teacher.qualification || '-'}</td>
                <td>${teacher.email || '-'}</td>
                <td>${teacher.phone || '-'}</td>
                <td>
                    <div class="action-buttons">
                        <button class="btn btn-warning" onclick="editTeacher(${teacher.id})">Edit</button>
                        <button class="btn btn-danger" onclick="confirmRemoveTeacher(${teacher.id})">Remove</button>
                    </div>
                </td>
            </tr>
        `).join('');
    } catch (error) {
        console.error('Error loading teachers:', error);
        showNotification('Failed to load teachers', 'error');
    }
}

/**
 * Hire (create) a new teacher
 */
async function hireTeacher(e) {
    e.preventDefault();
    
    const form = e.target;
    const formData = new FormData(form);
    const teacher = Object.fromEntries(formData);

    try {
        const response = await apiFetch(`${API_BASE}/teachers/hire`, {
            method: 'POST',
            body: JSON.stringify({
                user: {
                    username: teacher.username,
                    password: teacher.password,
                    email: teacher.email,
                    phone: teacher.phone,
                    role: 'TEACHER',
                    isActive: true
                },
                fullName: teacher.fullName,
                department: teacher.department,
                qualification: teacher.qualification,
                yearsOfExperience: parseInt(teacher.yearsOfExperience) || 0,
                email: teacher.email,
                phone: teacher.phone
            })
        });

        closeModal('hireTeacherModal');
        showNotification('Teacher hired successfully!', 'success');
        loadTeachers();
        loadDashboardStatistics();
        form.reset();
    } catch (error) {
        showNotification(error.message || 'Failed to hire teacher', 'error');
    }
}

/**
 * Open edit teacher modal and populate form
 */
async function editTeacher(teacherId) {
    try {
        const teacher = await apiFetch(`${API_BASE}/teachers/${teacherId}`);
        
        // Create a simple edit form for teachers (similar to students)
        const html = `
            <div id="editTeacherModal" class="modal show">
                <div class="modal-content">
                    <span class="modal-close" onclick="closeModal('editTeacherModal')">&times;</span>
                    <h2>Edit Teacher</h2>
                    <form id="editTeacherForm" class="form-container">
                        <input type="hidden" id="editTeacherId" name="id" value="${teacher.id}">
                        <div class="form-group">
                            <label>Full Name:</label>
                            <input type="text" name="fullName" value="${teacher.fullName || ''}" required>
                        </div>
                        <div class="form-group">
                            <label>Department:</label>
                            <input type="text" name="department" value="${teacher.department || ''}">
                        </div>
                        <div class="form-group">
                            <label>Qualification:</label>
                            <textarea name="qualification" rows="3">${teacher.qualification || ''}</textarea>
                        </div>
                        <div class="form-group">
                            <label>Years of Experience:</label>
                            <input type="number" name="yearsOfExperience" value="${teacher.yearsOfExperience || 0}" min="0">
                        </div>
                        <div class="form-group">
                            <label>Email:</label>
                            <input type="email" name="email" value="${teacher.email || ''}">
                        </div>
                        <div class="form-group">
                            <label>Phone:</label>
                            <input type="tel" name="phone" value="${teacher.phone || ''}">
                        </div>
                        <button type="submit" class="btn btn-success">Update Teacher</button>
                    </form>
                </div>
            </div>
        `;
        
        document.body.insertAdjacentHTML('beforeend', html);
        document.getElementById('editTeacherForm').addEventListener('submit', updateTeacher);
    } catch (error) {
        showNotification(error.message || 'Failed to load teacher', 'error');
    }
}

/**
 * Update teacher information
 */
async function updateTeacher(e) {
    e.preventDefault();
    
    const teacherId = document.getElementById('editTeacherId').value;
    const form = e.target;
    const formData = new FormData(form);

    const teacherData = {
        fullName: formData.get('fullName'),
        department: formData.get('department'),
        qualification: formData.get('qualification'),
        yearsOfExperience: parseInt(formData.get('yearsOfExperience')) || 0,
        email: formData.get('email'),
        phone: formData.get('phone')
    };

    try {
        await apiFetch(`${API_BASE}/teachers/${teacherId}`, {
            method: 'PUT',
            body: JSON.stringify(teacherData)
        });

        closeModal('editTeacherModal');
        document.getElementById('editTeacherModal').remove();
        showNotification('Teacher updated successfully!', 'success');
        loadTeachers();
    } catch (error) {
        showNotification(error.message || 'Failed to update teacher', 'error');
    }
}

/**
 * Confirm removal of teacher (soft delete)
 */
function confirmRemoveTeacher(teacherId) {
    showConfirmation('Are you sure you want to remove this teacher?', () => {
        removeTeacher(teacherId);
    });
}

/**
 * Remove (soft delete) a teacher
 */
async function removeTeacher(teacherId) {
    try {
        await apiFetch(`${API_BASE}/teachers/${teacherId}`, {
            method: 'DELETE'
        });

        showNotification('Teacher removed successfully!', 'success');
        loadTeachers();
        loadDashboardStatistics();
    } catch (error) {
        showNotification(error.message || 'Failed to remove teacher', 'error');
    }
}

// ============================================================================
// USER MANAGEMENT - CRUD OPERATIONS
// ============================================================================

/**
 * Load and display all users in the table
 */
async function loadUsers() {
    try {
        const users = await apiFetch(`${API_BASE}/users`);
        const tbody = document.getElementById('usersTableBody');
        
        if (!users || users.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align: center;">No users found</td></tr>';
            return;
        }

        tbody.innerHTML = users.map(user => `
            <tr>
                <td>${user.id}</td>
                <td>${user.username}</td>
                <td><span class="badge badge-${user.role.toLowerCase()}">${user.role}</span></td>
                <td>${user.email || '-'}</td>
                <td>${user.isActive ? '<span style="color: green;">Active</span>' : '<span style="color: red;">Inactive</span>'}</td>
                <td>
                    <div class="action-buttons">
                        ${!user.isActive ? `<button class="btn btn-success" onclick="restoreUser(${user.id})">Restore</button>` : ''}
                        <button class="btn btn-danger" onclick="confirmRemoveUser(${user.id})">Remove</button>
                    </div>
                </td>
            </tr>
        `).join('');
    } catch (error) {
        console.error('Error loading users:', error);
        showNotification('Failed to load users', 'error');
    }
}

/**
 * Create a new user
 */
async function createUser(e) {
    e.preventDefault();
    
    const form = e.target;
    const formData = new FormData(form);
    const user = Object.fromEntries(formData);

    try {
        await apiFetch(`${API_BASE}/users`, {
            method: 'POST',
            body: JSON.stringify({
                username: user.username,
                password: user.password,
                role: user.role,
                email: user.email,
                phone: user.phone,
                isActive: true
            })
        });

        closeModal('createUserModal');
        showNotification('User created successfully!', 'success');
        loadUsers();
        loadDashboardStatistics();
        form.reset();
    } catch (error) {
        showNotification(error.message || 'Failed to create user', 'error');
    }
}

/**
 * Confirm removal of user (soft delete)
 */
function confirmRemoveUser(userId) {
    showConfirmation('Are you sure you want to deactivate this user?', () => {
        removeUser(userId);
    });
}

/**
 * Remove (soft delete) a user
 */
async function removeUser(userId) {
    try {
        await apiFetch(`${API_BASE}/users/${userId}`, {
            method: 'DELETE'
        });

        showNotification('User deactivated successfully!', 'success');
        loadUsers();
        loadDashboardStatistics();
    } catch (error) {
        showNotification(error.message || 'Failed to remove user', 'error');
    }
}

/**
 * Restore a soft-deleted user
 */
async function restoreUser(userId) {
    try {
        await apiFetch(`${API_BASE}/users/${userId}/restore`, {
            method: 'POST'
        });

        showNotification('User restored successfully!', 'success');
        loadUsers();
        loadDashboardStatistics();
    } catch (error) {
        showNotification(error.message || 'Failed to restore user', 'error');
    }
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * Make API calls with proper headers and error handling
 */
async function apiFetch(url, options = {}) {
    const token = localStorage.getItem('authToken');
    
    const headers = {
        'Content-Type': 'application/json',
        ...options.headers
    };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(url, {
        ...options,
        headers
    });

    if (!response.ok) {
        if (response.status === 401) {
            // Token expired or invalid
            localStorage.removeItem('authToken');
            window.location.href = '/index.html';
        }
        
        const error = await response.json().catch(() => ({ message: 'An error occurred' }));
        throw new Error(error.message || `HTTP ${response.status}`);
    }

    return response.json();
}

/**
 * Logout the current user
 */
function logout() {
    localStorage.removeItem('authToken');
    window.location.href = '/index.html';
}
