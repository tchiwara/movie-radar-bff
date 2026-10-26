package dev.tchiwara.movieradar.movies;

import dev.tchiwara.movieradar.trending.TrendingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import dev.tchiwara.movieradar.trending.CountResult;

class MovieServiceTest {
    @Test void cacheMissAndHitStillCount() {
        MovieCache cache = mock(MovieCache.class);
        TrendingRepository trending = mock(TrendingRepository.class);
        SearchNormalizer normalizer = new SearchNormalizer();
        MovieResponse response = new MovieResponse(
                java.util.List.of(new MovieDto(1, "Batman", "https://example.com/poster.jpg", 8, "en", "2022")), 1, 1);
        when(cache.search("batman", 1)).thenReturn(response);
        when(trending.increment("batman", 1, "https://example.com/poster.jpg")).thenReturn(CountResult.INCREMENTED);
        MovieService service = new MovieService(cache, trending, normalizer);
        assertSame(response, service.search(" Batman ", 1));
        assertSame(response, service.search("batman", 1));
        verify(trending, times(2)).increment("batman", 1, "https://example.com/poster.jpg");
        verify(cache, times(2)).search("batman", 1);
    }
    @Test void countingFailureDoesNotBreakSearch() {
        MovieCache cache = mock(MovieCache.class); TrendingRepository trending = mock(TrendingRepository.class);
        doThrow(new RuntimeException()).when(trending).increment("abc", 1, "https://example.com/poster.jpg");
        MovieResponse response = new MovieResponse(
                java.util.List.of(new MovieDto(1, "ABC", "https://example.com/poster.jpg", 8, "en", "2022")), 1, 1);
        when(cache.search("abc", 1)).thenReturn(response);
        assertSame(response, new MovieService(cache, trending, new SearchNormalizer()).search("abc", 1));
    }
}
