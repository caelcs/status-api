/* Service status dashboard — matrix + counters, live via SSE (native EventSource),
   plus a per-service drill-down panel showing transition history. */
(function () {
  'use strict';

  var grid = document.getElementById('grid');
  var envSelect = document.getElementById('env');
  var conn = document.getElementById('conn');
  var panelBackdrop = document.getElementById('panel-backdrop');
  var panelClose = document.getElementById('panel-close');
  var es = null;

  var openServiceId = null;
  var lastFocusedCard = null;

  function currentEnv() {
    return envSelect.value;
  }

  function setCounter(id, value) {
    document.getElementById(id).textContent = value;
  }

  function renderSummary(summary) {
    setCounter('c-total', summary.total);
    setCounter('c-up', summary.up);
    setCounter('c-degraded', summary.degraded);
    setCounter('c-down', summary.down);
    setCounter('c-unknown', summary.unknown);
  }

  function formatTs(value) {
    if (!value) { return '—'; }
    var d = new Date(value);
    if (isNaN(d.getTime())) { return String(value); }
    return d.toLocaleString();
  }

  function cardFor(service) {
    var card = document.createElement('div');
    card.className = 'card ' + service.status;
    card.id = 'svc-' + service.id;
    card.setAttribute('role', 'button');
    card.setAttribute('tabindex', '0');
    card.setAttribute('aria-haspopup', 'dialog');
    card.setAttribute('aria-label', 'View status history for ' + service.name);

    var h = document.createElement('h2');
    h.textContent = service.name;
    card.appendChild(h);

    var key = document.createElement('div');
    key.className = 'key';
    key.textContent = service.key;
    card.appendChild(key);

    var status = document.createElement('span');
    status.className = 'status ' + service.status;
    status.textContent = service.status;
    card.appendChild(status);

    var meta = document.createElement('div');
    meta.className = 'meta';
    meta.textContent = (service.team ? service.team + ' · ' : '') +
      (service.latencyMs != null ? service.latencyMs + ' ms' : 'never checked');
    card.appendChild(meta);

    var hint = document.createElement('div');
    hint.className = 'hint';
    hint.textContent = 'View history →';
    card.appendChild(hint);

    card.addEventListener('click', function () { openPanel(service); });
    card.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        openPanel(service);
      }
    });

    return card;
  }

  function render(items) {
    grid.innerHTML = '';
    items.forEach(function (service) {
      grid.appendChild(cardFor(service));
    });
    markOpenCard();
  }

  function markOpenCard() {
    grid.querySelectorAll('.card').forEach(function (card) {
      if (openServiceId && card.id === 'svc-' + openServiceId) {
        card.classList.add('open');
      } else {
        card.classList.remove('open');
      }
    });
  }

  function renderPanelStatus(service) {
    var el = document.getElementById('panel-status');
    el.textContent = '';

    var badge = document.createElement('span');
    badge.className = 'status ' + service.status;
    badge.textContent = service.status;
    el.appendChild(badge);

    var facts = document.createElement('dl');
    facts.className = 'panel-facts';
    [
      ['Latency', service.latencyMs != null ? service.latencyMs + ' ms' : 'never checked'],
      ['Last checked', formatTs(service.lastCheckedAt)],
      ['Consecutive failures', service.consecutiveFailures != null ? service.consecutiveFailures : 0],
      ['Status changed', formatTs(service.statusChangedAt)]
    ].forEach(function (pair) {
      var dt = document.createElement('dt');
      dt.textContent = pair[0];
      var dd = document.createElement('dd');
      dd.textContent = String(pair[1]);
      facts.appendChild(dt);
      facts.appendChild(dd);
    });
    el.appendChild(facts);
  }

  function renderHistory(items) {
    var el = document.getElementById('panel-history');
    el.textContent = '';

    if (!items.length) {
      var empty = document.createElement('div');
      empty.className = 'panel-note';
      empty.textContent = 'No transitions recorded yet — the service has stayed green/red since you started watching.';
      el.appendChild(empty);
      return;
    }

    var table = document.createElement('table');
    var thead = document.createElement('thead');
    var headRow = document.createElement('tr');
    ['From → To', 'At', 'Reason'].forEach(function (label) {
      var th = document.createElement('th');
      th.textContent = label;
      headRow.appendChild(th);
    });
    thead.appendChild(headRow);
    table.appendChild(thead);

    var tbody = document.createElement('tbody');
    items.forEach(function (item) {
      var tr = document.createElement('tr');

      var trans = document.createElement('td');
      trans.textContent = (item.from || '?') + ' → ' + (item.to || '?');

      var at = document.createElement('td');
      at.textContent = formatTs(item.at);

      var reason = document.createElement('td');
      reason.textContent = item.reason || '';

      tr.appendChild(trans);
      tr.appendChild(at);
      tr.appendChild(reason);
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    el.appendChild(table);
  }

  function fetchHistory(serviceId) {
    var el = document.getElementById('panel-history');
    el.textContent = '';
    var loading = document.createElement('div');
    loading.className = 'panel-note';
    loading.textContent = 'Loading history…';
    el.appendChild(loading);

    fetch('/api/v1/services/' + encodeURIComponent(serviceId) + '/history?limit=50')
      .then(function (r) {
        if (!r.ok) { throw new Error('HTTP ' + r.status); }
        return r.json();
      })
      .then(function (data) {
        if (openServiceId !== serviceId) { return; }
        renderHistory(data.items || []);
      })
      .catch(function () {
        if (openServiceId !== serviceId) { return; }
        el.textContent = '';
        var err = document.createElement('div');
        err.className = 'panel-error';
        err.textContent = 'Could not load history. Please try again.';
        el.appendChild(err);
      });
  }

  function openPanel(service) {
    openServiceId = service.id;
    lastFocusedCard = document.activeElement;

    document.getElementById('panel-title').textContent = service.name;
    document.getElementById('panel-meta').textContent = [service.key, service.env].join(' · ');
    renderPanelStatus(service);

    panelBackdrop.hidden = false;
    markOpenCard();
    panelClose.focus();
    fetchHistory(service.id);
  }

  function closePanel() {
    openServiceId = null;
    panelBackdrop.hidden = true;
    markOpenCard();
    if (lastFocusedCard && document.contains(lastFocusedCard)) {
      lastFocusedCard.focus();
    }
  }

  function updateOpenPanelFromEvent(event) {
    if (!openServiceId || event.service.id !== openServiceId) { return; }
    var badge = document.querySelector('#panel-status .status');
    if (badge) {
      badge.className = 'status ' + event.to;
      badge.textContent = event.to;
    }
    fetchHistory(openServiceId);
  }

  function fetchSnapshot() {
    fetch('/api/v1/services?env=' + encodeURIComponent(currentEnv()))
      .then(function (r) { return r.json(); })
      .then(function (data) {
        renderSummary(data.summary);
        render(data.items);
      })
      .catch(function () { conn.textContent = 'snapshot error'; });
  }

  function updateCounters(event) {
    // Reconcile counters cheaply by re-fetching the snapshot (idempotent).
    fetchSnapshot();
  }

  function applyEvent(event) {
    var el = document.getElementById('svc-' + event.service.id);
    if (el) {
      el.className = 'card ' + event.to;
      var badge = el.querySelector('.status');
      if (badge) { badge.className = 'status ' + event.to; badge.textContent = event.to; }
    }
    markOpenCard();
    updateOpenPanelFromEvent(event);
  }

  function connect() {
    if (es) { es.close(); }
    conn.textContent = 'connecting…';
    es = new EventSource('/api/v1/events?env=' + encodeURIComponent(currentEnv()));
    es.addEventListener('status.changed', function (msg) {
      try {
        var event = JSON.parse(msg.data);
        applyEvent(event);
        updateCounters(event);
      } catch (e) { /* ignore malformed frame */ }
    });
    es.onopen = function () { conn.textContent = 'live'; };
    es.onerror = function () {
      conn.textContent = 'reconnecting…';
      // EventSource auto-reconnects; reconcile on reconnect.
      fetchSnapshot();
    };
  }

  envSelect.addEventListener('change', function () {
    fetchSnapshot();
    connect();
  });

  panelClose.addEventListener('click', closePanel);

  panelBackdrop.addEventListener('click', function (e) {
    if (e.target === panelBackdrop) { closePanel(); }
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && !panelBackdrop.hidden) { closePanel(); }
  });

  fetchSnapshot();
  connect();
})();
