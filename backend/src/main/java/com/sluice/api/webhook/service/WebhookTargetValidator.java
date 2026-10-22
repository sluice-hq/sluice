package com.sluice.api.webhook.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

@Service
public class WebhookTargetValidator {
    @FunctionalInterface
    public interface HostResolver { InetAddress[] resolve(String host) throws Exception; }

    private final HostResolver resolver;
    private final boolean localDemoCallback;

    @Autowired
    public WebhookTargetValidator(Environment environment,
            @Value("${SLUICE_WEBHOOK_LOCAL_DEMO_CALLBACK_ENABLED:false}") boolean enabled) {
        this(InetAddress::getAllByName, enabled);
        if (enabled && (!environment.acceptsProfiles(Profiles.of("local"))
                || environment.acceptsProfiles(Profiles.of("production")))) {
            throw new IllegalStateException("The local webhook demo callback is permitted only in the local profile");
        }
    }

    WebhookTargetValidator(HostResolver resolver) { this(resolver, false); }

    WebhookTargetValidator(HostResolver resolver, boolean localDemoCallback) {
        this.resolver = resolver;
        this.localDemoCallback = localDemoCallback;
    }

    public URI validate(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Webhook URL must be an absolute HTTP or HTTPS URL without credentials or fragments");
            }
            // A fixed local fixture endpoint, never an arbitrary private-network allowlist.
            if (localDemoCallback && (value.equals("http://host.docker.internal:8787/webhooks")
                    || value.equals("http://localhost:8787/webhooks"))) return uri;
            InetAddress[] addresses = resolver.resolve(uri.getHost());
            if (addresses.length == 0) throw new IllegalArgumentException("Webhook host did not resolve");
            for (InetAddress address : addresses) {
                if (isUnsafe(address)) {
                    throw new IllegalArgumentException("Webhook URL must not target a private or local network address");
                }
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Webhook host could not be resolved", exception);
        }
    }

    private boolean isUnsafe(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0 || (first == 100 && second >= 64 && second <= 127);
        }
        return (bytes[0] & 0xfe) == 0xfc;
    }
}
