-- Allow Groq (OpenAI-compatible API) as an import provider. Its key comes from GROQ_API_KEY.
ALTER TABLE import_jobs DROP CONSTRAINT ck_import_jobs_provider;
ALTER TABLE import_jobs ADD CONSTRAINT ck_import_jobs_provider
    CHECK (llm_provider IN ('ANTHROPIC', 'OPENAI', 'GROQ', 'OLLAMA'));

ALTER TABLE llm_settings DROP CONSTRAINT ck_llm_settings_provider;
ALTER TABLE llm_settings ADD CONSTRAINT ck_llm_settings_provider
    CHECK (provider IN ('ANTHROPIC', 'OPENAI', 'GROQ', 'OLLAMA'));
