package com.studytrack.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "StudyTrack API",
                version = "v1",
                description = """
                        REST API for StudyTrack: subjects, topics, study items, progress and LLM-assisted imports.
                        Authentication is not implemented yet and every request acts as the configured user;
                        do not expose this development API publicly.
                        """
        )
)
public class OpenApiConfiguration {
}
