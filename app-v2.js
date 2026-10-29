/* ═══════════════════════════════════════════════════════════════════════════
   HMWSSB Works Management & Estimation System - Modern Client API & Shared Engine (V2)
   Compliance: GIGW 3.0 / UX4G
   Authoritative Parity: V1 Engineering & Financial Workflow Reference
   ═══════════════════════════════════════════════════════════════════════════ */

window.HMWSSB = window.HMWSSB || {};

// ── Configuration ────────────────────────────────────────────────────────────
HMWSSB.CONFIG = {
  API_BASE: '/api',
  SESSION_KEY: 'hmwssb_logged_in_user',
  ESTIMATE_KEY: 'current_estimate_data',
  ESTIMATE_V2_KEY: 'current_estimate_data_v2',
  ACTIVE_LOCATION_KEY: 'current_active_location_key',
  ACTIVE_ROLE_KEY: 'current_active_role',
  ACTIVE_LOCATION_DATA: 'current_active_location',
  SESSION_TIMEOUT_HOURS: 8,
  FINANCIAL_YEAR: '2026–2027',
  ROLES: {
    MANAGER: { name: 'Manager (Engg)', level: 1, next: 'SUBMITTED_TO_DGM' },
    DGM: { name: 'Deputy General Manager', level: 2, next: 'SUBMITTED_TO_GM' },
    GM: { name: 'General Manager', level: 3, next: 'SUBMITTED_TO_CGM' },
    CGM: { name: 'Chief General Manager', level: 4, next: 'SUBMITTED_TO_DOP' },
    DOP: { name: 'Director of Projects', level: 5, next: 'APPROVED' },
    ADMIN: { name: 'System Administrator', level: 99, next: null }
  }
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
    var prevUser = this.getUser();
    if (prevUser && String(prevUser.phoneNumber) !== String(user.phoneNumber)) {
      localStorage.removeItem(HMWSSB.CONFIG.ESTIMATE_KEY);
      localStorage.removeItem(HMWSSB.CONFIG.ESTIMATE_V2_KEY);
      localStorage.removeItem(HMWSSB.CONFIG.ACTIVE_LOCATION_KEY);
      localStorage.removeItem(HMWSSB.CONFIG.ACTIVE_ROLE_KEY);
      localStorage.removeItem(HMWSSB.CONFIG.ACTIVE_LOCATION_DATA);
    }
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
    if (pr === 'ADMIN' || pr === tr) return true;
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
    localStorage.removeItem(HMWSSB.CONFIG.ACTIVE_LOCATION_KEY);
    localStorage.removeItem(HMWSSB.CONFIG.ACTIVE_ROLE_KEY);
    localStorage.removeItem(HMWSSB.CONFIG.ACTIVE_LOCATION_DATA);
    window.location.href = 'login-v2.html';
  }
};

// ── REST API Client ──────────────────────────────────────────────────────────
HMWSSB.API = {
  get: async function (path) {
    var res = await fetch(HMWSSB.CONFIG.API_BASE + path);
    if (!res.ok) {
      var text = await res.text();
      throw new Error(text || 'API Error ' + res.status);
    }
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

  put: async function (path, data) {
    var res = await fetch(HMWSSB.CONFIG.API_BASE + path, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!res.ok) {
      var msg = await res.text();
      throw new Error(msg || 'Request failed with status ' + res.status);
    }
    return res.json();
  },

  delete: async function (path) {
    var res = await fetch(HMWSSB.CONFIG.API_BASE + path, {
      method: 'DELETE'
    });
    if (!res.ok) {
      var msg = await res.text();
      throw new Error(msg || 'Request failed with status ' + res.status);
    }
    try {
      return await res.json();
    } catch (e) {
      return { success: true };
    }
  },

  // Estimates
  getEstimates: async function (params) {
    var q = '';
    if (params) {
      var parts = [];
      for (var k in params) {
        if (params[k] !== undefined && params[k] !== null && params[k] !== '') {
          parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(params[k]));
        }
      }
      if (parts.length) q = '?' + parts.join('&');
    }
    return this.get('/estimates' + q);
  },

  getEstimate: async function (id) {
    return this.get('/estimates/' + id);
  },

  saveEstimate: async function (estimate) {
    var user = HMWSSB.Auth.getUser();
    if (!estimate.officerPhone && user && user.phoneNumber) {
      estimate.officerPhone = user.phoneNumber;
    }
    if (!estimate.nameOfWork && estimate.workName) {
      estimate.nameOfWork = estimate.workName;
    }
    if (estimate.gstPercent === undefined || estimate.gstPercent === null) {
      estimate.gstPercent = 0.0;
    } else {
      estimate.gstPercent = parseFloat(estimate.gstPercent);
    }
    if (estimate.unforeseenAmount === undefined || estimate.unforeseenAmount === null) {
      estimate.unforeseenAmount = 0.0;
    } else {
      estimate.unforeseenAmount = parseFloat(estimate.unforeseenAmount);
    }

    // Ensure grand total is recalculable
    HMWSSB.Calculations.recalculate(estimate);

    return this.post('/estimates', estimate);
  },

  deleteEstimate: async function (id) {
    return this.delete('/estimates/' + id);
  },

  performAction: async function (id, action, officerPhone, remarks, tags) {
    var act = (action === 'APPROVE') ? 'FORWARD' : action;
    return this.post('/estimates/' + id + '/action', {
      action: act,
      officerPhone: officerPhone,
      remarks: remarks || '',
      tags: tags || ''
    });
  },

  getRemarks: async function (id) {
    return this.get('/estimates/' + id + '/remarks');
  },

  addRemark: async function (id, officerPhone, remarks, tags) {
    return this.post('/estimates/' + id + '/remarks', {
      officerPhone: officerPhone,
      remarks: remarks,
      tags: tags || 'OBSERVATION'
    });
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

  // Items (SSR Master)
  searchItems: async function (query) {
    if (!query || query.trim().length < 2) return [];
    return this.get('/items/search?q=' + encodeURIComponent(query.trim()));
  },

  getItems: async function (category) {
    var path = '/items';
    if (category) path += '?category=' + encodeURIComponent(category);
    return this.get(path);
  },

  getItemCategories: async function () {
    return this.get('/items/categories');
  },

  saveItem: async function (item) {
    return this.post('/items', item);
  },

  deleteItem: async function (id) {
    return this.delete('/items/' + id);
  },

  // Jurisdictions
  getJurisdictions: async function () {
    return this.get('/jurisdictions');
  },

  getJurisdictionHierarchy: async function () {
    return this.get('/jurisdictions/hierarchy');
  },

  // Users
  getUsers: async function () {
    return this.get('/users');
  },

  saveUser: async function (user) {
    return this.post('/users', user);
  },

  deleteUser: async function (id) {
    return this.delete('/users/' + id);
  }
};

// ── Technical Sanction Workflow Engine ───────────────────────────────────────
HMWSSB.Workflow = {
  stages: [
    { key: 'DRAFT', label: 'Draft', actorRole: 'MANAGER', order: 0 },
    { key: 'SUBMITTED_TO_DGM', label: 'Under Scrutiny (DGM)', actorRole: 'DGM', order: 1 },
    { key: 'SUBMITTED_TO_GM', label: 'Under Scrutiny (GM)', actorRole: 'GM', order: 2 },
    { key: 'SUBMITTED_TO_CGM', label: 'Under Scrutiny (CGM)', actorRole: 'CGM', order: 3 },
    { key: 'SUBMITTED_TO_DOP', label: 'Pending Sanction (DOP)', actorRole: 'DOP', order: 4 },
    { key: 'APPROVED', label: 'Technically Sanctioned', actorRole: null, order: 5 }
  ],

  getStageOrder: function (status) {
    for (var i = 0; i < this.stages.length; i++) {
      if (this.stages[i].key === status) return i;
    }
    return 0;
  },

  getStageLabel: function (status) {
    for (var i = 0; i < this.stages.length; i++) {
      if (this.stages[i].key === status) return this.stages[i].label;
    }
    return status || 'Draft';
  },

  isPendingForUser: function (estimate, user) {
    if (!estimate || !user) return false;
    var status = estimate.status || 'DRAFT';
    var userRole = (user.role || '').toUpperCase();
    if (userRole === 'ADMIN') return true;

    switch (status) {
      case 'DRAFT':
        return userRole === 'MANAGER' && (estimate.createdByPhone === user.phoneNumber || !estimate.id);
      case 'SUBMITTED_TO_DGM':
        return userRole === 'DGM';
      case 'SUBMITTED_TO_GM':
        return userRole === 'GM';
      case 'SUBMITTED_TO_CGM':
        return userRole === 'CGM';
      case 'SUBMITTED_TO_DOP':
        return userRole === 'DOP';
      default:
        return false;
    }
  },

  isReturned: function (estimate) {
    if (!estimate) return false;
    if (estimate.status === 'DRAFT' && estimate.lastRemarks) {
      var rem = (estimate.lastRemarks || '').trim();
      return rem.length > 0;
    }
    return false;
  },

  canEdit: function (estimate, user) {
    if (!estimate || !user) return false;
    var status = estimate.status || 'DRAFT';
    var userRole = (user.role || '').toUpperCase();
    if (userRole === 'ADMIN') return true;
    return status === 'DRAFT' && userRole === 'MANAGER';
  },

  getAllowedActions: function (estimate, user) {
    if (!estimate || !user) return [];
    var status = estimate.status || 'DRAFT';
    var userRole = (user.role || '').toUpperCase();
    var actions = [];

    if (userRole === 'ADMIN') {
      if (status === 'DRAFT') {
        actions.push('FORWARD');
      } else if (status === 'APPROVED') {
        actions.push('RETURN');
      } else {
        actions.push('FORWARD');
        actions.push('RETURN');
        if (status === 'SUBMITTED_TO_DOP') actions.push('APPROVE');
      }
      return actions;
    }

    if (status === 'DRAFT' && userRole === 'MANAGER') {
      actions.push('FORWARD');
    } else if (status === 'SUBMITTED_TO_DGM' && userRole === 'DGM') {
      actions.push('FORWARD');
      actions.push('RETURN');
    } else if (status === 'SUBMITTED_TO_GM' && userRole === 'GM') {
      actions.push('FORWARD');
      actions.push('RETURN');
    } else if (status === 'SUBMITTED_TO_CGM' && userRole === 'CGM') {
      actions.push('FORWARD');
      actions.push('RETURN');
    } else if (status === 'SUBMITTED_TO_DOP' && userRole === 'DOP') {
      actions.push('APPROVE');
      actions.push('RETURN');
    }

    return actions;
  }
};

// ── Financial Calculation Engine (Exact Parity with V1 & Java Backend) ────────────
HMWSSB.Calculations = {
  computeRow: function (row) {
    var l = (row.length !== null && row.length !== undefined && row.length !== '') ? parseFloat(row.length) : null;
    var b = (row.breadth !== null && row.breadth !== undefined && row.breadth !== '') ? parseFloat(row.breadth) : null;
    var d = (row.depth !== null && row.depth !== undefined && row.depth !== '') ? parseFloat(row.depth) : null;
    var num = (row.num !== null && row.num !== undefined && row.num !== '') ? parseFloat(row.num) : 1;
    if (isNaN(num) || num <= 0) num = 1;

    var qty = num;
    var hasDim = false;
    if (l !== null && !isNaN(l) && l > 0) {
      qty *= l;
      hasDim = true;
    }
    if (b !== null && !isNaN(b) && b > 0) {
      qty *= b;
      hasDim = true;
    }
    if (d !== null && !isNaN(d) && d > 0) {
      qty *= d;
      hasDim = true;
    }
    if (!hasDim && row.quantity && row.quantity > 0 && (!row.num || row.num === 1)) {
      qty = row.quantity;
    }

    row.quantity = Math.round(qty * 1000) / 1000;
    var rate = parseFloat(row.rate) || 0;
    row.amount = Math.round(row.quantity * rate * 100) / 100;
    return row;
  },

  recalculate: function (estimate) {
    var costOfCivilWork = 0;
    var costOfMaterial = 0;

    if (estimate.items && Array.isArray(estimate.items)) {
      for (var i = 0; i < estimate.items.length; i++) {
        var it = estimate.items[i];
        var qty = parseFloat(it.quantity) || 0;
        var rate = parseFloat(it.rate) || 0;
        var amt = Math.round(qty * rate * 100) / 100;
        it.amount = amt;

        var isMat = (it.isMaterial === 'Yes' || it.isMaterial === true || (it.itemCategory && it.itemCategory.toUpperCase() === 'MATERIAL'));
        if (isMat) {
          costOfMaterial += amt;
        } else {
          costOfCivilWork += amt;
        }
      }
    }

    costOfCivilWork = Math.round(costOfCivilWork * 100) / 100;
    costOfMaterial = Math.round(costOfMaterial * 100) / 100;
    var partITotal = Math.round((costOfMaterial + costOfCivilWork) * 100) / 100;

    var gstPct = estimate.gstPercent !== undefined && estimate.gstPercent !== null ? parseFloat(estimate.gstPercent) : 0;
    var gstAmount = Math.round(partITotal * (gstPct / 100) * 100) / 100;

    var unforeseen = estimate.unforeseenAmount !== undefined && estimate.unforeseenAmount !== null ? parseFloat(estimate.unforeseenAmount) : 0;
    unforeseen = Math.round(unforeseen * 100) / 100;

    var grandTotal = Math.round((partITotal + gstAmount + unforeseen) * 100) / 100;

    estimate.costOfMaterial = costOfMaterial;
    estimate.costOfCivilWork = costOfCivilWork;
    estimate.materialTotal = costOfMaterial;
    estimate.civilTotal = costOfCivilWork;
    estimate.partITotal = partITotal;
    estimate.subtotal = partITotal;
    estimate.gstPercent = gstPct;
    estimate.gstAmount = gstAmount;
    estimate.unforeseenAmount = unforeseen;
    estimate.grandTotal = grandTotal;

    return {
      costOfMaterial: costOfMaterial,
      costOfCivilWork: costOfCivilWork,
      partITotal: partITotal,
      gstPercent: gstPct,
      gstAmount: gstAmount,
      unforeseenAmount: unforeseen,
      grandTotal: grandTotal
    };
  }
};

// ── Active Location Scope Service (Exact V1 Mirror) ─────────────────────────
HMWSSB.LocationScope = {
  getOptions: function (user) {
    if (!user) return [];
    var options = [];
    var primaryRole = (user.role || 'OFFICER').toUpperCase();
    var locs = user.locations || [];

    if (primaryRole === 'ADMIN') {
      options.push({
        key: 'admin_all',
        role: 'ADMIN',
        label: 'ADMIN — All Estimates (System Access)',
        corp: '', zone: '', division: '', circle: '', ward: '', filterType: 'all'
      });
      return options;
    }

    var rolesMap = {};
    rolesMap[primaryRole] = true;
    locs.forEach(function (l) {
      if (l.role) rolesMap[l.role.toUpperCase()] = true;
    });
    var userRoles = Object.keys(rolesMap);

    userRoles.forEach(function (r) {
      var roleLocs = locs.filter(function (l) {
        var lRole = (l.role || primaryRole).toUpperCase();
        return lRole === r;
      });

      if (r === 'DOP') {
        options.push({
          key: 'loc_dop_all',
          role: 'DOP',
          label: 'DOP — Corporate Level (All Zones & Sanctions)',
          corp: 'MMC', zone: '', division: '', circle: '', ward: '', filterType: 'all'
        });
      } else if (r === 'CGM') {
        if (roleLocs.length > 1) {
          options.push({
            key: 'loc_cgm_all',
            role: 'CGM',
            label: 'CGM — All Assigned Zones',
            corp: roleLocs[0].corp || 'MMC', zone: '', division: '', circle: '', ward: '', filterType: 'zone'
          });
        }
        roleLocs.forEach(function (loc, idx) {
          var zName = loc.zoneName || 'Assigned Zone';
          options.push({
            key: 'loc_cgm_' + idx,
            role: 'CGM',
            label: 'CGM — ' + zName + (loc.corp ? ' (' + loc.corp + ')' : ''),
            corp: loc.corp || 'MMC', zone: loc.zoneName || '', division: '', circle: '', ward: '', filterType: 'zone'
          });
        });
        if (roleLocs.length === 0) {
          options.push({
            key: 'loc_cgm_default',
            role: 'CGM',
            label: 'CGM — Default Jurisdiction',
            corp: 'MMC', zone: '', division: '', circle: '', ward: '', filterType: 'zone'
          });
        }
      } else if (r === 'GM') {
        if (roleLocs.length > 1) {
          var divNames = roleLocs.map(function (l) { return l.division ? 'Div ' + l.division : 'All Divs'; }).join(', ');
          options.push({
            key: 'loc_gm_all',
            role: 'GM',
            label: 'GM — All Assigned Divisions (' + divNames + ')',
            corp: roleLocs[0].corp || 'MMC', zone: roleLocs[0].zoneName || '', division: '', circle: '', ward: '', filterType: 'division'
          });
        }
        roleLocs.forEach(function (loc, idx) {
          var divStr = loc.division ? 'Division ' + loc.division : 'Assigned Division';
          var zStr = loc.zoneName ? ' (' + loc.zoneName + ')' : '';
          options.push({
            key: 'loc_gm_' + idx,
            role: 'GM',
            label: 'GM — ' + divStr + zStr,
            corp: loc.corp || 'MMC', zone: loc.zoneName || '', division: loc.division || '', circle: '', ward: '', filterType: 'division'
          });
        });
        if (roleLocs.length === 0) {
          options.push({
            key: 'loc_gm_default',
            role: 'GM',
            label: 'GM — Default Jurisdiction',
            corp: 'MMC', zone: '', division: '', circle: '', ward: '', filterType: 'division'
          });
        }
      } else if (r === 'DGM') {
        if (roleLocs.length > 1) {
          var cirNames = roleLocs.map(function (l) { return l.circleName || 'Circle'; }).join(', ');
          options.push({
            key: 'loc_dgm_all',
            role: 'DGM',
            label: 'DGM — All Assigned Circles (' + cirNames + ')',
            corp: roleLocs[0].corp || 'MMC', zone: roleLocs[0].zoneName || '', division: roleLocs[0].division || '', circle: '', ward: '', filterType: 'circle'
          });
        }
        roleLocs.forEach(function (loc, idx) {
          var cirStr = loc.circleName ? loc.circleName : 'Assigned Circle';
          var divStr = loc.division ? ' (Div ' + loc.division + ')' : '';
          options.push({
            key: 'loc_dgm_' + idx,
            role: 'DGM',
            label: 'DGM — ' + cirStr + divStr,
            corp: loc.corp || 'MMC', zone: loc.zoneName || '', division: loc.division || '', circle: loc.circleName || '', ward: '', filterType: 'circle'
          });
        });
        if (roleLocs.length === 0) {
          options.push({
            key: 'loc_dgm_default',
            role: 'DGM',
            label: 'DGM — Default Jurisdiction',
            corp: 'MMC', zone: '', division: '', circle: '', ward: '', filterType: 'circle'
          });
        }
      } else if (r === 'MANAGER') {
        if (roleLocs.length > 1) {
          var wNames = roleLocs.map(function (l) { return l.wardName ? l.wardName.split(' - ')[0] : 'Ward'; }).join(', ');
          options.push({
            key: 'loc_mgr_all',
            role: 'MANAGER',
            label: 'MANAGER — All Assigned Wards (Wards ' + wNames + ')',
            corp: roleLocs[0].corp || 'MMC', zone: roleLocs[0].zoneName || '', division: roleLocs[0].division || '', circle: roleLocs[0].circleName || '', ward: '', filterType: 'ward'
          });
        }
        roleLocs.forEach(function (loc, idx) {
          var wStr = loc.wardName || 'Assigned Ward';
          var cirStr = loc.circleName ? ' (' + loc.circleName + ')' : '';
          options.push({
            key: 'loc_mgr_' + idx,
            role: 'MANAGER',
            label: 'MANAGER — ' + wStr + cirStr,
            corp: loc.corp || 'MMC', zone: loc.zoneName || '', division: loc.division || '', circle: loc.circleName || '', ward: loc.wardName || '', filterType: 'ward'
          });
        });
        if (roleLocs.length === 0) {
          options.push({
            key: 'loc_mgr_default',
            role: 'MANAGER',
            label: 'MANAGER — Default Jurisdiction',
            corp: 'MMC', zone: '', division: '', circle: '', ward: '', filterType: 'ward'
          });
        }
      }
    });

    if (options.length === 0) {
      options.push({
        key: 'loc_primary',
        role: primaryRole,
        label: primaryRole + ' — Default Jurisdiction',
        corp: '', zone: '', division: '', circle: '', ward: '', filterType: 'all'
      });
    }

    return options;
  },

  getActiveOption: function (user) {
    var options = this.getOptions(user);
    if (!options.length) return null;
    var storedKey = localStorage.getItem(HMWSSB.CONFIG.ACTIVE_LOCATION_KEY);
    var found = options.find(function (o) { return o.key === storedKey; });
    return found || options[0];
  },

  setActiveOption: function (optKey, user) {
    var options = this.getOptions(user);
    var found = options.find(function (o) { return o.key === optKey; });
    if (!found && options.length) found = options[0];
    if (found) {
      localStorage.setItem(HMWSSB.CONFIG.ACTIVE_LOCATION_KEY, found.key);
      localStorage.setItem(HMWSSB.CONFIG.ACTIVE_ROLE_KEY, found.role || user.role);
      localStorage.setItem(HMWSSB.CONFIG.ACTIVE_LOCATION_DATA, JSON.stringify({
        corp: found.corp,
        zoneName: found.zone,
        division: found.division,
        circleName: found.circle,
        wardName: found.ward,
        role: found.role
      }));
    }
    return found;
  },

  getDefaultEstimateOption: function (user) {
    var active = this.getActiveOption(user);
    if (active && active.ward) return active;
    var options = this.getOptions(user);
    var specific = options.find(function (o) { return o.ward && o.ward.trim().length > 0; });
    return specific || active || (options.length ? options[0] : null);
  },

  isManagerAuthorizedForWard: function (user, wardName) {
    if (!user || !wardName) return false;
    var role = (user.role || '').toUpperCase();
    if (role === 'ADMIN' || role === 'DOP') return true;
    if (!user.locations || !user.locations.length) return false;
    var cleanTarget = String(wardName).replace(/^(?:Ward)[\s.:#-]+/i, '').trim().toLowerCase();
    return user.locations.some(function (l) {
      var lWard = (l.wardName || '').replace(/^(?:Ward)[\s.:#-]+/i, '').trim().toLowerCase();
      if (lWard === cleanTarget) return true;
      var numTarget = (cleanTarget.match(/\b\d+\b/) || [])[0];
      var numLoc = (lWard.match(/\b\d+\b/) || [])[0];
      return numTarget && numLoc && numTarget === numLoc;
    });
  }
};

// ── Formatters & Numerical Precision Helpers ─────────────────────────────────
HMWSSB.Formatting = {
  round: function (num, decimals) {
    decimals = decimals !== undefined ? decimals : 2;
    var factor = Math.pow(10, decimals);
    return Math.round((Number(num) || 0) * factor) / factor;
  },

  formatCurrency: function (num) {
    var val = Number(num) || 0;
    return '₹ ' + val.toLocaleString('en-IN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
  },

  formatNumber: function (num, decimals) {
    var val = Number(num) || 0;
    return val.toLocaleString('en-IN', {
      minimumFractionDigits: decimals !== undefined ? decimals : 2,
      maximumFractionDigits: decimals !== undefined ? decimals : 2
    });
  },

  formatDate: function (dateStr) {
    if (!dateStr) return '—';
    var d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) + ' ' +
      d.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', hour12: true });
  },

  formatDateOnly: function (dateStr) {
    if (!dateStr) return '—';
    var d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  },

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

  getStatusBadge: function (status, isReturned) {
    status = status || 'DRAFT';
    if (status === 'DRAFT' && isReturned) {
      return '<span class="badge badge-danger" style="background:#FEF2F2; color:#DC2626; border:1px solid #FCA5A5; font-weight:700; display:inline-flex; align-items:center; gap:5px;"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg> Returned for Correction</span>';
    }
    switch (status) {
      case 'DRAFT': return '<span class="badge badge-draft">Draft</span>';
      case 'SUBMITTED_TO_DGM': return '<span class="badge badge-pending">Pending DGM</span>';
      case 'SUBMITTED_TO_GM': return '<span class="badge badge-pending">Pending GM</span>';
      case 'SUBMITTED_TO_CGM': return '<span class="badge badge-pending">Pending CGM</span>';
      case 'SUBMITTED_TO_DOP': return '<span class="badge badge-pending">Pending DOP</span>';
      case 'APPROVED': return '<span class="badge badge-approved" style="display:inline-flex; align-items:center; gap:5px;"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg> Technically Sanctioned</span>';
      default: return '<span class="badge badge-draft">' + status + '</span>';
    }
  },

  getRoleBadge: function (role) {
    role = (role || 'OFFICER').toUpperCase();
    var cls = 'badge-secondary';
    switch (role) {
      case 'MANAGER': cls = 'badge-role-manager'; break;
      case 'DGM': cls = 'badge-role-dgm'; break;
      case 'GM': cls = 'badge-role-gm'; break;
      case 'CGM': cls = 'badge-role-cgm'; break;
      case 'DOP': cls = 'badge-role-dop'; break;
      case 'ADMIN': cls = 'badge-role-admin'; break;
    }
    return '<span class="badge ' + cls + '">' + role + '</span>';
  }
};

// ── Jurisdiction Hierarchy Service ──────────────────────────────────────────
HMWSSB.Jurisdiction = {
  buildTreeFromList: function (list) {
    var tree = {};
    if (!Array.isArray(list)) return tree;

    for (var i = 0; i < list.length; i++) {
      var item = list[i];
      var corp = (item.corp || item.corporationName || '').trim();
      var zone = (item.zoneName || '').trim();
      var div = (item.division || item.divisionName || '').trim();
      var circle = (item.circleName || '').trim();
      var ward = (item.wardName || '').trim();

      if (!corp) continue;
      if (!tree[corp]) tree[corp] = {};
      if (!zone) continue;
      if (!tree[corp][zone]) tree[corp][zone] = {};
      if (!div) continue;
      if (!tree[corp][zone][div]) tree[corp][zone][div] = {};
      if (!circle) continue;
      if (!tree[corp][zone][div][circle]) tree[corp][zone][div][circle] = [];
      if (ward && tree[corp][zone][div][circle].indexOf(ward) === -1) {
        tree[corp][zone][div][circle].push(ward);
      }
    }
    return tree;
  },

  getHierarchy: async function () {
    try {
      var h = await HMWSSB.API.getJurisdictionHierarchy();
      if (h && Object.keys(h).length > 0) return h;
    } catch (e) {
      console.warn('API getJurisdictionHierarchy failed, falling back to list or global hierarchy', e);
    }
    if (window.WARD_HIERARCHY && Object.keys(window.WARD_HIERARCHY).length > 0) {
      return window.WARD_HIERARCHY;
    }
    try {
      var list = await HMWSSB.API.getJurisdictions();
      return this.buildTreeFromList(list);
    } catch (e) {
      return {};
    }
  },

  populateSelect: function (selectEl, items, placeholder, selectedValue) {
    if (!selectEl) return;
    selectEl.innerHTML = '';
    if (placeholder) {
      var opt = document.createElement('option');
      opt.value = '';
      opt.textContent = placeholder;
      selectEl.appendChild(opt);
    }
    if (Array.isArray(items)) {
      for (var i = 0; i < items.length; i++) {
        var val = typeof items[i] === 'object' ? items[i].value : items[i];
        var text = typeof items[i] === 'object' ? items[i].text : items[i];
        var o = document.createElement('option');
        o.value = val;
        o.textContent = text;
        if (selectedValue && String(val) === String(selectedValue)) {
          o.selected = true;
        }
        selectEl.appendChild(o);
      }
    }
  }
};

// ── Local Storage Working Copy Helper ────────────────────────────────────────
HMWSSB.Storage = {
  saveDraft: function (estimate) {
    if (!estimate) return false;
    try {
      var user = HMWSSB.Auth.getUser();
      if (user && !estimate.officerPhone) {
        estimate.officerPhone = user.phoneNumber;
      }
      localStorage.setItem(HMWSSB.CONFIG.ESTIMATE_V2_KEY, JSON.stringify(estimate));
      localStorage.setItem(HMWSSB.CONFIG.ESTIMATE_KEY, JSON.stringify(estimate));
      return true;
    } catch (e) {
      console.warn('Could not save draft to local storage', e);
      return false;
    }
  },

  loadDraft: function () {
    try {
      var raw = localStorage.getItem(HMWSSB.CONFIG.ESTIMATE_V2_KEY) || localStorage.getItem(HMWSSB.CONFIG.ESTIMATE_KEY);
      if (!raw) return null;
      var draft = JSON.parse(raw);
      var user = HMWSSB.Auth.getUser();
      if (user && draft && !draft.id && draft.officerPhone) {
        if (String(draft.officerPhone) !== String(user.phoneNumber)) {
          this.clearDraft();
          return null;
        }
      }
      return draft;
    } catch (e) {
      return null;
    }
  },

  clearDraft: function () {
    localStorage.removeItem(HMWSSB.CONFIG.ESTIMATE_V2_KEY);
    localStorage.removeItem(HMWSSB.CONFIG.ESTIMATE_KEY);
  }
};

// ── Shared UI Shell & Interaction Components ─────────────────────────────────
HMWSSB.UI = {
  renderHeader: function (activePage) {
    var user = HMWSSB.Auth.getUser() || { name: 'Guest Officer', role: 'OFFICER', designation: 'Guest', phoneNumber: '0000000000' };
    var isAdmin = (user.role || '').toUpperCase() === 'ADMIN';

    var safeName = (user.name || 'Officer').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    var safeRole = (user.role || 'OFFICER').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    var safeDesig = (user.designation || user.role || 'Department Officer').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    var safePhone = (user.phoneNumber || '—').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    var initials = (user.name || 'OF').split(' ').filter(Boolean).map(function (w) { return w[0]; }).slice(0, 2).join('').toUpperCase() || 'OF';

    var safeLocation = 'HMWSSB Head Office';
    if (user.locations && user.locations.length > 0) {
      var loc = user.locations[0];
      var locParts = [];
      if (loc.corp) locParts.push(loc.corp);
      if (loc.zoneName) locParts.push(loc.zoneName);
      if (loc.division) locParts.push('Div ' + loc.division);
      if (loc.circleName) locParts.push(loc.circleName);
      if (locParts.length > 0) safeLocation = locParts.join(' • ');
    }

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
            <button onclick="document.body.style.fontSize='12px'" style="background:none;border:none;color:#fff;cursor:pointer;font-weight:700;" title="Small text">A-</button>
            <button onclick="document.body.style.fontSize='14px'" style="background:none;border:none;color:#fff;cursor:pointer;font-weight:700;" title="Default text">A</button>
            <button onclick="document.body.style.fontSize='16px'" style="background:none;border:none;color:#fff;cursor:pointer;font-weight:700;" title="Large text">A+</button>
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
          <a href="home-v2.html" class="banner-logo-link" title="HMWSSB Works Home">
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
          <li><a href="home-v2.html" class="${activePage === 'home' ? 'active' : ''}">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
            Home
          </a></li>
          <li><a href="dashboard-v2.html" class="${activePage === 'dashboard' ? 'active' : ''}">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>
            Dashboard
          </a></li>
          <li><a href="measurement-v2.html" class="${activePage === 'measurement' ? 'active' : ''}">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21.3 8.7l-5-5a1 1 0 0 0-1.4 0l-12 12a1 1 0 0 0 0 1.4l5 5a1 1 0 0 0 1.4 0l12-12a1 1 0 0 0 0-1.4z"/><line x1="14.5" y1="5.5" x2="18.5" y2="9.5"/><line x1="7.5" y1="12.5" x2="10.5" y2="15.5"/></svg>
            Estimate Sheet
          </a></li>
          <li><a href="abstract-v2.html" class="${activePage === 'abstract' ? 'active' : ''}">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
            General Abstract
          </a></li>
          <li><a href="ssr-v2.html" class="${activePage === 'ssr' ? 'active' : ''}">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
            Item Master List
          </a></li>
          <li><a href="approvals-v2.html" class="${activePage === 'approvals' ? 'active' : ''}">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/></svg>
            Scrutiny &amp; Sanctions
          </a></li>
          ${isAdmin ? `<li><a href="admin-v2.html" class="${activePage === 'admin' ? 'active' : ''}">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
            Administration
          </a></li>` : ''}
        </ul>
        <div class="nav-extra">
          <!-- Innovative Officer Profile & Actions Menu -->
          <div class="user-menu-container" id="user-menu-container">
            <button type="button" class="user-menu-btn" onclick="HMWSSB.UI.toggleUserMenu(event)" title="Officer Profile & Session">
              <span class="user-avatar-circle">${initials}</span>
              <span class="officer-name-text">${safeName}</span>
              <span class="user-menu-caret">▾</span>
            </button>
            <div class="user-menu-dropdown" id="user-menu-dropdown" style="display:none;">
              <div class="user-menu-hero">
                <div class="user-menu-avatar-large">${initials}</div>
                <div class="user-menu-hero-meta">
                  <div class="user-menu-name">${safeName}</div>
                  <div class="user-menu-desig">${safeDesig}</div>
                  <span class="user-role-badge role-${safeRole.toLowerCase()}">${safeRole}</span>
                </div>
              </div>
              <div class="user-menu-content">
                <div class="user-info-item">
                  <span class="info-icon">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg>
                  </span>
                  <div class="info-details">
                    <span class="info-label">Official Mobile</span>
                    <span class="info-value">${safePhone}</span>
                  </div>
                </div>
                <div class="user-info-item">
                  <span class="info-icon">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
                  </span>
                  <div class="info-details">
                    <span class="info-label">Jurisdiction Scope</span>
                    <span class="info-value">${safeLocation}</span>
                  </div>
                </div>
                <div class="user-info-item">
                  <span class="info-icon">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
                  </span>
                  <div class="info-details">
                    <span class="info-label">Session Status</span>
                    <span class="info-value"><span class="status-dot online"></span> Authenticated Officer</span>
                  </div>
                </div>
              </div>
              <div class="user-menu-actions">
                <button type="button" onclick="HMWSSB.Auth.logout()" class="user-menu-signout-btn">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
                  <span>Sign Out</span>
                </button>
              </div>
            </div>
          </div>
          <a href="index.html" class="nav-classic-btn" title="Switch to classic V1 portal" style="display:inline-flex; align-items:center; gap:5px;">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 14 4 9 9 4"/><path d="M20 20v-7a4 4 0 0 0-4-4H4"/></svg>
            Classic V1
          </a>
        </div>
      </nav>
    `;

    var container = document.getElementById('gov-app-header');
    if (container) {
      container.innerHTML = headerHtml;
      if (new URLSearchParams(window.location.search).get('openUserMenu')) {
        setTimeout(function() {
          var dd = document.getElementById('user-menu-dropdown');
          if (dd) dd.style.display = 'block';
        }, 300);
      }
    }
  },

  toggleUserMenu: function (e) {
    if (e) e.stopPropagation();
    var dd = document.getElementById('user-menu-dropdown');
    if (!dd) return;
    var isOpen = dd.style.display !== 'none';
    dd.style.display = isOpen ? 'none' : 'block';
  },

  renderBreadcrumbs: function (crumbs) {
    var container = document.getElementById('gov-breadcrumbs');
    if (!container || !Array.isArray(crumbs) || crumbs.length === 0) return;

    var html = '<nav class="gov-breadcrumb-nav" aria-label="Breadcrumb"><ol class="breadcrumb-list">';
    for (var i = 0; i < crumbs.length; i++) {
      var c = crumbs[i];
      var isLast = (i === crumbs.length - 1);
      if (isLast) {
        html += '<li class="breadcrumb-item current" aria-current="page">' + c.label + '</li>';
      } else {
        html += '<li class="breadcrumb-item"><a href="' + (c.url || '#') + '">' + c.label + '</a></li>';
      }
    }
    html += '</ol></nav>';
    container.innerHTML = html;
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
              <a href="home-v2.html" style="color:var(--ashok-gold-400); text-decoration:none;">V2 Home</a> •
              <a href="dashboard-v2.html" style="color:var(--slate-400); text-decoration:none;">Dashboard</a> •
              <a href="index.html" style="color:var(--slate-400); text-decoration:none;">Classic View</a> •
              <a href="admin-v2.html" style="color:var(--slate-400); text-decoration:none;">Officer Admin</a>
            </div>
          </div>
        </div>
      </footer>
    `;

    var container = document.getElementById('gov-app-footer');
    if (container) {
      container.innerHTML = footerHtml;
    }
  },

  showToast: function (message, type, duration) {
    type = type || 'info';
    duration = duration || 4000;
    var container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      container.className = 'toast-container';
      document.body.appendChild(container);
    }

    var toast = document.createElement('div');
    toast.className = 'toast toast-' + type;
    var iconSvg = type === 'success'
      ? '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>'
      : (type === 'error'
        ? '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>'
        : '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>');
    toast.innerHTML = '<span class="toast-icon" style="display:inline-flex; align-items:center;">' + iconSvg + '</span><div class="toast-msg">' + message + '</div>';
    container.appendChild(toast);

    setTimeout(function () {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      setTimeout(function () {
        if (toast.parentNode) toast.parentNode.removeChild(toast);
      }, 300);
    }, duration);
  },

  showModal: function (title, contentHtml, footerButtons) {
    this.closeModal();
    var overlay = document.createElement('div');
    overlay.id = 'gov-modal-overlay';
    overlay.className = 'modal-backdrop';

    var btnHtml = '';
    if (Array.isArray(footerButtons)) {
      for (var i = 0; i < footerButtons.length; i++) {
        var b = footerButtons[i];
        btnHtml += '<button type="button" class="btn ' + (b.className || 'btn-secondary') + '" id="' + (b.id || '') + '">' + b.text + '</button>';
      }
    } else {
      btnHtml = '<button type="button" class="btn btn-secondary" onclick="HMWSSB.UI.closeModal()">Close</button>';
    }

    overlay.innerHTML = `
      <div class="modal-box" role="dialog" aria-modal="true">
        <div class="modal-header">
          <h3 class="modal-title">${title}</h3>
          <button type="button" class="modal-close-btn" onclick="HMWSSB.UI.closeModal()" aria-label="Close modal">&times;</button>
        </div>
        <div class="modal-body">${contentHtml}</div>
        <div class="modal-footer">${btnHtml}</div>
      </div>
    `;

    overlay.addEventListener('click', function (e) {
      if (e.target === overlay) {
        HMWSSB.UI.closeModal();
      }
    });

    document.body.appendChild(overlay);

    if (Array.isArray(footerButtons)) {
      for (var j = 0; j < footerButtons.length; j++) {
        var fb = footerButtons[j];
        if (fb.id && typeof fb.onClick === 'function') {
          var el = document.getElementById(fb.id);
          if (el) el.addEventListener('click', fb.onClick);
        }
      }
    }
  },

  closeModal: function () {
    var existing = document.getElementById('gov-modal-overlay');
    if (existing && existing.parentNode) {
      existing.parentNode.removeChild(existing);
    }
  }
};

HMWSSB.Utils = HMWSSB.Formatting;

// Close user dropdown on outside click
if (typeof window !== 'undefined' && !window._userMenuListenerAttached) {
  window._userMenuListenerAttached = true;
  window.addEventListener('click', function (e) {
    var dd = document.getElementById('user-menu-dropdown');
    if (dd && dd.style.display !== 'none') {
      var container = document.getElementById('user-menu-container');
      if (container && !container.contains(e.target)) {
        dd.style.display = 'none';
      }
    }
  });
}
