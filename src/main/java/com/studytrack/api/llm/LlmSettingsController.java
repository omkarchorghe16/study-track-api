package com.studytrack.api.llm;

import com.studytrack.api.llm.LlmDtos.LlmConnectionTestResponse;
import com.studytrack.api.llm.LlmDtos.LlmSettingsRequest;
import com.studytrack.api.llm.LlmDtos.LlmSettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/llm")
@Tag(name = "LLM settings", description = "Provider and model used for document imports")
public class LlmSettingsController {
    private final LlmSettingsService llmSettingsService;

    public LlmSettingsController(LlmSettingsService llmSettingsService) {
        this.llmSettingsService = llmSettingsService;
    }

    @GetMapping
    @Operation(summary = "Get LLM settings",
            description = "Saved settings or the server defaults, plus each provider's defaults and whether its API key is configured. Keys are never returned.")
    public LlmSettingsResponse getSettings() {
        return llmSettingsService.getSettings();
    }

    @PutMapping
    @Operation(summary = "Replace LLM settings", description = "API keys are not accepted; set ANTHROPIC_API_KEY / OPENAI_API_KEY on the server.")
    public LlmSettingsResponse replaceSettings(@Valid @RequestBody LlmSettingsRequest request) {
        return llmSettingsService.replaceSettings(request);
    }

    @PostMapping("/test")
    @Operation(summary = "Test the LLM connection",
            description = "Tests the settings in the body (unsaved form values), or the saved settings without a body. Returns ok=false with a reason instead of an error status.")
    public LlmConnectionTestResponse testConnection(@Valid @RequestBody(required = false) LlmSettingsRequest request) {
        return llmSettingsService.testConnection(request);
    }
}
