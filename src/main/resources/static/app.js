/* Service status dashboard — matrix + counters, live via SSE (native EventSource). */
(function () {
  'use strict';

  var grid = document.getElementById('grid');
  var envSelect = document.getElementById('env');
  var conn = document.getElementById('conn');
  var es = null;

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

  function cardFor(service) {
    var card = document.createElement('div');
    card.className = 'card ' + service.status;
    card.id = 'svc-' + service.id;

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

    return card;
  }

  function render(items) {
    grid.innerHTML = '';
    items.forEach(function (service) {
      grid.appendChild(cardFor(service));
    });
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
    if (!el) { return; }
    el.className = 'card ' + event.to;
    var badge = el.querySelector('.status');
    if (badge) { badge.className = 'status ' + event.to; badge.textContent = event.to; }
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

  fetchSnapshot();
  connect();
})();
