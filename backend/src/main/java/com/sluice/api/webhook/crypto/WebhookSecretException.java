package com.sluice.api.webhook.crypto;

public class WebhookSecretException extends RuntimeException {
    public WebhookSecretException(String message, Throwable cause) {
        super(message, cause);
    }

    public WebhookSecretException(String message) {
        super(message);
    }
}
