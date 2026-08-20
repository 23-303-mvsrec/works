/* ═══════════════════════════════════════════════════════════════════════════
   HMWSSB Works System - Shared AngularJS Services
   ═══════════════════════════════════════════════════════════════════════════ */

var hmwssbShared = angular.module('hmwssbShared', []);

/* ── AuthService ── */
hmwssbShared.factory('AuthService', ['$window', function ($window) {
  return {
    getUser: function () {
      var raw = $window.localStorage.getItem(APP_CONFIG.SESSION_KEY);
      if (!raw) return null;
      try {
        var user = JSON.parse(raw);
        if (user._loginTimestamp) {
          var hours = (Date.now() - user._loginTimestamp) / (1000 * 60 * 60);
          if (hours > APP_CONFIG.SESSION_TIMEOUT_HOURS) {
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
      user._loginTimestamp = Date.now();
      $window.localStorage.setItem(APP_CONFIG.SESSION_KEY, JSON.stringify(user));
    },

    isLoggedIn: function () {
      return this.getUser() !== null;
    },

    clearSession: function () {
      $window.localStorage.removeItem(APP_CONFIG.SESSION_KEY);
      $window.localStorage.removeItem(APP_CONFIG.ESTIMATE_KEY);
      $window.localStorage.removeItem('current_active_role');
      $window.localStorage.removeItem('current_active_location');
      $window.localStorage.removeItem('current_active_location_key');
    },

    logout: function () {
      this.clearSession();
      $window.location.href = 'login.html';
    },

    requireLogin: function () {
      if (!this.isLoggedIn()) {
        $window.location.href = 'login.html';
        return false;
      }
      return true;
    }
  };
}]);

/* ── StatusService ── */
hmwssbShared.factory('StatusService', [function () {
  var STATUS_ORDER = ['DRAFT', 'SUBMITTED_TO_DGM', 'SUBMITTED_TO_GM', 'SUBMITTED_TO_CGM', 'SUBMITTED_TO_DOP', 'APPROVED'];

  return {
    getStatusOrder: function () { return STATUS_ORDER; },

    getLabel: function (status) {
      if (!status) return 'Draft';
      switch (status) {
        case 'DRAFT': return 'Draft';
        case 'SUBMITTED_TO_DGM': return 'Pending DGM';
        case 'SUBMITTED_TO_GM': return 'Pending GM';
        case 'SUBMITTED_TO_CGM': return 'Pending CGM';
        case 'SUBMITTED_TO_DOP': return 'Pending DOP';
        case 'APPROVED': return 'Approved';
        default: return status;
      }
    },

    getBadgeClass: function (status) {
      if (!status || status === 'DRAFT') return 'badge-draft';
      if (status === 'APPROVED') return 'badge-approved';
      return 'badge-pending';
    },

    hasRole: function (userOrRole, targetRole) {
      if (!userOrRole) return false;
      targetRole = (targetRole || '').toUpperCase();
      if (typeof userOrRole === 'string') {
        var r = userOrRole.toUpperCase();
        if (r === targetRole || r === 'ADMIN' || r === 'DOP') return true;
        try {
          var u = localStorage.getItem('currentUser');
          if (u) {
            var parsed = JSON.parse(u);
            if ((parsed.role || '').toUpperCase() === targetRole || (parsed.role || '').toUpperCase() === 'ADMIN' || (parsed.role || '').toUpperCase() === 'DOP') return true;
            if (parsed.locations && Array.isArray(parsed.locations)) {
              for (var j = 0; j < parsed.locations.length; j++) {
                var lr = (parsed.locations[j].role || parsed.role || '').toUpperCase();
                if (lr === targetRole) return true;
              }
            }
          }
        } catch (e) {}
        return false;
      }
      var primaryRole = (userOrRole.role || '').toUpperCase();
      if (primaryRole === targetRole || primaryRole === 'ADMIN' || primaryRole === 'DOP') return true;
      if (userOrRole.locations && Array.isArray(userOrRole.locations)) {
        for (var i = 0; i < userOrRole.locations.length; i++) {
          var locRole = (userOrRole.locations[i].role || primaryRole).toUpperCase();
          if (locRole === targetRole) return true;
        }
      }
      return false;
    },

    isEditable: function (status, userOrRole) {
      status = status || 'DRAFT';
      if (status === 'DRAFT' && this.hasRole(userOrRole, 'MANAGER')) return true;
      if (status === 'SUBMITTED_TO_DGM' && this.hasRole(userOrRole, 'DGM')) return true;
      if (status === 'SUBMITTED_TO_GM' && this.hasRole(userOrRole, 'GM')) return true;
      if (status === 'SUBMITTED_TO_CGM' && this.hasRole(userOrRole, 'CGM')) return true;
      return false;
    },

    canForward: function (status, userOrRole) {
      status = status || 'DRAFT';
      if (status === 'DRAFT' && this.hasRole(userOrRole, 'MANAGER')) return true;
      if (status === 'SUBMITTED_TO_DGM' && this.hasRole(userOrRole, 'DGM')) return true;
      if (status === 'SUBMITTED_TO_GM' && this.hasRole(userOrRole, 'GM')) return true;
      if (status === 'SUBMITTED_TO_CGM' && this.hasRole(userOrRole, 'CGM')) return true;
      if (status === 'SUBMITTED_TO_DOP' && this.hasRole(userOrRole, 'DOP')) return true;
      return false;
    },

    canReturn: function (status, userOrRole) {
      status = status || 'DRAFT';
      if (status === 'SUBMITTED_TO_DGM' && this.hasRole(userOrRole, 'DGM')) return true;
      if (status === 'SUBMITTED_TO_GM' && this.hasRole(userOrRole, 'GM')) return true;
      if (status === 'SUBMITTED_TO_CGM' && this.hasRole(userOrRole, 'CGM')) return true;
      if (status === 'SUBMITTED_TO_DOP' && this.hasRole(userOrRole, 'DOP')) return true;
      return false;
    },

    getForwardLabel: function (status) {
      switch (status) {
        case 'DRAFT': return 'Forward to DGM';
        case 'SUBMITTED_TO_DGM': return 'Forward to GM';
        case 'SUBMITTED_TO_GM': return 'Forward to CGM';
        case 'SUBMITTED_TO_CGM': return 'Forward to DOP';
        case 'SUBMITTED_TO_DOP': return 'Approve & Sanction';
        default: return 'Forward';
      }
    },

    getReturnLabel: function (status) {
      switch (status) {
        case 'SUBMITTED_TO_DGM': return 'Manager (AE)';
        case 'SUBMITTED_TO_GM': return 'DGM';
        case 'SUBMITTED_TO_CGM': return 'GM';
        case 'SUBMITTED_TO_DOP': return 'CGM';
        default: return 'Previous Officer';
      }
    },

    getStepIndex: function (status) {
      return STATUS_ORDER.indexOf(status || 'DRAFT');
    }
  };
}]);

/* ── ModalService ── */
hmwssbShared.factory('ModalService', ['$rootScope', '$compile', '$timeout', function ($rootScope, $compile, $timeout) {
  var modalScope = null;

  function close() {
    if (modalScope) {
      modalScope.$destroy();
      modalScope = null;
    }
    var el = document.getElementById('hmwssb-modal');
    if (el) el.remove();
  }

  return {
    alert: function (title, message, type) {
      type = type || 'info';
      close();
      modalScope = $rootScope.$new();
      modalScope._title = title;
      modalScope._message = message;
      modalScope._type = type;
      modalScope._close = function () { $timeout(function() { close(); }); };

      var html = '<div class="modal-overlay" ng-click="_close()">' +
        '<div class="modal-content" ng-click="$event.stopPropagation()">' +
        '<div class="modal-header"><span>{{_title}}</span></div>' +
        '<div class="modal-body"><p>{{_message}}</p></div>' +
        '<div class="modal-footer"><button class="btn btn-primary" ng-click="_close()">OK</button></div>' +
        '</div></div>';

      var el = document.createElement('div');
      el.id = 'hmwssb-modal';
      el.innerHTML = html;
      document.body.appendChild(el);
      $compile(el)(modalScope);
    },

    confirm: function (title, message, onConfirm, type) {
      type = type || 'warning';
      close();
      modalScope = $rootScope.$new();
      modalScope._title = title;
      modalScope._message = message;
      modalScope._type = type;
      modalScope._confirm = function () {
        $timeout(function() {
          close();
          if (onConfirm) onConfirm();
        });
      };
      modalScope._cancel = function () { $timeout(function() { close(); }); };

      var html = '<div class="modal-overlay" ng-click="_cancel()">' +
        '<div class="modal-content" ng-click="$event.stopPropagation()">' +
        '<div class="modal-header" style="background:#e67e22;"><span>{{_title}}</span></div>' +
        '<div class="modal-body"><p>{{_message}}</p></div>' +
        '<div class="modal-footer">' +
        '<button class="btn btn-secondary" ng-click="_cancel()">Cancel</button>' +
        '<button class="btn btn-primary" ng-click="_confirm()">Confirm</button>' +
        '</div></div></div>';

      var el = document.createElement('div');
      el.id = 'hmwssb-modal';
      el.innerHTML = html;
      document.body.appendChild(el);
      $compile(el)(modalScope);
    },

    prompt: function (title, message, placeholder, onConfirm, onCancel, defaultValue) {
      close();
      modalScope = $rootScope.$new();
      modalScope._title = title;
      modalScope._message = message;
      modalScope._placeholder = placeholder || 'Enter remarks...';
      modalScope._input = defaultValue || '';
      modalScope._confirm = function () {
        var val = modalScope._input;
        $timeout(function() {
          close();
          if (onConfirm) onConfirm(val);
        });
      };
      modalScope._cancel = function () {
        $timeout(function() {
          close();
          if (onCancel) onCancel();
        });
      };

      var html = '<div class="modal-overlay" ng-click="_cancel()">' +
        '<div class="modal-content" style="max-width:480px;" ng-click="$event.stopPropagation()">' +
        '<div class="modal-header" style="background:linear-gradient(135deg, #7a7a38, #5a7a3a); color:#fff; padding:12px 18px;">' +
        '<span style="font-weight:700; font-size:15px;">{{_title}}</span>' +
        '</div>' +
        '<div class="modal-body" style="padding:16px 20px; background:#fffde7;">' +
        '<p style="margin-bottom:10px; color:#2c3e50; font-weight:600; font-size:13px;">{{_message}}</p>' +
        '<textarea ng-model="_input" placeholder="{{_placeholder}}" rows="4" style="width:100%; border:1px solid #d4c97a; border-radius:4px; padding:8px 10px; font-family:inherit; font-size:13px; outline:none; box-sizing:border-box; background:#fff;"></textarea>' +
        '</div>' +
        '<div class="modal-footer" style="padding:10px 18px; background:#f5f5e8; border-top:1px solid #d4c97a; display:flex; justify-content:flex-end; gap:8px;">' +
        '<button class="btn" style="background:#7f8c8d; color:#fff; border:none; border-radius:3px; padding:6px 14px; font-weight:600; cursor:pointer; font-size:13px;" ng-click="_cancel()">Cancel</button>' +
        '<button class="btn" style="background:#27ae60; color:#fff; border:none; border-radius:3px; padding:6px 14px; font-weight:600; cursor:pointer; font-size:13px;" ng-click="_confirm()">Submit</button>' +
        '</div></div></div>';

      var el = document.createElement('div');
      el.id = 'hmwssb-modal';
      el.innerHTML = html;
      document.body.appendChild(el);
      $compile(el)(modalScope);
    }
  };
}]);

/* ── Utility Functions ── */
hmwssbShared.factory('Utils', [function () {
  return {
    isLocalFileProtocol: function () {
      return window.location.protocol === 'file:';
    },

    cleanString: function (s) {
      if (!s) return '';
      return String(s)
        .replace(/\u2010|\u2011|\u2012|\u2013|\u2014|\u2015|\u2212|\u00E2\u20AC\u201C|\u00E2\u20AC\u201D/g, '-')
        .replace(/\u00A0|\uFEFF/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
    },

    safeEquals: function (s1, s2) {
      var n1 = this.cleanString(s1).toLowerCase();
      var n2 = this.cleanString(s2).toLowerCase();
      if (!n1 && !n2) return true;
      if (!n1 || !n2) return false;
      if (n1 === n2) return true;
      var p1 = (n1.match(/^(\d+)/) || [])[1];
      var p2 = (n2.match(/^(\d+)/) || [])[1];
      if (p1 && p2 && p1 === p2) return true;
      return false;
    },

    isMaterialYes: function (val) {
      if (val === undefined || val === null) return false;
      var s = String(val).trim().toLowerCase();
      return s === 'yes' || s === 'true' || s === '1' || s === 'y';
    },

    formatDate: function (dateStr) {
      if (!dateStr) return '';
      var d = new Date(dateStr);
      return d.toLocaleDateString('en-IN') + ' ' + d.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
    },

    numberToWords: function (num) {
      if (num === 0) return 'Rupees Zero Only';

      var ones = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten',
        'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'];
      var tens = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

      function helper(n) {
        if (n < 20) return ones[n];
        if (n < 100) return tens[Math.floor(n / 10)] + (n % 10 !== 0 ? ' ' + ones[n % 10] : '');
        if (n < 1000) return ones[Math.floor(n / 100)] + ' Hundred' + (n % 100 !== 0 ? ' ' + helper(n % 100) : '');
        if (n < 100000) return helper(Math.floor(n / 1000)) + ' Thousand' + (n % 1000 !== 0 ? ' ' + helper(n % 1000) : '');
        if (n < 10000000) return helper(Math.floor(n / 100000)) + ' Lakh' + (n % 100000 !== 0 ? ' ' + helper(n % 100000) : '');
        return helper(Math.floor(n / 10000000)) + ' Crore' + (n % 10000000 !== 0 ? ' ' + helper(n % 10000000) : '');
      }

      var roundedNum = Math.round(num * 100) / 100;
      var parts = String(roundedNum).split('.');
      var rupees = parseInt(parts[0], 10);
      var paise = parts[1] ? parseInt(parts[1], 10) : 0;

      var result = 'Rupees ' + helper(rupees);
      if (paise > 0) {
        result += ' and ' + helper(paise) + ' Paise';
      }
      result += ' Only';
      return result;
    }
  };
}]);

/* ── Global session helpers (usable by boot-guards before Angular loads) ── */
window.__hmwssbClearSession = function () {
  try {
    localStorage.removeItem(APP_CONFIG.SESSION_KEY);
    localStorage.removeItem(APP_CONFIG.ESTIMATE_KEY);
    localStorage.removeItem('current_active_role');
    localStorage.removeItem('current_active_location');
    localStorage.removeItem('current_active_location_key');
  } catch (e) {}
};

window.__hmwssbMarkBooted = function () {
  try { document.body.setAttribute('data-hmwssb-booted', '1'); } catch (e) {}
};
