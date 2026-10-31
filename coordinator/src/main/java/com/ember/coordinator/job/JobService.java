package com.ember.coordinator.job;

import com.ember.coordinator.common.EmberException;
import com.ember.coordinator.job.dto.JobCreateRequest;
import com.ember.coordinator.job.dto.JobResponse;
import com.ember.coordinator.job.dto.ResultOutcome;
import com.ember.coordinator.job.exception.InvalidResultException;
import com.ember.coordinator.job.exception.JobNotFoundException;
import com.ember.coordinator.job.exception.JobNotOwnedException;
import com.ember.coordinator.job.exception.OutputNotAvailableException;
import com.ember.coordinator.job.exception.UnknownModelException;
import com.ember.coordinator.worker.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private static final int MAX_CONTENT_TYPE_LENGTH = 64;
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final JobRepository jobRepository;
    private final ArtifactRepository artifactRepository;
    private final ArtifactStore artifactStore;
    private final ParamsValidator paramsValidator;
    private final WorkerService workerService;

    // Client side

    @Transactional
    public UUID create(JobCreateRequest request) {
        ModelCatalog model = ModelCatalog.fromId(request.modelId())
                .orElseThrow(() -> new UnknownModelException(request.modelId()));
        paramsValidator.validate(model, request.params());

        Job job = new Job();
        job.setType(model.getType());
        job.setModelId(model.getId());
        job.setStatus(JobStatus.QUEUED);
        job.setParams(request.params());
        return jobRepository.save(job).getId();
    }

    @Transactional(readOnly = true)
    public JobResponse get(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        String outputUrl = job.getStatus() == JobStatus.COMPLETED ? "/jobs/" + jobId + "/output" : null;
        return new JobResponse(job.getId(), job.getModelId(), job.getType(), job.getStatus(), job.getParams(),
                job.getCreatedAt(), job.getFinishedAt(), outputUrl, job.getErrorMessage());
    }

    @Transactional(readOnly = true)
    JobOutput getOutput(UUID jobId) {
        if (!jobRepository.existsById(jobId)) {
            throw new JobNotFoundException(jobId);
        }
        Artifact artifact = artifactRepository.findByJobIdAndRole(jobId, ArtifactRole.OUTPUT)
                .orElseThrow(() -> new OutputNotAvailableException(jobId));
        return new JobOutput(artifact.getContentType(), artifactStore.load(artifact.getUri()));
    }

    // Worker side, over HTTP

    @Transactional
    public void submitResult(UUID jobId, UUID workerId, ResultOutcome outcome, String error, MultipartFile output) {
        workerService.recordSeen(workerId);
        boolean hasOutput = output != null && !output.isEmpty();
        boolean hasError = error != null && !error.isBlank();

        switch (outcome) {
            case COMPLETED -> {
                if (!hasOutput) throw new InvalidResultException("COMPLETED needs a non-empty 'output' file");
                if (hasError) throw new InvalidResultException("COMPLETED can't carry an 'error'");
                String contentType = output.getContentType() != null ? output.getContentType() : DEFAULT_CONTENT_TYPE;
                if (contentType.length() > MAX_CONTENT_TYPE_LENGTH) {
                    throw new InvalidResultException("Content type longer than " + MAX_CONTENT_TYPE_LENGTH + " characters");
                }
                // Update first: it locks the row and rejects a second upload before any bytes are written.
                if (jobRepository.complete(jobId, workerId) == 0) throw notOwned(jobId, workerId);
                saveOutput(jobId, contentType, output);
            }
            case FAILED -> {
                if (!hasError) throw new InvalidResultException("FAILED needs an 'error' message");
                if (hasOutput) throw new InvalidResultException("FAILED can't carry an 'output' file");
                if (jobRepository.fail(jobId, workerId, error) == 0) throw notOwned(jobId, workerId);
            }
        }
    }

    @Transactional
    public void release(UUID jobId, UUID workerId) {
        workerService.recordSeen(workerId);
        if (jobRepository.release(jobId, workerId) == 0) {
            throw notOwned(jobId, workerId);
        }
    }

    // Worker side, called by scheduling

    @Transactional
    public Optional<ClaimedJob> claimNext(UUID workerId, int vramMb) {
        List<String> modelIds = ModelCatalog.idsFittingIn(vramMb);
        if (modelIds.isEmpty()) {
            return Optional.empty();
        }
        return jobRepository.claimNext(workerId, modelIds)
                .map(job -> new ClaimedJob(job.getId(), job.getModelId(), job.getParams()));
    }

    /**
     * True if the worker may keep running the job. Hands the job back if it was
     * requeued but not yet taken (decision 032).
     */
    @Transactional
    public boolean confirmOwnership(UUID jobId, UUID workerId) {
        return jobRepository.confirmOwnership(jobId, workerId) == 1;
    }

    /**
     * Requeues jobs assigned to the worker other than the one it reports running
     * (decision 033). Jobs assigned within {@code grace} are left alone.
     *
     * @param runningJobId the job the worker reports, or null if none
     */
    @Transactional
    public int requeueOrphans(UUID workerId, UUID runningJobId, Duration grace) {
        return runningJobId == null
                ? jobRepository.requeueOrphans(workerId, grace.toSeconds())
                : jobRepository.requeueOrphansExcept(workerId, runningJobId, grace.toSeconds());
    }

    @Transactional(readOnly = true)
    public Set<UUID> assignedWorkerIds() {
        return Set.copyOf(jobRepository.findAssignedWorkerIds());
    }

    @Transactional
    public int requeueJobsOf(Collection<UUID> workerIds) {
        if (workerIds.isEmpty()) {
            return 0;
        }
        return jobRepository.requeueAssignedTo(workerIds);
    }

    private void saveOutput(UUID jobId, String contentType, MultipartFile output) {
        Artifact artifact = new Artifact();
        artifact.setJobId(jobId);
        artifact.setRole(ArtifactRole.OUTPUT);
        artifact.setContentType(contentType);
        artifact.setUri(artifactStore.save(jobId, output));
        artifactRepository.save(artifact);
    }

    private EmberException notOwned(UUID jobId, UUID workerId) {
        return jobRepository.existsById(jobId)
                ? new JobNotOwnedException(jobId, workerId)
                : new JobNotFoundException(jobId);
    }
}
