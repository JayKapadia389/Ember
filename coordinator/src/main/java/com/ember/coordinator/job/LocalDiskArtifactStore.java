package com.ember.coordinator.job;

import com.ember.coordinator.job.exception.ArtifactStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
class LocalDiskArtifactStore implements ArtifactStore {

    private final Path root;

    LocalDiskArtifactStore(@Value("${ember.artifacts.dir}") Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String save(UUID jobId, InputStreamSource content) {
        Path file = root.resolve(jobId.toString()).resolve("output");
        try (InputStream in = content.getInputStream()) {
            Files.createDirectories(file.getParent());
            // A rolled-back earlier attempt may have left a file behind.
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ArtifactStorageException("Could not store output of job " + jobId, e);
        }
        return file.toUri().toString();
    }

    @Override
    public Resource load(String uri) {
        Resource resource = new FileSystemResource(Path.of(URI.create(uri)));
        if (!resource.exists()) {
            throw new ArtifactStorageException("Artifact file missing: " + uri, null);
        }
        return resource;
    }
}
