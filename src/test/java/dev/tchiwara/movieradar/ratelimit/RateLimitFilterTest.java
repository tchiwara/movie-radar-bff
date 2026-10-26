package dev.tchiwara.movieradar.ratelimit;

import dev.tchiwara.movieradar.config.AppProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RateLimitFilterTest {
    @Test void sixtyFirstRequestIsRejected() throws Exception {
        AppProperties p = new AppProperties("", "", 60, 10, 0,
                new AppProperties.Appwrite("", "", "", "", ""));
        RateLimitFilter filter = new RateLimitFilter(p);
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 60; i++)
            filter.doFilter(new MockHttpServletRequest("GET", "/api/trending"),
                    new MockHttpServletResponse(), chain);
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/trending"), rejected, chain);
        assertEquals(429, rejected.getStatus());
        assertEquals("60", rejected.getHeader("Retry-After"));
    }

    @Test void spoofedForwardedForCannotCreateNewBucketsWhenProxyHopsDisabled() throws Exception {
        AppProperties p = new AppProperties("", "", 60, 10, 0,
                new AppProperties.Appwrite("", "", "", "", ""));
        RateLimitFilter filter = new RateLimitFilter(p);
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 60; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/trending");
            request.setRemoteAddr("192.0.2.1");
            request.addHeader("X-Forwarded-For", "10.0.0." + i);
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }
        MockHttpServletRequest spoofed = new MockHttpServletRequest("GET", "/api/trending");
        spoofed.setRemoteAddr("192.0.2.1");
        spoofed.addHeader("X-Forwarded-For", "10.0.0.99");
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(spoofed, rejected, chain);
        assertEquals(429, rejected.getStatus());
    }

    @Test void usesLastForwardedEntryWhenOneProxyIsTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/trending");
        request.setRemoteAddr("192.0.2.1");
        request.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.7");
        assertEquals("203.0.113.7", dev.tchiwara.movieradar.config.ClientIpResolver.resolve(request, 1));
    }

    @Test void invalidForwardedHeadersFallBackToRemoteAddress() {
        MockHttpServletRequest shortHeader = new MockHttpServletRequest("GET", "/api/trending");
        shortHeader.setRemoteAddr("192.0.2.1");
        shortHeader.addHeader("X-Forwarded-For", "198.51.100.1");
        assertEquals("192.0.2.1", dev.tchiwara.movieradar.config.ClientIpResolver.resolve(shortHeader, 2));

        MockHttpServletRequest blankHeader = new MockHttpServletRequest("GET", "/api/trending");
        blankHeader.setRemoteAddr("192.0.2.1");
        blankHeader.addHeader("X-Forwarded-For", "  ");
        assertEquals("192.0.2.1", dev.tchiwara.movieradar.config.ClientIpResolver.resolve(blankHeader, 1));
    }
}
