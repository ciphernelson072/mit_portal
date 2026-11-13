const admissionsApi = '/api/public/admissions';

async function admissionsRequest(path, options = {}) {
  const response = await fetch(`${admissionsApi}${path}`, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || `Request failed (${response.status})`);
  return body;
}

function setAdmissionStatus(message, error = false) {
  const status = document.getElementById('admissionStatus');
  status.textContent = message;
  status.classList.toggle('error', error);
}

const applicationStatusGuidance = {
  SUBMITTED: 'Your application has been received. Keep your application number to check for updates.',
  UNDER_REVIEW: 'Your application is being reviewed. Contact admissions support if you need help.',
  ACCEPTED: 'Your application was accepted. A portal account is created after an administrator enrolls you; login details are provided after enrollment.',
  REJECTED: 'Your application was not accepted. Contact admissions support if you need clarification.',
  WITHDRAWN: 'This application is marked as withdrawn. Contact admissions support if you believe this is incorrect.',
};

const confirmation = document.getElementById('applicationConfirmation');
const copyApplicationNumberButton = document.getElementById('copyApplicationNumber');
let submittedApplicationNumber = '';

if (copyApplicationNumberButton && confirmation) {
  copyApplicationNumberButton.addEventListener('click', async () => {
    if (!submittedApplicationNumber) return;
    try {
      await navigator.clipboard.writeText(submittedApplicationNumber);
      copyApplicationNumberButton.textContent = 'Copied';
    } catch (error) {
      copyApplicationNumberButton.textContent = 'Copy unavailable — select the number to copy';
    }
  });
}

async function loadAdmissionOptions() {
  try {
    const [programs, year] = await Promise.all([
      admissionsRequest('/programs'),
      admissionsRequest('/academic-year'),
    ]);
    const programSelect = document.getElementById('programId');
    programSelect.replaceChildren(new Option('Choose a programme', ''));
    programs.forEach(program => programSelect.add(new Option(`${program.code} - ${program.name}`, program.id)));
    programSelect.disabled = programs.length === 0;
    const yearSelect = document.getElementById('academicYearId');
    yearSelect.replaceChildren(new Option(year.name || 'No active academic year configured', year.id || ''));
    yearSelect.disabled = !year.id;
    const ready = programs.length > 0 && Boolean(year.id);
    document.getElementById('submitAdmissionButton').disabled = !ready;
    if (!ready) {
      const missing = [];
      if (!programs.length) missing.push('active programmes');
      if (!year.id) missing.push('an active academic year');
      setAdmissionStatus(`Applications cannot be submitted yet: the institution must configure ${missing.join(' and ')}.`, true);
    } else {
      setAdmissionStatus('');
    }
  } catch (error) {
    document.getElementById('programId').disabled = true;
    document.getElementById('academicYearId').disabled = true;
    document.getElementById('submitAdmissionButton').disabled = true;
    setAdmissionStatus(error.message, true);
  }
}

document.getElementById('admissionForm').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget;
  const submitButton = document.getElementById('submitAdmissionButton');
  submitButton.disabled = true;
  try {
    const result = await admissionsRequest('/applications', {
      method: 'POST',
      body: JSON.stringify({
        applicantName: document.getElementById('applicantName').value.trim(),
        email: document.getElementById('email').value.trim(),
        phone: document.getElementById('phone').value.trim(),
        programId: Number(document.getElementById('programId').value),
        academicYearId: Number(document.getElementById('academicYearId').value),
      }),
    });
    form.reset();
    submittedApplicationNumber = result.applicationNumber;
    document.getElementById('submittedApplicationNumber').textContent = submittedApplicationNumber;
    confirmation.hidden = false;
    const emailMessage = result.emailMessage || (result.emailSent
      ? 'A confirmation email has been sent; check your inbox and spam folder.'
      : 'Email confirmation could not be sent.');
    setAdmissionStatus(`Application submitted. Your application number is ${submittedApplicationNumber}. Save it; you will need it with the email address used to apply to check your status. ${emailMessage} This is not a portal login: after acceptance and enrollment, you will receive your username and a one-time temporary password separately.`);
  } catch (error) {
    setAdmissionStatus(error.message, true);
  } finally {
    submitButton.disabled = false;
  }
});

document.getElementById('statusForm').addEventListener('submit', async event => {
  event.preventDefault();
  const result = document.getElementById('statusResult');
  try {
    const application = await admissionsRequest('/applications/status', {
      method: 'POST',
      body: JSON.stringify({
        applicationNumber: document.getElementById('applicationNumber').value.trim(),
        email: document.getElementById('statusEmail').value.trim(),
      }),
    });
    const statusCode = String(application.status || '').toUpperCase();
    const statusLabel = statusCode.split('_').join(' ').toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());
    result.textContent = [
      `Application: ${application.applicationNumber}`,
      `Programme: ${application.program}`,
      `Academic year: ${application.academicYear}`,
      `Status: ${statusLabel}`,
      applicationStatusGuidance[statusCode] || 'For help interpreting this status, contact admissions support.',
    ].join('\n');
  } catch (error) {
    result.textContent = error.message;
  }
});

loadAdmissionOptions();
