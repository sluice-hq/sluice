package com.sluice.api.webhook.service;

import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

@Service
public class WebhookTargetValidator {
    @FunctionalInterface
    public interface HostResolver { InetAddress[] resolve(String host) throws Exception; }

    private final HostResolver resolver;

    public WebhookTargetValidator() {
        this(InetAddress::getAllByName);
    }

    WebhookTargetValidator(HostResolver resolver) { this.resolver = resolver; }

    public URI validate(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Webhook URL must be an absolute HTTP or HTTPS URL without credentials or fragments");
            }
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
