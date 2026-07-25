/**
 * ═══════════════════════════════════════════════════════════════
 * FinScore AI — Frontend JavaScript
 * ═══════════════════════════════════════════════════════════════
 * Handles:
 * - Form progress bar tracking
 * - Client-side form validation
 * - API dropdown population
 * - Form submission with loading state
 * - Score circle SVG animation
 * - Counter animations
 * - Language toggle for explanations
 * - PDF download
 * - Toast notifications
 * - Dashboard chart rendering (Chart.js)
 * - CSV export
 * ═══════════════════════════════════════════════════════════════
 */

// ─── Toast Notification System ──────────────────────────────────────────────

/**
 * Show a toast notification that slides in from the right.
 * Auto-dismisses after 4 seconds.
 * 
 * @param {string} message - Notification text
 * @param {string} type - 'success' | 'error' | 'warning' | 'info'
 */
function showToast(message, type = 'info') {
    let container = document.querySelector('.toast-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container';
        container.setAttribute('aria-live', 'polite');
        document.body.appendChild(container);
    }

    const iconMap = {
        success: 'bi-check-circle-fill',
        error: 'bi-exclamation-triangle-fill',
        warning: 'bi-exclamation-circle-fill',
        info: 'bi-info-circle-fill'
    };

    const toast = document.createElement('div');
    toast.className = `toast-notification ${type}`;
    toast.setAttribute('role', 'alert');
    toast.innerHTML = `
        <i class="bi ${iconMap[type] || iconMap.info}" style="font-size:1.25rem"></i>
        <span>${message}</span>
    `;

    container.appendChild(toast);

    // Auto dismiss after 4 seconds
    setTimeout(() => {
        toast.classList.add('removing');
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}


// ─── Counter Animation ─────────────────────────────────────────────────────

/**
 * Animate a number counting up from 0 to the target value.
 * Uses requestAnimationFrame for smooth animation.
 * 
 * @param {HTMLElement} element - Element to animate
 * @param {number} target - Target number
 * @param {number} duration - Animation duration in ms
 * @param {string} suffix - Suffix to append (e.g., '+', '%')
 */
function animateCounter(element, target, duration = 2000, suffix = '') {
    const start = 0;
    const startTime = performance.now();
    
    function update(currentTime) {
        const elapsed = currentTime - startTime;
        const progress = Math.min(elapsed / duration, 1);
        
        // Ease out cubic for natural feel
        const eased = 1 - Math.pow(1 - progress, 3);
        const current = Math.floor(eased * target);
        
        element.textContent = current.toLocaleString('en-IN') + suffix;
        
        if (progress < 1) {
            requestAnimationFrame(update);
        } else {
            element.textContent = target.toLocaleString('en-IN') + suffix;
        }
    }
    
    requestAnimationFrame(update);
}

/**
 * Initialize counter animations when elements scroll into view.
 */
function initCounterAnimations() {
    const counters = document.querySelectorAll('[data-counter]');
    if (counters.length === 0) return;

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting && !entry.target.dataset.animated) {
                entry.target.dataset.animated = 'true';
                const target = parseFloat(entry.target.dataset.counter);
                const suffix = entry.target.dataset.suffix || '';
                animateCounter(entry.target, target, 2000, suffix);
            }
        });
    }, { threshold: 0.5 });

    counters.forEach(counter => observer.observe(counter));
}


// ─── Form: Populate Dropdowns from API ──────────────────────────────────────

/**
 * Fetch dropdown options from the API and populate select elements.
 * Called on form page load.
 */
async function populateDropdowns() {
    const endpoints = {
        'state-select': '/api/states',
        'occupation-select': '/api/occupations',
        'consistency-select': '/api/consistency-options'
    };

    for (const [selectId, url] of Object.entries(endpoints)) {
        const select = document.getElementById(selectId);
        if (!select) continue;

        try {
            const response = await fetch(url);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            
            const options = await response.json();
            
            // Clear existing options except the first (placeholder)
            while (select.options.length > 1) {
                select.remove(1);
            }
            
            // Add API options
            options.forEach(option => {
                const opt = document.createElement('option');
                opt.value = option;
                opt.textContent = option;
                select.appendChild(opt);
            });
            
        } catch (error) {
            console.error(`Failed to load ${selectId}:`, error);
            showToast(`Failed to load ${selectId.replace('-select', '')} options`, 'warning');
        }
    }
}


// ─── Form: Progress Bar ─────────────────────────────────────────────────────

/**
 * Track form completion and update the progress bar.
 */
function initProgressBar() {
    const form = document.getElementById('assessment-form');
    const progressFill = document.getElementById('progress-fill');
    const progressLabel = document.getElementById('progress-label');
    
    if (!form || !progressFill) return;

    const requiredFields = form.querySelectorAll('[required]');
    const totalFields = requiredFields.length;

    function updateProgress() {
        let filledCount = 0;
        
        requiredFields.forEach(field => {
            if (field.type === 'checkbox') {
                if (field.checked) filledCount++;
            } else if (field.value && field.value.trim() !== '') {
                filledCount++;
            }
        });

        const percentage = Math.round((filledCount / totalFields) * 100);
        progressFill.style.width = `${percentage}%`;
        if (progressLabel) {
            progressLabel.textContent = `${percentage}% complete`;
        }
    }

    // Listen for changes on all required fields
    requiredFields.forEach(field => {
        field.addEventListener('input', updateProgress);
        field.addEventListener('change', updateProgress);
    });

    // Initial update
    updateProgress();
}


// ─── Form: Client-Side Validation ───────────────────────────────────────────

/**
 * Validate form fields before submission.
 * Returns true if all fields are valid, false otherwise.
 */
function validateForm() {
    const form = document.getElementById('assessment-form');
    if (!form) return false;

    let isValid = true;
    const errors = [];

    // Clear previous errors
    form.querySelectorAll('.form-floating-custom').forEach(group => {
        group.classList.remove('has-error');
    });

    // Check consent
    const consent = document.getElementById('consent-checkbox');
    if (consent && !consent.checked) {
        errors.push('Please give consent to proceed');
        isValid = false;
    }

    // Validate required fields
    const requiredFields = [
        { id: 'fullName', label: 'Full Name', type: 'text' },
        { id: 'state-select', label: 'State', type: 'select' },
        { id: 'occupation-select', label: 'Occupation', type: 'select' },
        { id: 'monthlyIncome', label: 'Monthly Income', type: 'number', min: 0 },
        { id: 'upiFrequency', label: 'UPI Frequency', type: 'number', min: 0 },
        { id: 'avgUpiAmount', label: 'Avg UPI Amount', type: 'number', min: 0 },
        { id: 'consistency-select', label: 'Utility Consistency', type: 'select' },
        { id: 'rechargeAmount', label: 'Recharge Amount', type: 'number', min: 0 },
        { id: 'loanRequested', label: 'Loan Amount', type: 'number', min: 1 }
    ];

    requiredFields.forEach(({ id, label, type, min }) => {
        const field = document.getElementById(id);
        if (!field) return;

        const parent = field.closest('.form-floating-custom');

        if (!field.value || field.value.trim() === '') {
            if (parent) parent.classList.add('has-error');
            errors.push(`${label} is required`);
            isValid = false;
        } else if (type === 'number' && min !== undefined && parseFloat(field.value) < min) {
            if (parent) parent.classList.add('has-error');
            errors.push(`${label} must be at least ${min}`);
            isValid = false;
        }
    });

    if (!isValid) {
        showToast(errors[0], 'error');
    }

    return isValid;
}


// ─── Form: Submission ───────────────────────────────────────────────────────

/**
 * Handle form submission:
 * 1. Validate all fields
 * 2. Show loading overlay
 * 3. POST to /api/credit/score
 * 4. Redirect to result page on success
 */
async function submitForm(event) {
    event.preventDefault();

    if (!validateForm()) return;

    // Show loading overlay
    const overlay = document.getElementById('loading-overlay');
    if (overlay) overlay.classList.add('active');

    try {
        // Build request body
        const formData = {
            fullName: document.getElementById('fullName').value.trim(),
            gender: document.getElementById('gender-select').value,
            state: document.getElementById('state-select').value,
            occupation: document.getElementById('occupation-select').value,
            monthlyIncome: parseFloat(document.getElementById('monthlyIncome').value),
            upiFrequency: parseInt(document.getElementById('upiFrequency').value),
            avgUpiAmount: parseFloat(document.getElementById('avgUpiAmount').value),
            utilityConsistency: document.getElementById('consistency-select').value,
            rechargeAmount: parseFloat(document.getElementById('rechargeAmount').value),
            agriculturalYield: parseFloat(document.getElementById('agriculturalYield').value) || 0,
            loanRequested: parseFloat(document.getElementById('loanRequested').value),
            preferredLanguage: document.getElementById('language-select').value,
            consentGiven: document.getElementById('consent-checkbox').checked
        };

        const response = await fetch('/api/credit/score', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(formData)
        });

        const result = await response.json();

        if (response.ok) {
            showToast('Assessment complete! Redirecting...', 'success');
            // Redirect to result page
            setTimeout(() => {
                window.location.href = `/result/${result.id}`;
            }, 800);
        } else {
            throw new Error(result.error || 'Assessment failed');
        }

    } catch (error) {
        console.error('Form submission error:', error);
        showToast(error.message || 'Something went wrong. Please try again.', 'error');
    } finally {
        if (overlay) overlay.classList.remove('active');
    }
}


// ─── Result: Score Circle Animation ─────────────────────────────────────────

/**
 * Animate the SVG circular score meter from 0 to the actual score.
 * Color changes based on risk category.
 * 
 * @param {number} score - Credit score (0-100)
 * @param {string} riskClass - CSS class for risk category color
 */
function animateScoreCircle(score, riskClass) {
    const circle = document.querySelector('.circle-progress');
    const scoreDisplay = document.getElementById('score-display');
    
    if (!circle) return;

    // SVG circle circumference: 2 * PI * radius (90) = ~565.48
    const circumference = 565.48;
    const targetOffset = circumference - (score / 100) * circumference;

    // Add risk class for color
    circle.classList.add(riskClass);

    // Animate after a short delay for visual impact
    setTimeout(() => {
        circle.style.strokeDashoffset = targetOffset;
    }, 300);

    // Animate the number counting up
    if (scoreDisplay) {
        animateCounter(scoreDisplay, score, 2000);
    }
}


// ─── Result: Language Toggle ────────────────────────────────────────────────

/**
 * Toggle explanation language between Hindi and English.
 * Fetches a fresh explanation from /api/credit/explain.
 * 
 * @param {number} scoreId - Assessment database ID
 */
async function toggleLanguage(scoreId) {
    const toggleBtn = document.getElementById('language-toggle-btn');
    const explanationDiv = document.getElementById('explanation-content');
    
    if (!toggleBtn || !explanationDiv) return;

    // Determine target language
    const currentLang = toggleBtn.dataset.currentLang || 'English';
    const targetLang = currentLang === 'English' ? 'Hindi' : 'English';

    // Show skeleton loading
    explanationDiv.innerHTML = `
        <div class="skeleton skeleton-title"></div>
        <div class="skeleton skeleton-text"></div>
        <div class="skeleton skeleton-text" style="width:80%"></div>
        <div class="skeleton skeleton-paragraph"></div>
    `;

    try {
        const response = await fetch('/api/credit/explain', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                scoreId: scoreId,
                language: targetLang
            })
        });

        const result = await response.json();

        if (response.ok) {
            explanationDiv.innerHTML = `<p class="explanation-text">${result.explanation}</p>`;
            toggleBtn.dataset.currentLang = targetLang;
            toggleBtn.innerHTML = `<i class="bi bi-translate"></i> ${targetLang === 'English' ? 'हिंदी' : 'English'}`;
            showToast(`Switched to ${targetLang}`, 'success');
        } else {
            throw new Error(result.error || 'Failed to fetch explanation');
        }

    } catch (error) {
        console.error('Language toggle error:', error);
        explanationDiv.innerHTML = '<p class="explanation-text">Unable to load explanation. Please try again.</p>';
        showToast('Failed to switch language', 'error');
    }
}


// ─── Result: PDF Download ───────────────────────────────────────────────────

/**
 * Download the result page as a PDF using html2canvas + jsPDF.
 * Captures the main result content area.
 */
async function downloadPDF() {
    showToast('Generating PDF...', 'info');
    
    try {
        // Dynamically load html2canvas and jsPDF if not already loaded
        if (typeof html2canvas === 'undefined') {
            await loadScript('https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js');
        }
        if (typeof jspdf === 'undefined' && typeof jsPDF === 'undefined') {
            await loadScript('https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js');
        }

        const element = document.getElementById('result-content');
        if (!element) {
            showToast('Could not find result content', 'error');
            return;
        }

        const canvas = await html2canvas(element, {
            scale: 2,
            useCORS: true,
            logging: false,
            backgroundColor: '#ffffff'
        });

        const imgData = canvas.toDataURL('image/png');
        const { jsPDF } = window.jspdf || window;
        const pdf = new jsPDF('p', 'mm', 'a4');
        
        const pdfWidth = pdf.internal.pageSize.getWidth();
        const pdfHeight = (canvas.height * pdfWidth) / canvas.width;
        
        pdf.addImage(imgData, 'PNG', 0, 0, pdfWidth, pdfHeight);
        pdf.save('FinScore_AI_Credit_Assessment.pdf');
        
        showToast('PDF downloaded successfully!', 'success');
        
    } catch (error) {
        console.error('PDF generation error:', error);
        showToast('Failed to generate PDF. Please try again.', 'error');
    }
}

/**
 * Helper to dynamically load a script.
 */
function loadScript(src) {
    return new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.src = src;
        script.onload = resolve;
        script.onerror = reject;
        document.head.appendChild(script);
    });
}


// ─── Dashboard: Load Stats & Charts ─────────────────────────────────────────

/**
 * Load dashboard summary statistics from /api/dashboard/stats.
 */
async function loadDashboardStats() {
    try {
        const response = await fetch('/api/dashboard/stats');
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        
        const stats = await response.json();

        // Animate stat counters
        const statElements = {
            'stat-total': { value: stats.totalAssessments || 0, suffix: '' },
            'stat-avg-score': { value: stats.avgScore || 0, suffix: '' },
            'stat-eligibility': { value: stats.eligibilityRate || 0, suffix: '%' },
        };

        for (const [id, { value, suffix }] of Object.entries(statElements)) {
            const el = document.getElementById(id);
            if (el) animateCounter(el, value, 1500, suffix);
        }

        // Top occupation (text, no animation)
        const topOcc = document.getElementById('stat-top-occupation');
        if (topOcc) topOcc.textContent = stats.topOccupation || 'N/A';

    } catch (error) {
        console.error('Dashboard stats error:', error);
        showToast('Failed to load dashboard statistics', 'warning');
    }
}

/**
 * Load and render all dashboard charts using Chart.js.
 * Data comes from /api/dashboard/charts.
 */
async function loadDashboardCharts() {
    try {
        const response = await fetch('/api/dashboard/charts');
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        
        const data = await response.json();

        // Color palette for charts
        const colors = {
            primary: '#1a237e',
            primaryLight: 'rgba(26, 35, 126, 0.6)',
            saffron: '#ff6f00',
            saffronLight: 'rgba(255, 111, 0, 0.6)',
            success: '#2e7d32',
            danger: '#c62828',
            warning: '#f57f17',
            bgPrimary: 'rgba(26, 35, 126, 0.1)',
            bgSaffron: 'rgba(255, 111, 0, 0.1)',
        };

        // Chart.js defaults
        Chart.defaults.font.family = "'Inter', sans-serif";
        Chart.defaults.font.size = 12;
        Chart.defaults.color = '#757575';

        // ── Score Distribution Histogram ────────────────────────────
        renderChart('scoreDistChart', 'bar', {
            labels: data.scoreDistribution?.labels || [],
            datasets: [{
                label: 'Number of Assessments',
                data: data.scoreDistribution?.values || [],
                backgroundColor: colors.saffronLight,
                borderColor: colors.saffron,
                borderWidth: 2,
                borderRadius: 6
            }]
        }, { plugins: { legend: { display: false } } });

        // ── State-wise Average Score ────────────────────────────────
        renderChart('stateChart', 'bar', {
            labels: (data.stateWiseScore?.labels || []).slice(0, 10),
            datasets: [{
                label: 'Average Score',
                data: (data.stateWiseScore?.values || []).slice(0, 10),
                backgroundColor: colors.primaryLight,
                borderColor: colors.primary,
                borderWidth: 2,
                borderRadius: 6
            }]
        }, { 
            indexAxis: 'y',
            plugins: { legend: { display: false } }
        });

        // ── Occupation-wise Comparison ──────────────────────────────
        renderChart('occupationChart', 'bar', {
            labels: data.occupationWiseScore?.labels || [],
            datasets: [{
                label: 'Average Score',
                data: data.occupationWiseScore?.values || [],
                backgroundColor: [
                    colors.primaryLight, colors.saffronLight,
                    'rgba(46, 125, 50, 0.6)', 'rgba(198, 40, 40, 0.6)',
                    'rgba(245, 127, 23, 0.6)', 'rgba(74, 20, 140, 0.6)',
                    'rgba(0, 121, 107, 0.6)', 'rgba(21, 101, 192, 0.6)',
                    'rgba(130, 119, 23, 0.6)'
                ],
                borderWidth: 2,
                borderRadius: 6
            }]
        }, { plugins: { legend: { display: false } } });

        // ── Gender-wise Comparison (Bias Monitoring) ────────────────
        renderChart('genderChart', 'bar', {
            labels: data.genderWiseScore?.labels || [],
            datasets: [{
                label: 'Average Score',
                data: data.genderWiseScore?.values || [],
                backgroundColor: [colors.primaryLight, colors.saffronLight, 'rgba(46, 125, 50, 0.6)'],
                borderColor: [colors.primary, colors.saffron, colors.success],
                borderWidth: 2,
                borderRadius: 6
            }]
        }, {
            plugins: {
                title: { display: true, text: 'Bias Monitoring — Scores Should Be Similar Across Genders', font: { size: 11 } }
            }
        });

        // ── Daily Assessment Volume ─────────────────────────────────
        renderChart('volumeChart', 'line', {
            labels: data.dailyVolume?.labels || [],
            datasets: [{
                label: 'Assessments',
                data: data.dailyVolume?.values || [],
                borderColor: colors.saffron,
                backgroundColor: colors.bgSaffron,
                fill: true,
                tension: 0.4,
                pointRadius: 4,
                pointBackgroundColor: colors.saffron
            }]
        }, { plugins: { legend: { display: false } } });

    } catch (error) {
        console.error('Dashboard charts error:', error);
        showToast('Failed to load charts', 'warning');
    }
}

/**
 * Helper to render a Chart.js chart.
 */
function renderChart(canvasId, type, data, extraOptions = {}) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    
    new Chart(ctx, {
        type: type,
        data: data,
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: { beginAtZero: true, grid: { color: 'rgba(0,0,0,0.05)' } },
                x: { grid: { display: false } }
            },
            ...extraOptions
        }
    });
}


// ─── Dashboard: Load Assessment Table ───────────────────────────────────────

let currentPage = 0;
const pageSize = 10;

/**
 * Load paginated assessment data into the dashboard table.
 */
async function loadAssessmentTable(page = 0) {
    const tableBody = document.getElementById('assessment-table-body');
    if (!tableBody) return;

    try {
        const response = await fetch(`/api/credit/history?page=${page}&size=${pageSize}`);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        
        const data = await response.json();
        
        tableBody.innerHTML = '';
        
        if (!data.content || data.content.length === 0) {
            tableBody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:2rem;color:#757575">No assessments yet</td></tr>';
            return;
        }

        data.content.forEach(item => {
            const riskClass = item.riskCategory === 'Low Risk' ? 'low-risk' 
                            : item.riskCategory === 'Medium Risk' ? 'medium-risk' : 'high-risk';
            
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${item.fullName || 'N/A'}</td>
                <td>${item.occupation || 'N/A'}</td>
                <td>${item.state || 'N/A'}</td>
                <td><strong>${item.creditScore ?? 'N/A'}</strong></td>
                <td><span class="risk-badge ${riskClass}" style="font-size:0.7rem;padding:0.25rem 0.75rem">${item.riskCategory || 'N/A'}</span></td>
                <td>${item.loanEligible ? '✅ Yes' : '❌ No'}</td>
                <td>${item.createdAt ? new Date(item.createdAt).toLocaleDateString('en-IN') : 'N/A'}</td>
            `;
            tableBody.appendChild(row);
        });

        // Update pagination
        currentPage = page;
        renderPagination(data.totalPages, data.currentPage);

    } catch (error) {
        console.error('Assessment table error:', error);
        tableBody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:2rem;color:#c62828">Failed to load data</td></tr>';
    }
}

/**
 * Render pagination buttons.
 */
function renderPagination(totalPages, currentPage) {
    const container = document.getElementById('pagination-container');
    if (!container) return;
    
    container.innerHTML = '';
    
    if (totalPages <= 1) return;

    for (let i = 0; i < totalPages && i < 10; i++) {
        const btn = document.createElement('button');
        btn.className = `pagination-btn ${i === currentPage ? 'active' : ''}`;
        btn.textContent = i + 1;
        btn.setAttribute('aria-label', `Page ${i + 1}`);
        btn.addEventListener('click', () => loadAssessmentTable(i));
        container.appendChild(btn);
    }
}


// ─── Dashboard: CSV Export ──────────────────────────────────────────────────

/**
 * Export all assessment data as a CSV file.
 */
async function exportCSV() {
    showToast('Generating CSV export...', 'info');
    
    try {
        // Fetch all data (large page size)
        const response = await fetch('/api/credit/history?page=0&size=10000');
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        
        const data = await response.json();
        
        if (!data.content || data.content.length === 0) {
            showToast('No data to export', 'warning');
            return;
        }

        // Build CSV
        const headers = ['Name', 'Gender', 'State', 'Occupation', 'Monthly Income', 
                         'UPI Frequency', 'Avg UPI Amount', 'Utility Consistency',
                         'Recharge Amount', 'Agricultural Yield', 'Loan Requested',
                         'Credit Score', 'Risk Category', 'Loan Eligible', 
                         'Max Loan Amount', 'Interest Rate', 'Date'];
        
        let csv = headers.join(',') + '\n';
        
        data.content.forEach(item => {
            const row = [
                `"${item.fullName || ''}"`,
                `"${item.gender || ''}"`,
                `"${item.state || ''}"`,
                `"${item.occupation || ''}"`,
                item.monthlyIncome || 0,
                item.upiFrequency || 0,
                item.avgUpiAmount || 0,
                `"${item.utilityConsistency || ''}"`,
                item.rechargeAmount || 0,
                item.agriculturalYield || 0,
                item.loanRequested || 0,
                item.creditScore || 0,
                `"${item.riskCategory || ''}"`,
                item.loanEligible ? 'Yes' : 'No',
                item.maxLoanAmount || 0,
                `"${item.suggestedInterestRate || ''}"`,
                item.createdAt ? new Date(item.createdAt).toLocaleDateString('en-IN') : ''
            ];
            csv += row.join(',') + '\n';
        });

        // Download
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `FinScore_AI_Assessments_${new Date().toISOString().split('T')[0]}.csv`;
        link.click();
        
        showToast('CSV exported successfully!', 'success');
        
    } catch (error) {
        console.error('CSV export error:', error);
        showToast('Failed to export CSV', 'error');
    }
}


// ─── Page Initialization ────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
    // Initialize counter animations (home page)
    initCounterAnimations();

    // Form page initialization
    const assessmentForm = document.getElementById('assessment-form');
    if (assessmentForm) {
        populateDropdowns();
        initProgressBar();
        assessmentForm.addEventListener('submit', submitForm);
    }

    // Result page initialization
    const scoreCircle = document.querySelector('.score-circle');
    if (scoreCircle) {
        const score = parseInt(scoreCircle.dataset.score || '0');
        const riskClass = scoreCircle.dataset.risk || 'medium-risk';
        animateScoreCircle(score, riskClass);
    }

    // Dashboard page initialization
    const dashboardPage = document.getElementById('dashboard-page');
    if (dashboardPage) {
        loadDashboardStats();
        loadDashboardCharts();
        loadAssessmentTable(0);
    }
});
