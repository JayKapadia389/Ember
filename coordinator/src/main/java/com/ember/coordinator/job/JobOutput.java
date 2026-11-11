package com.ember.coordinator.job;

import org.springframework.core.io.Resource;

record JobOutput(String contentType, Resource content) {}
