-- Claim-and-advance (ADR §4.11): drop the ownership-claim and
-- instance-heartbeat tables. Probing coordination is claim-and-advance on
-- services.next_check_at (FOR UPDATE SKIP LOCKED): there is no ownership row
-- and no instance-membership heartbeat.
DROP TABLE IF EXISTS service_claim;
DROP TABLE IF EXISTS instance;
