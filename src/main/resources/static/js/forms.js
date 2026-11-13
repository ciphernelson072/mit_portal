/**
 * Forms Utility Module
 * Provides utility functions for form handling, validation, and submission
 */

/**
 * Form Validation Rules
 * Defines validation rules for different field types
 */
const ValidationRules = {
    required: (value) => value.trim() !== '',
    email: (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value),
    minLength: (minLen) => (value) => value.length >= minLen,
    maxLength: (maxLen) => (value) => value.length <= maxLen,
    phone: (value) => /^[\d\s\-\+\(\)]+$/.test(value) && value.replace(/\D/g, '').length >= 10,
    numeric: (value) => /^\d+$/.test(value),
    alphanumeric: (value) => /^[a-zA-Z0-9]+$/.test(value),
    username: (value) => /^[a-zA-Z0-9_.-]+$/.test(value) && value.length >= 3,
    password: (value) => value.length >= 6,
    strongPassword: (value) => {
        // At least 8 chars, 1 uppercase, 1 lowercase, 1 number, 1 special char
        return /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/.test(value);
    },
    date: (value) => !isNaN(Date.parse(value)),
    url: (value) => {
        try {
            new URL(value);
            return true;
        } catch {
            return false;
        }
    }
};

/**
 * Form Validator Class
 * Manages form validation and error tracking
 */
class FormValidator {
    constructor(formElement) {
        this.form = formElement;
        this.errors = {};
        this.touched = {};
        this.fields = {};
    }

    /**
     * Add a validation rule to a field
     * @param {string} fieldName - Name of the field
     * @param {string} ruleName - Name of the validation rule
     * @param {string} errorMessage - Error message if validation fails
     */
    addRule(fieldName, ruleName, errorMessage) {
        if (!this.fields[fieldName]) {
            this.fields[fieldName] = {
                rules: [],
                element: this.form.querySelector(`[name="${fieldName}"]`)
            };
        }

        this.fields[fieldName].rules.push({
            name: ruleName,
            validate: ValidationRules[ruleName],
            message: errorMessage
        });
    }

    /**
     * Validate all fields
     * @returns {boolean} - True if all fields are valid
     */
    validateAll() {
        this.errors = {};
        
        Object.keys(this.fields).forEach(fieldName => {
            this.validateField(fieldName);
        });

        return Object.keys(this.errors).length === 0;
    }

    /**
     * Validate a single field
     * @param {string} fieldName - Name of the field to validate
     * @returns {boolean} - True if field is valid
     */
    validateField(fieldName) {
        const field = this.fields[fieldName];
        if (!field) return true;

        const value = field.element.value;

        for (const rule of field.rules) {
            let isValid;

            if (typeof rule.validate === 'function') {
                isValid = rule.validate(value);
            } else {
                isValid = rule.validate;
            }

            if (!isValid) {
                this.errors[fieldName] = rule.message;
                return false;
            }
        }

        delete this.errors[fieldName];
        return true;
    }

    /**
     * Get all validation errors
     * @returns {object} - Object with field names as keys and error messages as values
     */
    getErrors() {
        return { ...this.errors };
    }

    /**
     * Mark a field as touched
     * @param {string} fieldName - Name of the field
     */
    markTouched(fieldName) {
        this.touched[fieldName] = true;
    }

    /**
     * Check if a field has been touched
     * @param {string} fieldName - Name of the field
     * @returns {boolean} - True if field has been touched
     */
    isTouched(fieldName) {
        return this.touched[fieldName] === true;
    }

    /**
     * Display validation errors in the form
     */
    displayErrors() {
        // Clear previous errors
        this.form.querySelectorAll('.form-error').forEach(el => {
            el.textContent = '';
            el.style.display = 'none';
        });

        // Display current errors
        Object.entries(this.errors).forEach(([fieldName, errorMessage]) => {
            const field = this.fields[fieldName];
            if (field && field.element) {
                const errorEl = field.element.parentElement.querySelector('.form-error') ||
                                (() => {
                                    const el = document.createElement('div');
                                    el.className = 'form-error';
                                    field.element.parentElement.appendChild(el);
                                    return el;
                                })();

                errorEl.textContent = errorMessage;
                errorEl.style.display = 'block';
                field.element.classList.add('error');
                field.element.classList.remove('success');
            }
        });

        // Mark successful fields
        Object.keys(this.fields).forEach(fieldName => {
            if (!this.errors[fieldName]) {
                const field = this.fields[fieldName];
                if (field && field.element) {
                    field.element.classList.remove('error');
                    field.element.classList.add('success');
                }
            }
        });
    }
}

/**
 * Reset form to initial state
 * @param {Element} form - The form to reset
 */
function resetForm(form) {
    form.reset();
    form.querySelectorAll('input, textarea, select').forEach(field => {
        field.classList.remove('error', 'success');
        const errorEl = field.parentElement.querySelector('.form-error');
        if (errorEl) {
            errorEl.textContent = '';
        }
    });
}

/**
 * Get form data as object
 * @param {Element} form - The form to extract data from
 * @returns {object} - Form data as key-value pairs
 */
function getFormData(form) {
    const formData = new FormData(form);
    const data = {};

    for (let [key, value] of formData.entries()) {
        if (data[key]) {
            // Handle multiple values for same key (arrays)
            if (!Array.isArray(data[key])) {
                data[key] = [data[key]];
            }
            data[key].push(value);
        } else {
            data[key] = value;
        }
    }

    return data;
}

/**
 * Populate form with data
 * @param {Element} form - The form to populate
 * @param {object} data - Data object with field names as keys
 */
function populateFormData(form, data) {
    Object.entries(data).forEach(([key, value]) => {
        const field = form.querySelector(`[name="${key}"]`);
        if (field) {
            if (field.type === 'checkbox') {
                field.checked = value === true || value === '1' || value === 'true';
            } else if (field.type === 'radio') {
                const radioField = form.querySelector(`[name="${key}"][value="${value}"]`);
                if (radioField) {
                    radioField.checked = true;
                }
            } else {
                field.value = value || '';
            }
        }
    });
}

/**
 * Serialize form data to URL query string
 * @param {object} data - Data object
 * @returns {string} - URL encoded query string
 */
function serializeToQueryString(data) {
    const params = new URLSearchParams();
    Object.entries(data).forEach(([key, value]) => {
        if (Array.isArray(value)) {
            value.forEach(v => params.append(key, v));
        } else if (value !== null && value !== '') {
            params.append(key, value);
        }
    });
    return params.toString();
}

/**
 * Show field validation feedback
 * @param {Element} field - The input field
 * @param {boolean} isValid - Is the field valid
 * @param {string} message - Validation message
 */
function setFieldFeedback(field, isValid, message = '') {
    field.classList.remove('error', 'success');
    field.classList.add(isValid ? 'success' : 'error');

    const errorEl = field.parentElement.querySelector('.form-error');
    if (errorEl) {
        errorEl.textContent = message;
        errorEl.style.display = isValid ? 'none' : 'block';
    }
}

/**
 * Add real-time validation to form fields
 * @param {Element} form - The form
 * @param {FormValidator} validator - The validator instance
 */
function enableRealtimeValidation(form, validator) {
    Object.entries(validator.fields).forEach(([fieldName, field]) => {
        const element = field.element;
        if (!element) return;

        element.addEventListener('blur', function() {
            validator.markTouched(fieldName);
            const isValid = validator.validateField(fieldName);
            const error = validator.getErrors()[fieldName];
            setFieldFeedback(element, isValid, error);
        });

        element.addEventListener('input', function() {
            if (validator.isTouched(fieldName)) {
                const isValid = validator.validateField(fieldName);
                const error = validator.getErrors()[fieldName];
                setFieldFeedback(element, isValid, error);
            }
        });
    });
}

/**
 * Submit form with validation
 * @param {Element} form - The form to submit
 * @param {FormValidator} validator - The validator instance
 * @param {function} onSuccess - Callback on successful validation
 * @returns {Promise} - Promise that resolves when submission completes
 */
async function submitFormWithValidation(form, validator, onSuccess) {
    return new Promise((resolve, reject) => {
        if (!validator.validateAll()) {
            validator.displayErrors();
            reject(new Error('Form validation failed'));
            return;
        }

        const formData = getFormData(form);
        
        if (typeof onSuccess === 'function') {
            onSuccess(formData, form);
        }

        resolve(formData);
    });
}

/**
 * Create a disabled form state (for loading)
 * @param {Element} form - The form
 * @param {boolean} disabled - True to disable, false to enable
 */
function setFormDisabled(form, disabled) {
    form.classList.toggle('form-loading', disabled);
    form.querySelectorAll('input, textarea, select, button').forEach(field => {
        field.disabled = disabled;
    });
}

/**
 * Export utilities for global use
 */
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        ValidationRules,
        FormValidator,
        resetForm,
        getFormData,
        populateFormData,
        serializeToQueryString,
        setFieldFeedback,
        enableRealtimeValidation,
        submitFormWithValidation,
        setFormDisabled
    };
}