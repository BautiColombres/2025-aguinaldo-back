package com.medibook.api.service;

import com.medibook.api.entity.TurnFile;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface TurnFileService {

    Mono<String> uploadTurnFile(UUID turnId, MultipartFile file);

    Mono<Void> deleteTurnFile(UUID turnId);

    Optional<TurnFile> getTurnFileInfo(UUID turnId);

    /**
     * Batch variant of {@link #getTurnFileInfo(UUID)} — returns the files for the
     * given turn ids keyed by turnId, resolved in a single query.
     */
    Map<UUID, TurnFile> getTurnFileInfoBatch(Collection<UUID> turnIds);

    boolean fileExistsForTurn(UUID turnId);
}
