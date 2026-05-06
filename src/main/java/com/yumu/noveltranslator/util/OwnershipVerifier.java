package com.yumu.noveltranslator.util;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Function;

/**
 * Verifies entity ownership by userId.
 */
@Service
public class OwnershipVerifier {

    /**
     * Verify that an entity belongs to the given user.
     * Returns the entity if verified, empty otherwise.
     *
     * @param entityId    the entity ID to look up
     * @param userId      the expected owner user ID
     * @param loader      function to load the entity by ID
     * @param userIdExtractor function to extract userId from the entity
     * @return Optional containing the entity if owned by user, empty otherwise
     */
    public <T> Optional<T> verifyAndGet(Long entityId, Long userId,
                                          Function<Long, T> loader,
                                          Function<T, Long> userIdExtractor) {
        T entity = loader.apply(entityId);
        if (entity == null) {
            return Optional.empty();
        }
        Long entityUserId = userIdExtractor.apply(entity);
        if (!userId.equals(entityUserId)) {
            return Optional.empty();
        }
        return Optional.of(entity);
    }
}
