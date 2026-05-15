package io.github.hectorvent.floci.core.common;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Global CORS support for browser-based local development.
 *
 * <p>Configured via {@code EXTRA_CORS_ALLOWED_ORIGINS} (or the equivalent
 * {@code FLOCI_SECURITY_EXTRA_CORS_ALLOWED_ORIGINS}). Service-level CORS
 * responses, such as S3 bucket CORS rules or API Gateway integrations, keep
 * precedence because this filter only fills missing CORS headers.</p>
 */
@Provider
@PreMatching
@Priority(Priorities.HEADER_DECORATOR)
public class GlobalCorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String ORIGIN = "Origin";
    private static final String REQUEST_METHOD = "Access-Control-Request-Method";
    private static final String REQUEST_HEADERS = "Access-Control-Request-Headers";
    private static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String ALLOW_METHODS = "Access-Control-Allow-Methods";
    private static final String ALLOW_HEADERS = "Access-Control-Allow-Headers";
    private static final String EXPOSE_HEADERS = "Access-Control-Expose-Headers";
    private static final String VARY = "Vary";

    private static final String DEFAULT_ALLOW_METHODS = "GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS";
    private static final String DEFAULT_ALLOW_HEADERS = String.join(", ",
            "authorization",
            "content-type",
            "x-amz-content-sha256",
            "x-amz-date",
            "x-amz-security-token",
            "x-amz-target",
            "x-amz-user-agent");

    @Override
    public void filter(ContainerRequestContext requestContext) {
        CorsSettings settings = CorsSettings.load();
        if (!settings.enabled()) {
            return;
        }

        String origin = requestContext.getHeaderString(ORIGIN);
        if (origin == null || !settings.matchesOrigin(origin)) {
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(requestContext.getMethod())
                && requestContext.getHeaderString(REQUEST_METHOD) != null) {
            Response.ResponseBuilder builder = Response.noContent()
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .header(ALLOW_ORIGIN, settings.allowOriginHeader(origin))
                    .header(ALLOW_METHODS, DEFAULT_ALLOW_METHODS)
                    .header(ALLOW_HEADERS, settings.allowHeaders(requestContext.getHeaderString(REQUEST_HEADERS)))
                    .header(VARY, ORIGIN);

            settings.exposeHeaders().ifPresent(value -> builder.header(EXPOSE_HEADERS, value));
            requestContext.abortWith(builder.build());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        CorsSettings settings = CorsSettings.load();
        if (!settings.enabled()) {
            return;
        }

        String origin = requestContext.getHeaderString(ORIGIN);
        if (origin == null || !settings.matchesOrigin(origin)) {
            return;
        }

        MultivaluedMap<String, Object> headers = responseContext.getHeaders();
        headers.putIfAbsent(ALLOW_ORIGIN, List.of(settings.allowOriginHeader(origin)));
        settings.exposeHeaders().ifPresent(value -> headers.putIfAbsent(EXPOSE_HEADERS, List.of(value)));
        addVaryOrigin(headers);
    }

    private static void addVaryOrigin(MultivaluedMap<String, Object> headers) {
        boolean hasOrigin = Optional.ofNullable(headers.get(VARY))
                .orElse(List.of())
                .stream()
                .flatMap(value -> Arrays.stream(String.valueOf(value).split(",")))
                .map(String::trim)
                .anyMatch(ORIGIN::equalsIgnoreCase);
        if (!hasOrigin) {
            headers.add(VARY, ORIGIN);
        }
    }

    private record CorsSettings(boolean disabled,
                                Set<String> allowedOrigins,
                                Set<String> extraAllowedHeaders,
                                Set<String> extraExposeHeaders) {

        static CorsSettings load() {
            return new CorsSettings(
                    readBoolean("floci.security.disable-cors-headers",
                            "disable.cors.headers"),
                    readCsv("floci.security.extra-cors-allowed-origins",
                            "extra.cors.allowed.origins"),
                    readCsv("floci.security.extra-cors-allowed-headers",
                            "extra.cors.allowed.headers"),
                    readCsv("floci.security.extra-cors-expose-headers",
                            "extra.cors.expose.headers"));
        }

        boolean enabled() {
            return !disabled && !allowedOrigins.isEmpty();
        }

        boolean matchesOrigin(String origin) {
            return allowedOrigins.contains("*")
                    || allowedOrigins.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(origin));
        }

        String allowOriginHeader(String origin) {
            return allowedOrigins.contains("*") ? "*" : origin;
        }

        String allowHeaders(String requestedHeaders) {
            LinkedHashSet<String> headers = new LinkedHashSet<>();
            headers.addAll(splitCsv(DEFAULT_ALLOW_HEADERS));
            headers.addAll(extraAllowedHeaders);
            headers.addAll(splitCsv(requestedHeaders));
            return String.join(", ", headers);
        }

        Optional<String> exposeHeaders() {
            return extraExposeHeaders.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", extraExposeHeaders));
        }

        private static boolean readBoolean(String... names) {
            for (String name : names) {
                Optional<String> value = ConfigProvider.getConfig().getOptionalValue(name, String.class);
                if (value.isPresent()) {
                    String normalized = value.get().trim().toLowerCase(Locale.ROOT);
                    return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized);
                }
            }
            return false;
        }

        private static Set<String> readCsv(String... names) {
            for (String name : names) {
                Optional<String> value = ConfigProvider.getConfig().getOptionalValue(name, String.class);
                if (value.isPresent()) {
                    return splitCsv(value.get());
                }
            }
            return Set.of();
        }

        private static LinkedHashSet<String> splitCsv(String value) {
            if (value == null || value.isBlank()) {
                return new LinkedHashSet<>();
            }
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
