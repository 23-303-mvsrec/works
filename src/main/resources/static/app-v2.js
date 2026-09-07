/* ═══════════════════════════════════════════════════════════════════════════
   HMWSSB Works Management & Estimation System - Modern Client API & Shared Engine (V2)
   Compliance: GIGW 3.0 / UX4G
   ═══════════════════════════════════════════════════════════════════════════ */

window.HMWSSB = window.HMWSSB || {};

// Configuration
HMWSSB.CONFIG = {
  API_BASE: '/api',
  SESSION_KEY: 'hmwssb_logged_in_user',
  ESTIMATE_KEY: 'current_estimate_data',
  ESTIMATE_V2_KEY: 'current_estimate_data_v2',
  SESSION_TIMEOUT_HOURS: 8,
  FINANCIAL_YEAR: '2024–2025'
};

// ── Authentication & Session Service ─────────────────────────────────────────
HMWSSB.Auth = {
  getUser: function () {
    var raw = localStorage.getItem(HMWSSB.CONFIG.SESSION_KEY);
    if (!raw) return null;
    try {
      var user = JSON.parse(raw);
      if (user._loginTimestamp) {
        var hours = (Date.now() - user._loginTimestamp) / (1000 * 60 * 60);
        if (hours > HMWSSB.CONFIG.SESSION_TIMEOUT_HOURS) {
          this.logout();
          return null;
        }
      }
      return user;
    } catch (e) {
      return null;
    }
  },

  setUser: function (user) {
    if (!user) return;
    user._loginTimestamp = Date.now();
    localStorage.setItem(HMWSSB.CONFIG.SESSION_KEY, JSON.stringify(user));
  },

  isLoggedIn: function () {
    return this.getUser() !== null;
  },

  requireLogin: function () {
    var user = this.getUser();
    if (!user) {
      window.location.href = 'login-v2.html';
      return null;
    }
    return user;
  },

  hasRole: function (targetRole) {
    var user = this.getUser();
    if (!user) return false;
    var tr = (targetRole || '').toUpperCase();
    var pr = (user.role || '').toUpperCase();
    if (pr === 'ADMIN' || pr === 'DOP' || pr === tr) return true;
    if (user.locations && Array.isArray(user.locations)) {
      for (var i = 0; i < user.locations.length; i++) {
        var lr = (user.locations[i].role || pr).toUpperCase();
        if (lr === tr) return true;
      }
    }
    return false;
  },

  logout: function () {
    localStorage.removeItem(HMWSSB.CONFIG.SESSION_KEY);
    localStorage.removeItem(HMWSSB.CONFIG.ESTIMATE_KEY);
    localStorage.removeItem(HMWSSB.CONFIG.ESTIMATE_V2_KEY);
    window.location.href = 'login-v2.html';
  }
};

// ── REST API Client ──────────────────────────────────────────────────────────
HMWSSB.API = {
  get: async function (path) {
    var res = await fetch(HMWSSB.CONFIG.API_BASE + path);
    if (!res.ok) throw new Error('API Error ' + res.status + ': ' + (await res.text()));
    return res.json();
  },

  post: async function (path, data) {
    var res = await fetch(HMWSSB.CONFIG.API_BASE + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!res.ok) {
      var msg = await res.text();
      throw new Error(msg || 'Request failed with status ' + res.status);
    }
    return res.json();
  },

  searchItems: async function (query) {
    if (!query || query.trim().length < 2) return [];
    return this.get('/items/search?q=' + encodeURIComponent(query.trim()));
  },

  getEstimates: async function (params) {
    var q = '';
    if (params) {
      var parts = [];
      for (var k in params) {
        if (params[k]) parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(params[k]));
      }
      if (parts.length) q = '?' + parts.join('&');
    }
    return this.get('/estimates' + q);
  },

  getEstimate: async function (id) {
    return this.get('/estimates/' + id);
  },

  saveEstimate: async function (estimate) {
    return this.post('/estimates', estimate);
  },

  performAction: async function (id, action, officerPhone, remarks, tags) {
    return this.post('/estimates/' + id + '/action', {
      action: action,
      officerPhone: officerPhone,
      remarks: remarks || '',
      tags: tags || ''
    });
  },

  getRemarks: async function (id) {
    return this.get('/estimates/' + id + '/remarks');
  },

  getRevisions: async function (id) {
    return this.get('/estimates/' + id + '/revisions');
  },

  restoreRevision: async function (id, revNum, officerPhone) {
    var res = await fetch(HMWSSB.CONFIG.API_BASE + '/estimates/' + id + '/revisions/' + revNum + '/restore?officerPhone=' + encodeURIComponent(officerPhone), {
      method: 'POST'
    });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  },

  getJurisdictions: async function () {
    try {
      return await this.get('/jurisdictions');
    } catch (e) {
      // Fallback to static hierarchy.json if endpoint unavailable
      var res = await fetch('hierarchy.json');
      return res.json();
    }
  }
};

// ── Formatters & Numerical Precision Helpers ─────────────────────────────────
HMWSSB.Utils = {
  round: function (num, decimals) {
    decimals = decimals !== undefined ? decimals : 2;
    var factor = Math.pow(10, decimals);
    return Math.round((Number(num) || 0) * factor) / factor;
  },

  formatCurrency: function (num) {
    var val = Number(num) || 0;
    return val.toLocaleString('en-IN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
  },

  formatDate: function (dateStr) {
    if (!dateStr) return '—';
    var d = new Date(dateStr);
    return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) + ' ' +
           d.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
  },

  // FIXED Legal Indian Currency Converter (Accurate Paise conversion)
  numberToRupeeWords: function (num) {
    var n = Math.round((Number(num) || 0) * 100) / 100;
    if (n === 0) return 'Rupees Zero Only';

    var ones = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten',
      'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'];
    var tens = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

    function helper(v) {
      if (v < 20) return ones[v];
      if (v < 100) return tens[Math.floor(v / 10)] + (v % 10 !== 0 ? ' ' + ones[v % 10] : '');
      if (v < 1000) return ones[Math.floor(v / 100)] + ' Hundred' + (v % 100 !== 0 ? ' ' + helper(v % 100) : '');
      if (v < 100000) return helper(Math.floor(v / 1000)) + ' Thousand' + (v % 1000 !== 0 ? ' ' + helper(v % 1000) : '');
      if (v < 10000000) return helper(Math.floor(v / 100000)) + ' Lakh' + (v % 100000 !== 0 ? ' ' + helper(v % 100000) : '');
      return helper(Math.floor(v / 10000000)) + ' Crore' + (v % 10000000 !== 0 ? ' ' + helper(v % 10000000) : '');
    }

    // Fix: toFixed(2) guarantees exactly 2 digits after decimal for paise
    var parts = n.toFixed(2).split('.');
    var rupees = parseInt(parts[0], 10);
    var paise = parseInt(parts[1], 10);

    var result = 'Rupees ' + helper(rupees);
    if (paise > 0) {
      result += ' and ' + helper(paise) + ' Paise';
    }
    result += ' Only';
    return result;
  },

  getStatusBadge: function (status) {
    status = status || 'DRAFT';
    switch (status) {
      case 'DRAFT': return '<span class="badge badge-draft">Draft</span>';
      case 'SUBMITTED_TO_DGM': return '<span class="badge badge-pending">Pending DGM</span>';
      case 'SUBMITTED_TO_GM': return '<span class="badge badge-pending">Pending GM</span>';
      case 'SUBMITTED_TO_CGM': return '<span class="badge badge-pending">Pending CGM</span>';
      case 'SUBMITTED_TO_DOP': return '<span class="badge badge-pending">Pending DOP</span>';
      case 'APPROVED': return '<span class="badge badge-approved">Approved & Sanctioned</span>';
      default: return '<span class="badge badge-draft">' + status + '</span>';
    }
  }
};

// ── Shared UI Shell Renderer (GIGW 3.0 / HMWSSB Standard) ────────────────────
HMWSSB.UI = {
  renderHeader: function (activePage) {
    var user = HMWSSB.Auth.getUser() || { name: 'Guest Officer', role: 'OFFICER', designation: 'Guest', phoneNumber: '0000000000' };

    var headerHtml = `
      <!-- 1. GIGW Accessibility Top Ribbon -->
      <div class="gov-ribbon">
        <div style="display:flex; align-items:center; gap:8px;">
          <div class="tricolor-flag">
            <span></span><span></span><span></span>
          </div>
          <span style="font-family:var(--font-telugu); font-size:11px; color:#A7F3D0;">తెలంగాణ ప్రభుత్వం</span>
          <span style="color:var(--slate-500);">|</span>
          <span style="font-weight:600; color:var(--slate-200);">Government of Telangana</span>
        </div>
        <div style="display:flex; align-items:center; gap:14px; font-size:11px;">
          <a href="#main-content">Skip to Main Content</a>
          <div style="display:flex; align-items:center; background:#1E293B; border-radius:4px; padding:1px 6px; gap:6px;">
            <button onclick="document.body.style.fontSize='12px'" style="background:none;border:none;color:#fff;cursor:pointer;font-weight:700;">A-</button>
            <button onclick="document.body.style.fontSize='14px'" style="background:none;border:none;color:#fff;cursor:pointer;font-weight:700;">A</button>
            <button onclick="document.body.style.fontSize='16px'" style="background:none;border:none;color:#fff;cursor:pointer;font-weight:700;">A+</button>
          </div>
          <button onclick="document.body.classList.toggle('high-contrast')" style="background:none;border:none;color:var(--slate-300);cursor:pointer;font-size:11px;display:flex;align-items:center;gap:4px;">
            <span>◑</span> High Contrast
          </button>
          <span style="color:var(--ashok-gold-400); font-family:var(--font-telugu); font-weight:700;">తెలుగు</span>
        </div>
      </div>

      <!-- 2. Official Executive Header Banner -->
      <header class="gov-header">
        <div class="gov-banner-container">
          <a href="dashboard-v2.html" class="banner-logo-link" title="HMWSSB Home">
            <img src="assets/images/hmwssb-logo-title.png" alt="HMWSSB - Hyderabad Metropolitan Water Supply & Sewerage Board" class="gov-banner-logo">
          </a>
          <div class="gov-banner-dignitaries">
            <img src="assets/images/hmwssb-dignitaries.png" alt="Sri A Revanth Reddy (Hon'ble Chief Minister), Government of Telangana, Sri K Ashok Reddy, IAS (MD, HMWSSB)" class="gov-banner-dignitaries-img">
          </div>
        </div>
      </header>

      <!-- 3. Primary Departmental Navigation Bar -->
      <nav class="gov-nav">
        <ul class="nav-links">
          <li><a href="dashboard-v2.html" class="${activePage === 'dashboard' ? 'active' : ''}">📊 Dashboard</a></li>
          <li><a href="measurement-v2.html" class="${activePage === 'measurement' ? 'active' : ''}">📐 Measurement Book (MB)</a></li>
          <li><a href="abstract-v2.html" class="${activePage === 'abstract' ? 'active' : ''}">📑 General Abstract</a></li>
          <li><a href="ssr-v2.html" class="${activePage === 'ssr' ? 'active' : ''}">📖 SSR Rate Master</a></li>
          <li><a href="approvals-v2.html" class="${activePage === 'approvals' ? 'active' : ''}">✅ Scrutiny & Sanctions</a></li>
        </ul>
        <div class="nav-extra">
          <div class="nav-officer-pill">
            <span class="officer-role-tag">${user.role || 'OFFICER'}</span>
            <span class="officer-name-text" title="${user.name || 'Officer'}">${user.name || 'Officer'}</span>
          </div>
          <button onclick="HMWSSB.Auth.logout()" class="btn-logout" title="Sign Out">Sign Out</button>
          <span class="nav-fy-badge">FY: <b>${HMWSSB.CONFIG.FINANCIAL_YEAR}</b></span>
          <a href="index.html" class="nav-classic-btn" title="Switch back to legacy AngularJS application">
            ↩ Classic V1
          </a>
        </div>
      </nav>

      <!-- 4. Official Notification Ticker -->
      <div class="gov-ticker">
        <span class="tag">Official Circular</span>
        <marquee scrollamount="5" style="font-size:12px; font-weight:500;">
          <b>G.O. Rt. No. 142 (MA&UD Dept):</b> Implementation of Standard Schedule of Rates (SSR 2024-25) with mandatory 18% GST and 1% Labour Welfare Cess. All Measurement Books must be digitally submitted through WMB-GAES V2 Portal.
        </marquee>
      </div>
    `;

    var container = document.getElementById('gov-app-header');
    if (container) {
      container.innerHTML = headerHtml;
    }
  },

  renderFooter: function () {
    var footerHtml = `
      <footer class="gov-footer">
        <div class="gov-footer-content">
          <div>
            <div style="font-weight:700; color:var(--white); font-size:13px;">Hyderabad Metropolitan Water Supply and Sewerage Board</div>
            <p style="margin-top:4px;">Government of Telangana • Administrative Building, Khairatabad, Hyderabad - 500004</p>
            <p style="margin-top:2px; font-size:11px; color:var(--slate-500);">Compliant with Guidelines for Indian Government Websites (GIGW 3.0) & UX4G Design Standards</p>
          </div>
          <div style="text-align:right;">
            <div style="font-size:11px; color:var(--slate-400);">Designed for Official Departmental Use Only</div>
            <div style="margin-top:4px; display:flex; gap:12px; justify-content:flex-end;">
              <a href="dashboard-v2.html" style="color:var(--ashok-gold-400); text-decoration:none;">V2 Portal</a> •
              <a href="index.html" style="color:var(--slate-400); text-decoration:none;">Classic View</a> •
              <a href="admin.html" style="color:var(--slate-400); text-decoration:none;">Officer Admin</a>
            </div>
          </div>
        </div>
      </footer>
    `;

    var container = document.getElementById('gov-app-footer');
    if (container) {
      container.innerHTML = footerHtml;
    }
  }
};
