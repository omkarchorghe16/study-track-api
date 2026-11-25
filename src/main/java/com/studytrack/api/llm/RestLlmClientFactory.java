package com.studytrack.api.llm;

import com.studytrack.api.llm.LlmDtos.LlmConnectionSettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Creates provider clients on Spring's {@code RestClient} with bounded timeouts (10 s connect,
 * {@code app.llm.timeout} read). The API key is attached as a default header and never logged.
 */
@Component
public class RestLlmClientFactory implements LlmClientFactory {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient.Builder restClientBuilder;
    private final LlmProperties llmProperties;

    public RestLlmClientFactory(RestClient.Builder restClientBuilder, LlmProperties llmProperties) {
        this.restClientBuilder = restClientBuilder;
        this.llmProperties = llmProperties;
    }

    @Override
    public LlmClient createClient(LlmConnectionSettings settings) {
        LlmProvider provider = settings.provider();
        String apiKey = llmProperties.apiKey(provider);
        if (provider.isApiKeyRequired() && apiKey == null) {
            throw new LlmException(LlmException.Reason.CONFIGURATION,
                    apiKeyVariable(provider) + " is not set on the server");
        }
        RestClient.Builder builder = restClientBuilder.clone()
                .baseUrl(settings.baseUrl())
                .requestFactory(createRequestFactory());
        return switch (provider) {
            case ANTHROPIC -> new AnthropicLlmClient(builder.defaultHeader("x-api-key", apiKey).build(),
                    settings.model(), llmProperties.anthropic().effort(), llmProperties.maxOutputTokens());
            case OPENAI -> new OpenAiLlmClient(
                    builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey).build(),
                    settings.model(), llmProperties.maxOutputTokens());
            case OLLAMA -> new OllamaLlmClient(builder.build(), settings.model(),
                    llmProperties.ollama().contextTokens());
        };
    }

    public static String apiKeyVariable(LlmProvider provider) {
        return switch (provider) {
            case ANTHROPIC -> "ANTHROPIC_API_KEY";
            case OPENAI -> "OPENAI_API_KEY";
            case OLLAMA -> "(none)";
        };
    }

    /** Buffered so requests carry Content-Length; some servers and proxies reject chunked JSON bodies. */
    private ClientHttpRequestFactory createRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .proxy(ProxySelector.getDefault())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(llmProperties.timeout());
        return new BufferingClientHttpRequestFactory(requestFactory);
    }
}
