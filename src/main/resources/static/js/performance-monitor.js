/**
 * Performance Monitor & Form Validation
 * Tracks form submission performance and provides detailed metrics
 * Includes client-side validation and performance reporting
 */

/**
 * Form Performance Metrics Object
 * Stores performance data for each form submission
 */
class FormPerformanceMetrics {
    constructor(formName) {
        this.formName = formName;
        this.startTime = performance.now();
        this.validationTime = 0;
        this.submissionTime = 0;
        this.totalTime = 0;
        this.errors = [];
        this.fieldMetrics = {};
    }

    /**
     * Mark validation completion
     */
    endValidation() {
        this.validationTime = performance.now() - this.startTime;
    }

    /**
     * Mark submission completion
     */
    endSubmission() {
        this.totalTime = performance.now() - this.startTime;
        this.submissionTime = this.totalTime - this.validationTime;
    }

    /**
     * Add field-specific metrics
     */
    addFieldMetric(fieldName, valid, validationMs) {
        this.fieldMetrics[fieldName] = {
            valid: valid,
            validationTime: validationMs
        };
    }

    /**
     * Get performance report as object
     */
    getReport() {
        return {
            formName: this.formName,
            validationTimeMs: this.validationTime.toFixed(2),
            submissionTimeMs: this.submissionTime.toFixed(2),
            totalTimeMs: this.totalTime.toFixed(2),
            errorCount: this.errors.length,
            errors: this.errors,
            fieldMetrics: this.fieldMetrics,
            timestamp: new Date().toISOString()
        };
    }

    /**
     * Generate CSV report
     */
    toCsv() {
        const report = this.getReport();
        let csv = 'Form Performance Report\n';
        csv += `Form Name,${report.formName}\n`;
        csv += `Timestamp,${report.timestamp}\n`;
        csv += `Validation Time (ms),${report.validationTimeMs}\n`;
        csv += `Submission Time (ms),${report.submissionTimeMs}\n`;
        csv += `Total Time (ms),${report.totalTimeMs}\n`;
        csv += `Errors Found,${report.errorCount}\n\n`;
        
        csv += 'Field Metrics:\n';
        csv += 'Field Name,Valid,Validation Time (ms)\n';
        
        Object.entries(report.fieldMetrics).forEach(([field, metrics]) => {
            csv += `"${field}",${metrics.valid},${metrics.validationTime}\n`;
        });

        return csv;
    }
}

// Storage for all performance metrics
const performanceMetricsStore = [];

/**
 * Initialize form validation on page load
 */
document.addEventListener('DOMContentLoaded', function() {
    initializeFormValidation();
});

/**
 * Initialize validation for all forms
 */
function initializeFormValidation() {
    // Get all forms
    const forms = document.querySelectorAll('form');
    
    forms.forEach(form => {
        // Add real-time validation to inputs
        const inputs = form.querySelectorAll('input, textarea, select');
        inputs.forEach(input => {
            input.addEventListener('blur', function() {
                validateField(this);
            });

            input.addEventListener('change', function() {
                validateField(this);
            });
        });

        // Add form submission handler
        form.addEventListener('submit', function(e) {
            if (!validateForm(this)) {
                e.preventDefault();
            }
        });
    });
}

/**
 * Validate a single form field
 * @param {Element} field - The input field to validate
 * @returns {boolean} - True if valid
 */
function validateField(field) {
    const startTime = performance.now();
    let isValid = true;
    let errorMessage = '';

    // Check if field is required
    if (field.hasAttribute('required') && field.value.trim() === '') {
        isValid = false;
        errorMessage = 'This field is required';
    }
    // Email validation
    else if (field.type === 'email' && field.value.trim() !== '') {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(field.value)) {
            isValid = false;
            errorMessage = 'Please enter a valid email address';
        }
    }
    // Phone validation
    else if (field.name && field.name.includes('phone') && field.value.trim() !== '') {
        const phoneRegex = /^[\d\s\-\+\(\)]+$/;
        if (!phoneRegex.test(field.value) || field.value.replace(/\D/g, '').length < 10) {
            isValid = false;
            errorMessage = 'Please enter a valid phone number (at least 10 digits)';
        }
    }
    // Password validation (if password field)
    else if (field.type === 'password' && field.value.length > 0) {
        if (field.value.length < 6) {
            isValid = false;
            errorMessage = 'Password must be at least 6 characters long';
        }
    }
    // Number validation
    else if (field.type === 'number') {
        const min = field.getAttribute('min');
        const max = field.getAttribute('max');
        
        if (field.value !== '') {
            const num = parseFloat(field.value);
            if (min && num < parseFloat(min)) {
                isValid = false;
                errorMessage = `Minimum value is ${min}`;
            }
            if (max && num > parseFloat(max)) {
                isValid = false;
                errorMessage = `Maximum value is ${max}`;
            }
        }
    }

    // Update field styling based on validation
    const validationTime = performance.now() - startTime;
    updateFieldValidationState(field, isValid, errorMessage);

    return {
        isValid: isValid,
        validationTime: validationTime,
        field: field.name || field.id
    };
}

/**
 * Update field visual state based on validation result
 * @param {Element} field - The field being validated
 * @param {boolean} isValid - Is the field valid
 * @param {string} errorMessage - Error message if invalid
 */
function updateFieldValidationState(field, isValid, errorMessage) {
    // Remove previous validation classes
    field.classList.remove('success', 'error');

    if (isValid) {
        field.classList.add('success');
        // Remove any error messages
        const errorEl = field.parentElement.querySelector('.form-error');
        if (errorEl) {
            errorEl.textContent = '';
        }
    } else {
        field.classList.add('error');
        // Show error message
        let errorEl = field.parentElement.querySelector('.form-error');
        if (!errorEl) {
            errorEl = document.createElement('div');
            errorEl.className = 'form-error';
            field.parentElement.appendChild(errorEl);
        }
        errorEl.textContent = errorMessage;
    }
}

/**
 * Validate entire form
 * @param {Element} form - The form to validate
 * @returns {boolean} - True if form is valid
 */
function validateForm(form) {
    const formName = form.id || form.name || 'Unnamed Form';
    const metrics = new FormPerformanceMetrics(formName);

    const inputs = form.querySelectorAll('input, textarea, select');
    let isFormValid = true;
    const errors = [];

    inputs.forEach(input => {
        const validation = validateField(input);
        
        if (!validation.isValid) {
            isFormValid = false;
            errors.push({
                field: validation.field,
                message: input.parentElement.querySelector('.form-error')?.textContent || 'Invalid field'
            });
        }

        metrics.addFieldMetric(validation.field, validation.isValid, validation.validationTime);
    });

    metrics.errors = errors;
    metrics.endValidation();

    if (!isFormValid) {
        showValidationError(formName, errors);
        return false;
    }

    return true;
}

/**
 * Show validation error summary
 * @param {string} formName - Name of the form
 * @param {Array} errors - Array of error objects
 */
function showValidationError(formName, errors) {
    let errorMessage = `Validation failed in ${formName}:\n\n`;
    errors.forEach(error => {
        errorMessage += `• ${error.field}: ${error.message}\n`;
    });

    showNotification(errorMessage, 'error', 5000);
}

/**
 * Generate and download performance report
 * @param {string} type - Report type: 'json', 'csv', or 'html'
 */
function downloadPerformanceReport(type = 'json') {
    if (performanceMetricsStore.length === 0) {
        showNotification('No performance data available yet', 'warning');
        return;
    }

    let content = '';
    let filename = '';
    let mimeType = '';

    if (type === 'json') {
        content = JSON.stringify(performanceMetricsStore, null, 2);
        filename = `form-performance-${new Date().toISOString().slice(0, 10)}.json`;
        mimeType = 'application/json';
    } else if (type === 'csv') {
        // Convert metrics to CSV
        content = 'Form Name,Validation Time (ms),Submission Time (ms),Total Time (ms),Error Count,Timestamp\n';
        performanceMetricsStore.forEach(metric => {
            content += `"${metric.formName}",${metric.validationTimeMs},${metric.submissionTimeMs},${metric.totalTimeMs},${metric.errorCount},"${metric.timestamp}"\n`;
        });
        filename = `form-performance-${new Date().toISOString().slice(0, 10)}.csv`;
        mimeType = 'text/csv';
    } else if (type === 'html') {
        content = generateHtmlReport();
        filename = `form-performance-${new Date().toISOString().slice(0, 10)}.html`;
        mimeType = 'text/html';
    }

    // Create download link
    const blob = new Blob([content], { type: mimeType });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);

    showNotification(`Report downloaded: ${filename}`, 'success');
}

/**
 * Generate HTML report for performance metrics
 * @returns {string} - HTML content
 */
function generateHtmlReport() {
    let html = `
    <!DOCTYPE html>
    <html>
    <head>
        <title>Form Performance Report</title>
        <style>
            body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
            h1 { color: #2c3e50; }
            .report-section { background: white; padding: 20px; margin: 15px 0; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
            table { width: 100%; border-collapse: collapse; margin-top: 10px; }
            th, td { padding: 10px; text-align: left; border-bottom: 1px solid #ecf0f1; }
            th { background-color: #34495e; color: white; }
            tr:hover { background-color: #f9f9f9; }
            .metric-value { font-weight: bold; color: #3498db; }
            .error { color: #e74c3c; }
            .success { color: #27ae60; }
        </style>
    </head>
    <body>
        <h1>Form Performance Report</h1>
        <p>Generated: ${new Date().toLocaleString()}</p>
        <div class="report-section">
            <h2>Summary</h2>
            <p>Total Forms Submitted: <span class="metric-value">${performanceMetricsStore.length}</span></p>
            <p>Average Validation Time: <span class="metric-value">${getAverageMetric('validationTimeMs')}ms</span></p>
            <p>Average Submission Time: <span class="metric-value">${getAverageMetric('submissionTimeMs')}ms</span></p>
            <p>Total Forms with Errors: <span class="metric-value">${performanceMetricsStore.filter(m => m.errorCount > 0).length}</span></p>
        </div>
        <div class="report-section">
            <h2>Detailed Metrics</h2>
            <table>
                <thead>
                    <tr>
                        <th>Form Name</th>
                        <th>Validation Time (ms)</th>
                        <th>Submission Time (ms)</th>
                        <th>Total Time (ms)</th>
                        <th>Errors</th>
                        <th>Timestamp</th>
                    </tr>
                </thead>
                <tbody>
                    ${performanceMetricsStore.map(metric => `
                        <tr>
                            <td>${metric.formName}</td>
                            <td class="metric-value">${metric.validationTimeMs}</td>
                            <td class="metric-value">${metric.submissionTimeMs}</td>
                            <td class="metric-value">${metric.totalTimeMs}</td>
                            <td class="${metric.errorCount > 0 ? 'error' : 'success'}">${metric.errorCount}</td>
                            <td>${new Date(metric.timestamp).toLocaleString()}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        </div>
    </body>
    </html>
    `;
    return html;
}

/**
 * Calculate average of a metric
 * @param {string} metricName - Name of the metric
 * @returns {string} - Average value formatted to 2 decimals
 */
function getAverageMetric(metricName) {
    if (performanceMetricsStore.length === 0) return '0';
    
    const sum = performanceMetricsStore.reduce((total, metric) => {
        return total + parseFloat(metric[metricName]);
    }, 0);

    return (sum / performanceMetricsStore.length).toFixed(2);
}

/**
 * Log performance metrics to console for debugging
 */
function logPerformanceMetrics() {
    console.log('=== Form Performance Metrics ===');
    performanceMetricsStore.forEach((metric, index) => {
        console.log(`\n${index + 1}. ${metric.formName}`);
        console.log(`   Validation: ${metric.validationTimeMs}ms`);
        console.log(`   Submission: ${metric.submissionTimeMs}ms`);
        console.log(`   Total: ${metric.totalTimeMs}ms`);
        console.log(`   Errors: ${metric.errorCount}`);
    });
}

/**
 * Clear performance metrics from memory
 */
function clearPerformanceMetrics() {
    performanceMetricsStore.length = 0;
    showNotification('Performance metrics cleared', 'info');
}
