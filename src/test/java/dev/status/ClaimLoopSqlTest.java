package dev.status;

import dev.status.domain.ClaimedService;
import dev.status.port.ClaimRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the claim-and-advance transaction (FOR UPDATE SKIP LOCKED) yields
 * disjoint claim sets under concurrency, advances next_check_at at claim time,
 * and never re-claims a row before its write-back (FR3 / ADR §4.11). Runs the
 * real claim SQL against Postgres.
 */
class ClaimLoopSqlTest extends BaseApiTest {

    @Autowired
    ClaimRepository claimRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void given_dueRows_when_twoClaimersClaimConcurrently_then_disjointRowSets() throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            jdbc.update("""
                    INSERT INTO services (id, key, name, env, health_url, next_check_at)
                    VALUES (?, ?, ?, 'dev', 'http://x/health', now())
                    """, id, unique("svc"), "Service " + i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<List<ClaimedService>> claimA = () -> {
            start.await();
            return claimRepository.claimDue(Duration.ofSeconds(30), 5);
        };
        Callable<List<ClaimedService>> claimB = () -> {
            start.await();
            return claimRepository.claimDue(Duration.ofSeconds(30), 5);
        };
        Future<List<ClaimedService>> fa = executor.submit(claimA);
        Future<List<ClaimedService>> fb = executor.submit(claimB);
        start.countDown();
        List<UUID> ra = idsOf(fa.get());
        List<UUID> rb = idsOf(fb.get());
        executor.shutdown();

        assertThat(Collections.disjoint(ra, rb))
                .as("the two claimers must receive disjoint row sets")
                .isTrue();
        Set<UUID> union = new HashSet<>();
        union.addAll(ra);
        union.addAll(rb);
        assertThat(union)
                .as("every due row must be claimed exactly once")
                .containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void given_dueRow_when_claimed_then_nextCheckAtAdvanced() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO services (id, key, name, env, health_url, next_check_at)
                VALUES (?, ?, 'Payments', 'dev', 'http://x/health', now())
                """, id, unique("payments"));

        List<ClaimedService> claimed = claimRepository.claimDue(Duration.ofSeconds(30), 10);
        assertThat(claimed).extracting(ClaimedService::serviceId).containsExactly(id);

        Instant nextCheckAt = jdbc.queryForObject(
                "SELECT next_check_at FROM services WHERE id = ?", Timestamp.class, id).toInstant();
        assertThat(nextCheckAt)
                .as("next_check_at must be advanced at claim time")
                .isAfter(Instant.now());
    }

    @Test
    void given_claimedRow_when_secondClaimBeforeWriteBack_then_empty() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO services (id, key, name, env, health_url, next_check_at)
                VALUES (?, ?, 'Payments', 'dev', 'http://x/health', now())
                """, id, unique("payments"));

        List<ClaimedService> first = claimRepository.claimDue(Duration.ofSeconds(30), 10);
        assertThat(first).hasSize(1);

        // schedule already advanced at claim time -> not re-claimable before write-back
        List<ClaimedService> second = claimRepository.claimDue(Duration.ofSeconds(30), 10);
        assertThat(second).isEmpty();
    }

    private static List<UUID> idsOf(List<ClaimedService> claimed) {
        return claimed.stream().map(ClaimedService::serviceId).collect(Collectors.toList());
    }
}
