package dev.tchiwara.movieradar.movies;

import dev.tchiwara.movieradar.trending.CountResult;
import dev.tchiwara.movieradar.trending.TrendingRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MovieServiceDedupeTest {
    private final MovieCache cache = mock(MovieCache.class);
    private final TrendingRepository trending = mock(TrendingRepository.class);
    private final MovieService service =
            new MovieService(cache, trending, new SearchNormalizer(), new CountDeduplicator(10));

    private void stubCache(String title) {
        MovieDto movie = new MovieDto(1561726, title, "https://image.tmdb.org/t/p/w500/x.jpg",
                7.0, "en", "2026");
        when(cache.search(anyString(), anyInt())).thenReturn(new MovieResponse(List.of(movie), 1, 1));
    }

    @Test void repeatedSearchesForTheSameMovieFromOneClientCountOnce() {
        stubCache("Masterplan");
        service.search("masterp", 1, "1.1.1.1");
        service.search("masterpla", 1, "1.1.1.1");
        service.search("masterplan", 1, "1.1.1.1");
        verify(trending, times(1)).increment(anyString(), eq(1561726), anyString());
    }

    @Test void differentClientsAreCountedSeparately() {
        stubCache("Masterplan");
        service.search("masterplan", 1, "1.1.1.1");
        service.search("masterplan", 1, "2.2.2.2");
        verify(trending, times(2)).increment(anyString(), eq(1561726), anyString());
    }

    @Test void unknownClientIsAlwaysCounted() {
        stubCache("Masterplan");
        service.search("masterplan", 1);
        service.search("masterplan", 1);
        verify(trending, times(2)).increment(anyString(), eq(1561726), anyString());
    }

    @Test void failedCountingIsRetriedNextTime() {
        stubCache("Masterplan");
        when(trending.increment(anyString(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(CountResult.INCREMENTED);
        service.search("masterplan", 1, "1.1.1.1");
        service.search("masterplan", 1, "1.1.1.1");
        verify(trending, times(2)).increment(anyString(), eq(1561726), anyString());
    }

    @Test void storesTheFullMovieTitleNotTheTypedTerm() {
        stubCache("Masterplan");
        service.search("masterp", 1, "1.1.1.1");
        verify(trending).increment(eq("Masterplan"), eq(1561726), anyString());
    }

    @Test void fallsBackToTheQueryWhenTheTitleIsBlank() {
        stubCache("  ");
        service.search("masterp", 1, "1.1.1.1");
        verify(trending).increment(eq("masterp"), eq(1561726), anyString());
    }
}