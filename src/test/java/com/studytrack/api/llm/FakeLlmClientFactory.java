package com.studytrack.api.llm;

import com.studytrack.api.llm.LlmDtos.LlmConnectionCheck;
import com.studytrack.api.llm.LlmDtos.LlmConnectionSettings;
import com.studytrack.api.llm.LlmDtos.LlmExtractionRequest;
import com.studytrack.api.llm.LlmDtos.LlmExtractionResult;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Replaces the real factory in every test context (component scan picks it up), so {@code mvn test}
 * never calls an LLM provider. Tests set what the fake answers and read the requests it received.
 */
@Component
@Primary
public class FakeLlmClientFactory implements LlmClientFactory {
    private final List<LlmExtractionRequest> requests = new CopyOnWriteArrayList<>();
    private final List<LlmConnectionSettings> createdFor = new CopyOnWriteArrayList<>();
    private volatile Function<LlmExtractionRequest, String> responder = request -> "{\"items\":[]}";
    private volatile LlmException creationFailure;
    private volatile LlmConnectionCheck connectionCheck = new LlmConnectionCheck(true, "Fake connection ok");

    /** Back to defaults: empty answers, client creation and connection test succeed. */
    public void reset() {
        requests.clear();
        createdFor.clear();
        responder = request -> "{\"items\":[]}";
        creationFailure = null;
        connectionCheck = new LlmConnectionCheck(true, "Fake connection ok");
    }

    /** The function may throw {@link LlmException} to simulate provider failures. */
    public void answerWith(Function<LlmExtractionRequest, String> newResponder) {
        responder = newResponder;
    }

    public void failClientCreation(LlmException failure) {
        creationFailure = failure;
    }

    public void answerConnectionTest(LlmConnectionCheck check) {
        connectionCheck = check;
    }

    public List<LlmExtractionRequest> getRequests() {
        return List.copyOf(requests);
    }

    public List<LlmConnectionSettings> getCreatedFor() {
        return List.copyOf(createdFor);
    }

    @Override
    public LlmClient createClient(LlmConnectionSettings settings) {
        if (creationFailure != null) {
            throw creationFailure;
        }
        createdFor.add(settings);
        return new FakeLlmClient();
    }

    private final class FakeLlmClient implements LlmClient {
        @Override
        public LlmExtractionResult extract(LlmExtractionRequest request) {
            requests.add(request);
            String answer = responder.apply(request);
            return new LlmExtractionResult(answer, request.userPrompt().length() / 4, answer.length() / 4);
        }

        @Override
        public LlmConnectionCheck testConnection() {
            return connectionCheck;
        }
    }
}
