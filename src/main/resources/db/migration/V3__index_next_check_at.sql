-- Claim-and-advance hot path: the claim query scans services WHERE next_check_at <= now()
-- ORDER BY next_check_at on every tick, and overdueCount() uses the same predicate.
-- Index next_check_at so a growing fleet does not force a full table scan per tick.
CREATE INDEX idx_services_next_check_at ON services (next_check_at);

-- status_history changed_at is already covered by V1's composite
-- idx_status_history_service_time (service_id, changed_at DESC), which serves the
-- history read (WHERE service_id = ? ... ORDER BY changed_at DESC). No standalone
-- changed_at index is added — it would be redundant with the existing composite.
