
package dev.tchiwara.movieradar.movies;

import dev.tchiwara.movieradar.trending.CountResult;
import dev.tchiwara.movieradar.trending.TrendingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MovieServiceTest {

    @Test
    void cacheMissAndHitStillCount() {
        MovieCache cache = mock(MovieCache.class);
        TrendingRepository trending = mock(TrendingRepository.class);
        SearchNormalizer normalizer = new SearchNormalizer();

        MovieResponse response = new MovieResponse(
                List.of(new MovieDto(
                        1,
                        "Batman",
                        "https://example.com/poster.jpg",
                        8,
                        "en",
                        "2022"
                )),
                1,
                1
        );

        when(cache.search("batman", 1)).thenReturn(response);

        when(trending.increment(
                "Batman",
                1,
                "https://example.com/poster.jpg"
        )).thenReturn(CountResult.INCREMENTED);

        MovieService service = new MovieService(cache, trending, normalizer);

        assertSame(response, service.search(" Batman ", 1));
        assertSame(response, service.search("batman", 1));

        // Trending stores the full movie title, not the user's partial query.
        verify(trending, times(2)).increment(
                "Batman",
                1,
                "https://example.com/poster.jpg"
        );

        // Both searches still use the normalized query and cache.
        verify(cache, times(2)).search("batman", 1);
        verifyNoMoreInteractions(trending);
    }

    @Test
    void countingFailureDoesNotBreakSearch() {
        MovieCache cache = mock(MovieCache.class);
        TrendingRepository trending = mock(TrendingRepository.class);

        MovieResponse response = new MovieResponse(
                List.of(new MovieDto(
                        1,
                        "ABC",
                        "https://example.com/poster.jpg",
                        8,
                        "en",
                        "2022"
                )),
                1,
                1
        );

        when(cache.search("abc", 1)).thenReturn(response);

        // The service passes the movie title, "ABC", to the repository.
        doThrow(new RuntimeException("Appwrite unavailable"))
                .when(trending)
                .increment("ABC", 1, "https://example.com/poster.jpg");

        MovieService service = new MovieService(
                cache,
                trending,
                new SearchNormalizer()
        );

        assertSame(response, service.search("abc", 1));

        // Verify that counting was attempted and failed without breaking search.
        verify(trending).increment(
                "ABC",
                1,
                "https://example.com/poster.jpg"
        );

        verify(cache).search("abc", 1);
    }
}
