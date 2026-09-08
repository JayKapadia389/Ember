package com.ember.coordinator.services;

import com.ember.coordinator.dtos.StatusDto;
import com.ember.coordinator.dtos.TaskDTO;
import com.ember.coordinator.entities.Task;
import com.ember.coordinator.enums.Model;
import com.ember.coordinator.enums.TaskStatus;
import com.ember.coordinator.exceptions.NoSuchTaskException;
import com.ember.coordinator.repositories.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DelegatorService {

    private final TaskRepository taskRepository;

    private Model stringToModel(String model) {
        switch (model) {
            case "stable diffusion 1.5":
                return Model.STABLE_DIFFUSION_1_5;
            case "gpt2":
                return Model.GPT2;
//            default:
//                throw new UnsupportedModelException(model);
        }

        return null;
    }

    private Task toEntity(TaskDTO taskDTO) {
        Task task = new Task();
        System.out.println("model name >>" + taskDTO.model());
        task.setModel(stringToModel(taskDTO.model()));
        task.setInput(taskDTO.input());
        return task;
    }

    public UUID createNewTask(TaskDTO taskDTO) {
        Task task = toEntity(taskDTO);
        taskRepository.save(task);
        return task.getId();
    }

    public StatusDto getTaskStatus(UUID taskId) {
        Optional<Task> task = taskRepository.findById(taskId);

        if(task.isEmpty()){
            throw new NoSuchTaskException(taskId);
        }

        TaskStatus status = task.get().getStatus();

        return new StatusDto(status.toString());
    }
}
