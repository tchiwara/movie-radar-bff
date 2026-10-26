package dev.tchiwara.movieradar.movies;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SearchNormalizerTest {
    @Test void trimsAndLowercases() { assertEquals("star wars", new SearchNormalizer().normalize(" Star Wars ")); }
    @Test void minimumCountableLengthIsThree() {
        SearchNormalizer n = new SearchNormalizer();
        assertFalse(n.isCountable("ab")); assertTrue(n.isCountable("abc"));
    }
}
