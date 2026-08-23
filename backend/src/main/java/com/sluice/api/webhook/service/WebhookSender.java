package com.sluice.api.webhook.service;

import java.net.URI;
import java.util.Map;

public interface WebhookSender {
    int send(URI uri, String payload, Map<String, String> headers) throws Exception;
}
