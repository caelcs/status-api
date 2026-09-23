package dev.status;

import dev.status.domain.ClaimedService;
import dev.status.port.ClaimRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the atomic conditional claim UPDATE yields exactly one winner under
 * concurrency (FR3 / ADR §4.4). Runs the real claim SQL against Postgres.
 */
class ClaimLoopSqlTest extends BaseApiTest {

    @Autowired
    ClaimRepository claimRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void given_dueRow_when_twoInstancesClaimConcurrently_then_exactlyOneWinner() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO services (id, key, name, env, health_url, next_check_at)
                VALUES (?, ?, 'Payments', 'dev', 'http://x/health', now())
                """, id, unique("payments"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<List<ClaimedService>> claimA = () -> {
            start.await();
            return claimRepository.claimDue("instance-A", 30_000L, 10);
        };
        Callable<List<ClaimedService>> claimB = () -> {
            start.await();
            return claimRepository.claimDue("instance-B", 30_000L, 10);
        };
        Future<List<ClaimedService>> fa = executor.submit(claimA);
        Future<List<ClaimedService>> fb = executor.submit(claimB);
        start.countDown();
        List<ClaimedService> ra = fa.get();
        List<ClaimedService> rb = fb.get();
        executor.shutdown();

        boolean aWon = ra.stream().anyMatch(c -> c.serviceId().equals(id));
        boolean bWon = rb.stream().anyMatch(c -> c.serviceId().equals(id));
        int winners = (aWon ? 1 : 0) + (bWon ? 1 : 0);

        assertThat(winners).as("exactly one instance must win the claim").isEqualTo(1);
    }

    @Test
    void given_claimedRow_when_leaseUnexpired_then_notReclaimed() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO services (id, key, name, env, health_url, next_check_at)
                VALUES (?, ?, 'Payments', 'dev', 'http://x/health', now())
                """, id, unique("payments"));

        List<ClaimedService> first = claimRepository.claimDue("instance-A", 30_000L, 10);
        assertThat(first).hasSize(1);

        // unexpired lease -> a second claimer gets nothing
        List<ClaimedService> second = claimRepository.claimDue("instance-B", 30_000L, 10);
        assertThat(second).isEmpty();
    }
}
