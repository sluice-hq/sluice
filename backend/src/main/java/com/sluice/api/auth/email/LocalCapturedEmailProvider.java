package com.sluice.api.auth.email;

import com.sluice.api.runtime.ConditionalOnApiRuntime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.List;

@Component
@ConditionalOnApiRuntime
@ConditionalOnProperty(name = "sluice.auth.email.provider", havingValue = "local", matchIfMissing = true)
public class LocalCapturedEmailProvider implements AuthEmailProvider {
    private final ArrayDeque<AuthEmail> messages = new ArrayDeque<>();
    private final int capacity;

    public LocalCapturedEmailProvider(@Value("${sluice.auth.email.local.capacity:1000}") int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    @Override
    public synchronized void send(AuthEmail email) {
        while (messages.size() >= capacity) messages.removeFirst();
        messages.addLast(email);
    }

    public synchronized List<AuthEmail> messages() { return List.copyOf(messages); }
    public synchronized void clear() { messages.clear(); }
}
