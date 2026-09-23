package dev.status;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC15: the §2.6 traceability matrix. Every FR1-FR11 / AC1-AC15 row must have
 * at least one named test reference, and each reference must resolve to a real
 * test method — otherwise the build fails.
 */
class TraceabilityMatrixTest {

    record Row(String requirement, List<String> references) {
    }

    static final List<Row> MATRIX = List.of(
            new Row("FR1", List.of(
                    "dev.status.ServiceRegistrationApiTest.given_validKeyAndEnv_when_postService_then_201AndRoundTrip",
                    "dev.status.ServiceRegistrationApiTest.given_existingService_when_rePostSameKeyAndEnv_then_409_noDuplicate",
                    "dev.status.ServiceRegistrationApiTest.given_existingKey_when_postDifferentService_then_409")),
            new Row("FR2", List.of(
                    "dev.status.ServiceRegistrationApiTest.given_devKey_when_postProdEnv_then_403Exact",
                    "dev.status.ServiceReadApiTest.given_devAndProdServices_when_listByEnv_then_onlyMatchingEnv")),
            new Row("FR3", List.of(
                    "dev.status.ClaimLoopSqlTest.given_dueRow_when_twoInstancesClaimConcurrently_then_exactlyOneWinner",
                    "dev.status.MultiInstanceRebalanceTest.given_ownerKilled_when_leaseExpires_then_survivorsReclaim_withoutDuplicates")),
            new Row("FR4", List.of(
                    "dev.status.application.BackoffCalculatorTest$Intervals.given_threeFailures_when_nextDelay_then_60s",
                    "dev.status.MonitoringFaultInjectionApiTest.given_serviceUp_when_flippedDown_then_transitionHistoryAndFailures")),
            new Row("FR5", List.of("dev.status.application.ProbeStatusMappingTest$Mapping.given_2xxWithOk_when_mapped_then_up")),
            new Row("FR6", List.of(
                    "dev.status.MonitoringFaultInjectionApiTest.given_serviceUp_when_flippedDown_then_transitionHistoryAndFailures",
                    "dev.status.ServiceHistoryApiTest.given_existingService_when_getHistory_then_200Empty")),
            new Row("FR7", List.of(
                    "dev.status.SseDeliveryApiTest.given_connectedClient_when_transition_then_eventReceived",
                    "dev.status.CrossInstanceNotifyTest.given_instanceACollects_when_instanceBDashboard_then_delivered")),
            new Row("FR8", List.of(
                    "dev.status.DashboardDataApiTest.given_services_when_getServices_then_matrixAndSummaryInOneCall",
                    "dev.status.DashboardReconcileTest.given_transition_when_refetch_then_reconciled")),
            new Row("FR9", List.of("dev.status.MockServiceFaultInjectionTest.given_mockService_when_faultToggledDown_then_cellRed_and_countersUpdate")),
            new Row("FR10", List.of(
                    "dev.status.MetricsObservabilityTest.given_checksRun_when_prometheus_then_metricsExposed",
                    "dev.status.StructuredLoggingTest.given_transition_when_logged_then_transitionsLogged_and_upStaysUpSilent")),
            new Row("FR11", List.of("dev.status.MultiInstanceRebalanceTest.given_ownerKilled_when_leaseExpires_then_survivorsReclaim_withoutDuplicates")),
            new Row("AC1", List.of("hub:python3 scripts/validate-registry.py")),
            new Row("AC2", List.of(
                    "dev.status.WalkingSkeletonApiTest.given_boot_when_health_then_200",
                    "dev.status.WalkingSkeletonApiTest.given_emptyDb_when_listServices_then_200EmptyList")),
            new Row("AC3", List.of(
                    "dev.status.WalkingSkeletonApiTest.given_missingKey_when_postService_then_401Exact",
                    "dev.status.WalkingSkeletonApiTest.given_unknownKey_when_postService_then_401Exact",
                    "dev.status.WalkingSkeletonApiTest.given_devKey_when_postProdEnv_then_403Exact")),
            new Row("AC4", List.of(
                    "dev.status.ServiceRegistrationApiTest.given_validKeyAndEnv_when_postService_then_201AndRoundTrip",
                    "dev.status.ServiceRegistrationApiTest.given_malformedBody_when_post_then_400WithErrors")),
            new Row("AC5", List.of("dev.status.ServiceReadApiTest.given_devAndProdServices_when_listByEnv_then_onlyMatchingEnv")),
            new Row("AC6", List.of("dev.status.MonitoringFaultInjectionApiTest.given_serviceUp_when_flippedDown_then_transitionHistoryAndFailures")),
            new Row("AC7", List.of("dev.status.MultiInstanceRebalanceTest.given_ownerKilled_when_leaseExpires_then_survivorsReclaim_withoutDuplicates")),
            new Row("AC8", List.of("dev.status.application.BackoffCalculatorTest$Intervals.given_manyFailures_when_nextDelay_then_cappedAt60s")),
            new Row("AC9", List.of("dev.status.SseDeliveryApiTest.given_connectedClient_when_transition_then_eventReceived")),
            new Row("AC10", List.of(
                    "dev.status.MockServiceFaultInjectionTest.given_mockService_when_faultToggledDown_then_cellRed_and_countersUpdate",
                    "dev.status.DashboardReconcileTest.given_transition_when_refetch_then_reconciled")),
            new Row("AC11", List.of("dev.status.MetricsObservabilityTest.given_checksRun_when_prometheus_then_metricsExposed")),
            new Row("AC12", List.of("dev.status.ComposeSmokeTest.given_composeFile_when_inspected_then_fullTopologyPresent")),
            new Row("AC13", List.of(
                    "dev.status.ContractGoldenTest.given_missingKey_when_post_then_401ExactEnvelope",
                    "dev.status.ContractGoldenTest.given_devKey_when_postProd_then_403ExactEnvelope")),
            new Row("AC14", List.of("dev.status.MultiInstanceRebalanceTest.given_ownerKilled_when_leaseExpires_then_survivorsReclaim_withoutDuplicates")),
            new Row("AC15", List.of("dev.status.TraceabilityMatrixTest.given_matrix_when_everyRowNamed_then_allRefsResolve"))
    );

    @Test
    void given_matrix_when_everyRowNamed_then_allRefsResolve() throws Exception {
        assertThat(MATRIX).hasSize(26); // FR1-11 + AC1-15
        for (Row row : MATRIX) {
            assertThat(row.references())
                    .as("row %s must have at least one named test reference", row.requirement())
                    .isNotEmpty();
            for (String ref : row.references()) {
                if (ref.startsWith("hub:")) {
                    continue; // orchestration gate (AC1)
                }
                assertMethodExists(ref);
            }
        }
    }

    private void assertMethodExists(String ref) throws Exception {
        int dot = ref.lastIndexOf('.');
        String className = ref.substring(0, dot);
        String methodName = ref.substring(dot + 1);
        Class<?> cls = Class.forName(className);
        boolean found = Arrays.stream(cls.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals(methodName));
        assertThat(found)
                .as("referenced test method must exist: %s", ref)
                .isTrue();
    }
}
