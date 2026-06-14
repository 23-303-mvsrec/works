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

    logout: function () {
      $window.localStorage.removeItem(APP_CONFIG.SESSION_KEY);
      $window.localStorage.removeItem(APP_CONFIG.ESTIMATE_KEY);
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

    isEditable: function (status, role) {
      status = status || 'DRAFT';
      role = (role || '').toUpperCase();
      if (status === 'DRAFT' && role === 'MANAGER') return true;
      if (status === 'SUBMITTED_TO_DGM' && role === 'DGM') return true;
      if (status === 'SUBMITTED_TO_GM' && role === 'GM') return true;
      if (status === 'SUBMITTED_TO_CGM' && role === 'CGM') return true;
      return false;
    },

    canForward: function (status, role) {
      status = status || 'DRAFT';
      role = (role || '').toUpperCase();
      if (status === 'DRAFT' && role === 'MANAGER') return true;
      if (status === 'SUBMITTED_TO_DGM' && role === 'DGM') return true;
      if (status === 'SUBMITTED_TO_GM' && role === 'GM') return true;
      if (status === 'SUBMITTED_TO_CGM' && role === 'CGM') return true;
      if (status === 'SUBMITTED_TO_DOP' && role === 'DOP') return true;
      return false;
    },

    canReturn: function (status, role) {
      status = status || 'DRAFT';
      role = (role || '').toUpperCase();
      if (status === 'SUBMITTED_TO_DGM' && role === 'DGM') return true;
      if (status === 'SUBMITTED_TO_GM' && role === 'GM') return true;
      if (status === 'SUBMITTED_TO_CGM' && role === 'CGM') return true;
      if (status === 'SUBMITTED_TO_DOP' && role === 'DOP') return true;
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
      modalScope._close = function () { close(); };

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
      modalScope._confirm = function () { close(); if (onConfirm) onConfirm(); };
      modalScope._cancel = function () { close(); };

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
    }
  };
}]);

/* ── Utility Functions ── */
hmwssbShared.factory('Utils', [function () {
  return {
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
