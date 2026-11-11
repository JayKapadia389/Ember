package com.ember.coordinator.job;

import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;

import java.util.UUID;

/**
 * Where artifact bytes live (decision 017). Local disk in v1, object storage later.
 */
interface ArtifactStore {

    /**
     * @return the URI to record in the artifacts table
     */
    String save(UUID jobId, InputStreamSource content);

    Resource load(String uri);
}
