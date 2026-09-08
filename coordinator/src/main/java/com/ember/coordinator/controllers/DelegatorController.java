package com.ember.coordinator.controllers;

import com.ember.coordinator.dtos.StatusDto;
import com.ember.coordinator.dtos.TaskDTO;
import com.ember.coordinator.services.DelegatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/delegate")
public class DelegatorController {

    private final DelegatorService delegatorService;

    @PostMapping("/new")
    public UUID createNewTask(@RequestBody TaskDTO taskDTO) {
        return delegatorService.createNewTask(taskDTO);
    }

    @GetMapping("/status/{uuid}")
    public StatusDto getTaskStatus(@PathVariable UUID uuid) {
        return delegatorService.getTaskStatus(uuid);
    }
}
