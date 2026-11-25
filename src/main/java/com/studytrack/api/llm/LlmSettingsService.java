package com.studytrack.api.llm;

import com.studytrack.api.llm.LlmDtos.LlmConnectionCheck;
import com.studytrack.api.llm.LlmDtos.LlmConnectionSettings;
import com.studytrack.api.llm.LlmDtos.LlmConnectionTestResponse;
import com.studytrack.api.llm.LlmDtos.LlmProviderResponse;
import com.studytrack.api.llm.LlmDtos.LlmSettingsRequest;
import com.studytrack.api.llm.LlmDtos.LlmSettingsResponse;
import com.studytrack.api.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The LLM each user imports with: their saved row in {@code llm_settings}, or the {@code app.llm}
 * defaults. Switching provider or model needs no code change, only new settings.
 */
@Service
public class LlmSettingsService {
    private final LlmSettingsRepository llmSettingsRepository;
    private final LlmProperties llmProperties;
    private final LlmClientFactory llmClientFactory;
    private final CurrentUser currentUser;

    public LlmSettingsService(LlmSettingsRepository llmSettingsRepository, LlmProperties llmProperties,
                              LlmClientFactory llmClientFactory, CurrentUser currentUser) {
        this.llmSettingsRepository = llmSettingsRepository;
        this.llmProperties = llmProperties;
        this.llmClientFactory = llmClientFactory;
        this.currentUser = currentUser;
    }

    /** What a user's import will use; {@code enabled} false means imports are switched off. */
    public record ImportLlmSettings(LlmConnectionSettings connection, boolean enabled) {
    }

    @Transactional(readOnly = true)
    public LlmSettingsResponse getSettings() {
        Optional<LlmSettingsEntity> saved = llmSettingsRepository.findByUserId(currentUser.getUserId());
        ImportLlmSettings settings = toImportSettings(saved);
        return new LlmSettingsResponse(settings.connection().provider(), settings.connection().model(),
                settings.connection().baseUrl(), settings.enabled(), saved.isPresent(), listProviders());
    }

    @Transactional
    public LlmSettingsResponse replaceSettings(LlmSettingsRequest request) {
        long userId = currentUser.getUserId();
        LlmSettingsEntity settings = llmSettingsRepository.findByUserId(userId)
                .orElseGet(() -> new LlmSettingsEntity(userId));
        settings.setProvider(request.provider());
        settings.setModel(request.model().strip());
        settings.setBaseUrl(normalizeBaseUrl(request.baseUrl()));
        settings.setEnabled(request.enabled() == null || request.enabled());
        llmSettingsRepository.saveAndFlush(settings);
        return getSettings();
    }

    /**
     * Checks the given settings (unsaved form values), or the saved ones when {@code request} is null.
     * Failures are reported in the response, not as an error status.
     */
    @Transactional(readOnly = true)
    public LlmConnectionTestResponse testConnection(LlmSettingsRequest request) {
        LlmConnectionSettings connection = request == null
                ? findImportSettings(currentUser.getUserId()).connection()
                : new LlmConnectionSettings(request.provider(), request.model().strip(),
                        baseUrlOrDefault(request.provider(), normalizeBaseUrl(request.baseUrl())));
        LlmConnectionCheck check;
        try {
            check = llmClientFactory.createClient(connection).testConnection();
        } catch (LlmException exception) {
            check = new LlmConnectionCheck(false, exception.getMessage());
        }
        return new LlmConnectionTestResponse(check.ok(), check.message(), connection.provider(), connection.model());
    }

    /** The settings a new import of {@code userId} uses. */
    @Transactional(readOnly = true)
    public ImportLlmSettings findImportSettings(long userId) {
        return toImportSettings(llmSettingsRepository.findByUserId(userId));
    }

    /**
     * The connection for a job recorded with {@code provider} and {@code model}: the user's base URL
     * when they still use that provider, otherwise the provider's default.
     */
    @Transactional(readOnly = true)
    public LlmConnectionSettings findJobConnection(long userId, LlmProvider provider, String model) {
        LlmConnectionSettings current = findImportSettings(userId).connection();
        String baseUrl = current.provider() == provider ? current.baseUrl() : llmProperties.defaultBaseUrl(provider);
        return new LlmConnectionSettings(provider, model, baseUrl);
    }

    public LlmClient createClient(LlmConnectionSettings connection) {
        return llmClientFactory.createClient(connection);
    }

    private ImportLlmSettings toImportSettings(Optional<LlmSettingsEntity> saved) {
        if (saved.isEmpty()) {
            LlmProvider provider = llmProperties.provider();
            return new ImportLlmSettings(new LlmConnectionSettings(provider, llmProperties.defaultModel(provider),
                    llmProperties.defaultBaseUrl(provider)), true);
        }
        LlmSettingsEntity settings = saved.get();
        return new ImportLlmSettings(new LlmConnectionSettings(settings.getProvider(), settings.getModel(),
                baseUrlOrDefault(settings.getProvider(), settings.getBaseUrl())), settings.isEnabled());
    }

    private String baseUrlOrDefault(LlmProvider provider, String baseUrl) {
        return baseUrl != null ? baseUrl : llmProperties.defaultBaseUrl(provider);
    }

    private List<LlmProviderResponse> listProviders() {
        return Arrays.stream(LlmProvider.values())
                .map(provider -> new LlmProviderResponse(provider, llmProperties.defaultModel(provider),
                        llmProperties.defaultBaseUrl(provider), provider.isApiKeyRequired(),
                        llmProperties.apiKey(provider) != null))
                .toList();
    }

    /**
     * Blank means the provider default. Otherwise an http(s) URL with a host and no user info, query or
     * fragment (credentials never go in URLs); a trailing slash is removed.
     */
    static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String trimmed = baseUrl.strip();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Base URL is not a valid URL");
        }
        boolean httpScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        if (!httpScheme || uri.getHost() == null) {
            throw new IllegalArgumentException("Base URL must start with http:// or https:// and name a host");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Base URL must not contain a user name, password, query or fragment");
        }
        return trimmed.replaceAll("/+$", "");
    }
}
