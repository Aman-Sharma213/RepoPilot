package com.repopilot.cli;

import com.repopilot.ai.AiMode;
import com.repopilot.ai.GroqClient;
import picocli.CommandLine.Option;

/** Safe BYOK options: only an environment-variable name is accepted, never a raw secret. */
public class AiOptions {

    @Option(names = "--mode", paramLabel = "<local|ai|auto>", defaultValue = "auto",
            description = "Analysis mode: local, ai, or auto (default: auto).")
    String mode = "auto";

    @Option(names = "--no-ai", description = "Force fully local analysis; equivalent to --mode local.")
    boolean noAi;

    @Option(names = "--groq-key-env", paramLabel = "<name>", defaultValue = GroqClient.API_KEY_ENV,
            description = "Environment variable containing the Groq key; the key is never persisted.")
    String keyEnvironment = GroqClient.API_KEY_ENV;

    @Option(names = "--groq-model", paramLabel = "<model>", description = "Groq model override.")
    String model;

    public AiMode selectedMode() {
        return noAi ? AiMode.LOCAL : AiMode.parse(mode);
    }

    public GroqClient client() {
        AiMode selected = selectedMode();
        if (selected == AiMode.LOCAL) {
            return GroqClient.disabled();
        }
        validateEnvironmentName(keyEnvironment);
        String key = System.getenv(keyEnvironment);
        if (selected == AiMode.AI_ASSISTED && (key == null || key.isBlank())) {
            throw new IllegalArgumentException("AI mode requires a key in environment variable "
                    + keyEnvironment + ". Use --mode local to continue without AI.");
        }
        String selectedModel = model == null || model.isBlank()
                ? System.getenv().getOrDefault(GroqClient.MODEL_ENV, GroqClient.DEFAULT_MODEL) : model;
        return GroqClient.forKey(key, selectedModel);
    }

    public String displayMode() {
        AiMode selected = selectedMode();
        if (selected == AiMode.AUTO) {
            String key = System.getenv(keyEnvironment);
            return key == null || key.isBlank() ? "LOCAL (auto fallback)" : "AI_ASSISTED (auto)";
        }
        return selected.name();
    }

    private void validateEnvironmentName(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("--groq-key-env must be an environment-variable name");
        }
    }
}
