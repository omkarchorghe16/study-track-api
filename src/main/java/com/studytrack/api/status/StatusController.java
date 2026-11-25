package com.studytrack.api.status;

import com.studytrack.api.status.StatusDtos.StatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/status")
@Tag(name = "Status", description = "Service identity and database connectivity")
public class StatusController {
    private final StatusService statusService;

    public StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping
    @Operation(summary = "Get service status", description = "Returns the service name, API version and the user requests act as.")
    public StatusResponse getStatus() {
        return statusService.getStatus();
    }
}
