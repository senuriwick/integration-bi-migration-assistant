/*
 *  Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package synapse.converter;

import common.ConversionUtils;
import synapse.converter.ConversionContext.UnsupportedEntry;
import synapse.converter.bir.InboundEndpointConverter;
import synapse.model.DependencyGraph;
import synapse.model.DependencyGraph.ArtifactNode;
import synapse.model.DependencyResolver;
import synapse.model.Synapse;
import synapse.model.Synapse.Api;
import synapse.model.Synapse.InboundEndpoint;
import synapse.model.Synapse.Kind;
import synapse.model.Synapse.Param;
import synapse.model.Synapse.Resource;
import synapse.model.Synapse.SynapseNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Resolves, before any artifact is converted, which {@code <api>} artifacts an http/https
 * {@code <inboundEndpoint>}'s {@code dispatch.filter.pattern} parameter additionally exposes on that
 * endpoint's own listener.
 */
public final class DispatchFilterIndexer {

    private static final Set<String> HTTP_PROTOCOLS = Set.of("http", "https");
    // Stands in for a resource's matchAnyPath segment or a {templateParam} segment when building a
    // concrete-looking path to test the pattern against; only its shape (one path segment), not its
    // value, matters for a regex match.
    private static final String PLACEHOLDER_SEGMENT = "x";

    private DispatchFilterIndexer() {
    }

    public static void index(DependencyGraph dependencyGraph, ConversionContext context) {
        List<ArtifactNode> inboundEndpointNodes = new ArrayList<>();
        List<ArtifactNode> apiNodes = new ArrayList<>();
        for (ArtifactNode node : dependencyGraph.nodes().keySet()) {
            if (node.kind() == Kind.INBOUND_ENDPOINT) {
                inboundEndpointNodes.add(node);
            } else if (node.kind() == Kind.API) {
                apiNodes.add(node);
            }
        }
        if (inboundEndpointNodes.isEmpty() || apiNodes.isEmpty()) {
            return;
        }

        List<Api> apis = new ArrayList<>();
        for (ArtifactNode node : apiNodes) {
            if (DependencyResolver.findArtifact(node) instanceof Api api) {
                apis.add(api);
            }
        }

        for (ArtifactNode node : inboundEndpointNodes) {
            if (DependencyResolver.findArtifact(node) instanceof InboundEndpoint inboundEndpoint) {
                indexInboundEndpoint(inboundEndpoint, apis, node.file(), context);
            }
        }
    }

    private static void indexInboundEndpoint(InboundEndpoint inboundEndpoint, List<Api> apis, Path file,
                                              ConversionContext context) {
        if (!HTTP_PROTOCOLS.contains(inboundEndpoint.protocol().toLowerCase(Locale.ROOT))) {
            return;
        }
        Optional<String> dispatchFilterPattern = dispatchFilterPattern(inboundEndpoint);
        if (dispatchFilterPattern.isEmpty()) {
            return;
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(dispatchFilterPattern.get());
        } catch (PatternSyntaxException e) {
            // convertHttp still skips generating its own catch-all resource once dispatch.filter.pattern
            // is present at all, regardless of whether it compiles - so an invalid pattern silently
            // leaves the endpoint's listener with no service at all unless reported here.
            reportInvalidDispatchFilterPattern(inboundEndpoint, dispatchFilterPattern.get(), file, e, context);
            return;
        }
        String listenerName = InboundEndpointConverter.httpListenerName(inboundEndpoint);
        for (Api api : apis) {
            if (matches(pattern, api)) {
                context.addExtraApiListener(api.name(), listenerName);
            }
        }
    }

    // This pre-pass runs before sourceRoot is known (see SynapseConverter#migrateSynapse), so the report
    // cites the bare filename rather than a source-root-relative path like every other reported entry.
    private static void reportInvalidDispatchFilterPattern(InboundEndpoint inboundEndpoint, String pattern,
                                                             Path file, PatternSyntaxException cause,
                                                             ConversionContext context) {
        String reason = cause.getDescription() + " near index " + cause.getIndex();
        String detail = "Inbound endpoint '" + inboundEndpoint.name() + "' parameter '"
                + Synapse.DISPATCH_FILTER_PATTERN_PARAM + "' is not a valid regular expression (" + reason
                + "); no <api> can be matched against it, so this endpoint's listener is left with no service at "
                + "all. Manual conversion required.";
        String snippet = "<parameter name=\"" + Synapse.DISPATCH_FILTER_PATTERN_PARAM + "\">" + pattern
                + "</parameter>";
        context.reportUnsupported(new UnsupportedEntry("Unsupported inbound endpoint parameter", "parameter",
                file == null ? "" : file.getFileName().toString(), detail, snippet));
    }

    // A pattern is written against real request paths, which may target the api's bare context
    // directly (e.g. "^(/foo|/bar)$", matching a whole api with no further sub-path) or a path reaching
    // into one of its resources (e.g. "/api/.*", which never matches the bare context "/api" itself,
    // only "/api/..."). Both are checked: the context alone, and the context joined with each resource's
    // path (template segments replaced by a placeholder, since only the shape - not the value - matters
    // for matching a regex).
    private static boolean matches(Pattern pattern, Api api) {
        if (pattern.matcher(api.context()).matches()) {
            return true;
        }
        for (SynapseNode child : api.resources()) {
            if (child instanceof Resource resource
                    && pattern.matcher(joinPath(api.context(), representativePath(resource))).matches()) {
                return true;
            }
        }
        return false;
    }

    private static String representativePath(Resource resource) {
        if (resource.matchAnyPath()) {
            return PLACEHOLDER_SEGMENT;
        }
        List<String> segments = new ArrayList<>();
        for (String segment : ConversionUtils.splitPathSegments(resource.path())) {
            segments.add(segment.startsWith("{") && segment.endsWith("}") ? PLACEHOLDER_SEGMENT : segment);
        }
        return String.join("/", segments);
    }

    private static String joinPath(String context, String resourcePath) {
        String base = context.endsWith("/") ? context.substring(0, context.length() - 1) : context;
        return base + "/" + resourcePath;
    }

    private static Optional<String> dispatchFilterPattern(InboundEndpoint inboundEndpoint) {
        for (Param parameter : inboundEndpoint.parameters()) {
            if (Synapse.DISPATCH_FILTER_PATTERN_PARAM.equals(parameter.name())) {
                return Optional.of(parameter.value());
            }
        }
        return Optional.empty();
    }
}
