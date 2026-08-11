package com.storyplatform.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyplatform.shared.api.RateLimitingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The budgets here are what stand between a normal reader and a blank page: one
 * home render fans out 5 parallel calls, so a ceiling tuned for single requests
 * rejects ordinary browsing.
 */
class RateLimitingFilterTest {

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter();
    }

    private HttpServletRequest request(String method, String path, String ip) {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getMethod()).thenReturn(method);
        Mockito.when(request.getRequestURI()).thenReturn(path);
        Mockito.when(request.getRemoteAddr()).thenReturn(ip);
        return request;
    }

    private HttpServletResponse response(StringWriter body) throws Exception {
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        Mockito.when(response.getWriter()).thenReturn(new PrintWriter(body));
        return response;
    }

    /** Drives the filter directly, returning true when the request passed through. */
    private boolean send(String method, String path, String ip) throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        StringWriter body = new StringWriter();
        ReflectionTestUtils.invokeMethod(
                filter, "doFilterInternal", request(method, path, ip), response(body), chain);
        return Mockito.mockingDetails(chain).getInvocations().size() > 0;
    }

    @Test
    void allowsAFullHomePageRenderManyTimesOver() throws Exception {
        // 5 parallel calls per render; six quick renders must not be throttled.
        for (int call = 0; call < 30; call++) {
            assertThat(send("GET", "/home", "10.0.0.1"))
                    .as("request %d of a normal browsing burst", call + 1)
                    .isTrue();
        }
    }

    @Test
    void stillStopsARunawayBurst() throws Exception {
        int allowed = 0;
        for (int call = 0; call < 200; call++) {
            if (send("GET", "/home", "10.0.0.2")) {
                allowed++;
            }
        }
        assertThat(allowed)
                .as("a flood must be capped well below the number of attempts")
                .isLessThan(200);
    }

    @Test
    void keepsAuthAttemptsTightEvenThoughReadsAreGenerous() throws Exception {
        int allowed = 0;
        for (int call = 0; call < 40; call++) {
            if (send("POST", "/auth/login", "10.0.0.3")) {
                allowed++;
            }
        }
        // Login stays on the strict budget; raising read limits must not loosen it.
        assertThat(allowed).isLessThanOrEqualTo(15);
    }

    @Test
    void oneClientCannotSpendAnotherClientsBudget() throws Exception {
        for (int call = 0; call < 200; call++) {
            send("GET", "/home", "10.0.0.4");
        }
        assertThat(send("GET", "/home", "10.0.0.5"))
                .as("a separate IP must be unaffected by the flooder")
                .isTrue();
    }

    @Test
    void aBlockedRequestDoesNotConsumeTheOtherBudgets() throws Exception {
        // Exhaust the auth budget, then confirm reads still work: previously the
        // rejected auth calls had already incremented the general counter.
        for (int call = 0; call < 40; call++) {
            send("POST", "/auth/login", "10.0.0.6");
        }
        assertThat(send("GET", "/home", "10.0.0.6")).isTrue();
    }
}
