package com.ember.coordinator.job;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Fixed v1 catalog (decision 036). Each model has a params schema at
 * {@code resources/schemas/params/<id>.json}.
 */
@Getter
public enum ModelCatalog {
    QWEN3_1_7B("qwen3-1.7b", JobType.TEXT, 5120),
    STABLE_DIFFUSION_1_5("stable-diffusion-1.5", JobType.IMAGE, 5120);

    private final String id;
    private final JobType type;
    private final int requiredVramMb;

    ModelCatalog(String id, JobType type, int requiredVramMb) {
        this.id = id;
        this.type = type;
        this.requiredVramMb = requiredVramMb;
    }

    public static Optional<ModelCatalog> fromId(String id) {
        return Arrays.stream(values()).filter(model -> model.id.equals(id)).findFirst();
    }

    static List<String> idsFittingIn(int vramMb) {
        return Arrays.stream(values())
                .filter(model -> model.requiredVramMb <= vramMb)
                .map(ModelCatalog::getId)
                .toList();
    }
}
