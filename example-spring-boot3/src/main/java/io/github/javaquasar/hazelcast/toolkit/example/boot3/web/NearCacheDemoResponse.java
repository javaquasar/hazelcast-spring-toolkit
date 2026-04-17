package io.github.javaquasar.hazelcast.toolkit.example.boot3.web;

public record NearCacheDemoResponse(
        Long entityId,
        String entityTitle,
        TimingsMs timingsMs,
        HibernateL2Deltas hibernateL2Deltas,
        Interpretation interpretation
) {

    public record TimingsMs(
            long coldRead,
            long warmRead,
            long postEvictionRead
    ) {
    }

    public record HibernateL2Deltas(
            long hitsOnWarmRead,
            long missesAfterEviction
    ) {
    }

    public record Interpretation(
            boolean warmReadFasterThanColdRead,
            boolean evictionForcedMiss
    ) {
    }
}
