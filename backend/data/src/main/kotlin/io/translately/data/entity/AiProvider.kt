package io.translately.data.entity

/**
 * AI provider selected for a project.
 *
 * Translately is **bring-your-own-key only**: the platform never ships with
 * credentials and never proxies through a platform-owned account. When no
 * provider is configured (which is the default), AI suggestion buttons are
 * simply absent from the UI — the product works end-to-end without AI.
 */
enum class AiProvider {
    /** Anthropic Claude — `x-api-key` authentication. */
    ANTHROPIC,

    /** OpenAI — `Authorization: Bearer` authentication. */
    OPENAI,

    /**
     * Any OpenAI-compatible endpoint: Codex, Ollama, vLLM, LM Studio, self-hosted.
     * The adapter reads `ai_base_url` from the project config.
     */
    OPENAI_COMPATIBLE,
}
