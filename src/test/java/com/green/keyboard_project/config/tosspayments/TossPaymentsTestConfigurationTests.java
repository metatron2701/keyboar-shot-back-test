package com.green.keyboard_project.config.tosspayments;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class TossPaymentsTestConfigurationTests {
    // 실제 키가 아닌 테스트 전용 문자열이며 외부 API로 전송하지 않습니다.
    private static final String FIXTURE = "test_gsk_fixture_only";
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(TossPaymentsTestConfiguration.class);

    @Test
    void disabledByDefaultDoesNotRequireSecret() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TossPaymentsTestCredentials.class);
        });
    }

    @Test
    void disabledIgnoresInvalidOrLiveKey() {
        runner.withPropertyValues("toss.payments.test.enabled=false", "TOSS_PAYMENTS_SECRET_KEY=live_gsk_fixture_only")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(TossPaymentsTestCredentials.class);
            });
    }

    @Test
    void enabledLoadsTestOrderSheetSecretWithoutNetworkCalls() {
        runner.withPropertyValues("toss.payments.test.enabled=true", "TOSS_PAYMENTS_SECRET_KEY=" + FIXTURE)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(TossPaymentsTestCredentials.class);
                var credentials = context.getBean(TossPaymentsTestCredentials.class);
                assertThat(credentials.secretKey()).isEqualTo(FIXTURE);
                assertThat(credentials.toString()).isEqualTo("TossPaymentsTestCredentials[secretKey=REDACTED]");
            });
    }

    @Test
    void enabledMissingKeyFailsClearly() {
        runner.withPropertyValues("toss.payments.test.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("TOSS_PAYMENTS_SECRET_KEY가 없습니다");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "test_sk_fixture_only", "test_gck_fixture_only", "test_ck_fixture_only",
        "live_gsk_fixture_only", "live_sk_fixture_only", "test_gsk_", "test_gsk_fixture only", "${UNRESOLVED_KEY}"})
    void enabledRejectsMissingWrongKindLiveAndMalformedKeys(String candidate) {
        runner.withPropertyValues("toss.payments.test.enabled=true", "TOSS_PAYMENTS_SECRET_KEY=" + candidate)
            .run(context -> {
                assertThat(context).hasFailed();
                var failure = context.getStartupFailure();
                assertThat(failure).hasRootCauseInstanceOf(IllegalStateException.class);
                if (!candidate.isBlank() && !candidate.startsWith("${") && !candidate.equals("test_gsk_")) {
                    assertThat(failure.getMessage()).doesNotContain(candidate);
                    assertThat(rootCause(failure).getMessage()).doesNotContain(candidate);
                }
            });
    }

    @Test
    void publicMethodsDoNotExposeSecretGetter() {
        assertThat(TossPaymentsTestCredentials.class.getMethods())
            .noneMatch(method -> method.getName().equals("getSecretKey") || method.getName().equals("secretKey"));
    }

    private static Throwable rootCause(Throwable failure) {
        while (failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }
}
