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
package synapse.converter.bir.mediators;

import common.BallerinaModel.Expression.StringTemplate;
import common.BallerinaModel.Expression.XMLTemplate;
import common.BallerinaModel.Import;
import common.BallerinaModel.Statement;
import synapse.converter.ConversionContext.UnsupportedEntry;
import synapse.converter.ScopeContext;
import synapse.converter.TypeConverter;
import synapse.converter.bir.BIRConverter;
import synapse.model.Synapse;
import synapse.model.Synapse.Log;
import synapse.model.Synapse.Property;
import synapse.model.Synapse.SynapseNode;
import synapse.model.SynapseType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Converts a Synapse {@code <log>} mediator into a {@code log:print*} call. {@code category} and
 * {@code level} are independent: {@code category} selects the Ballerina log function (Ballerina has no
 * trace/fatal level, so {@code TRACE} collapses to {@code printDebug} and {@code FATAL} to
 * {@code printError}; {@code level} selects what the message contains.
 *
 * <p>Only {@code custom} is fully replicated: its message is exactly the listed properties, matching
 * real Synapse. {@code simple}/{@code headers}/{@code full} also log the listed properties plus, for
 * {@code full}, the current payload; the built-in fields real Synapse would add for these levels
 * (To/From/WSAction/SOAPAction/ReplyTo/MessageID/correlation_id for simple/full, SOAP header blocks for
 * headers) have no equivalent in the generated {@code Context} and are surfaced as a to-do instead of
 * silently dropped.
 */
public class LogConverter implements BIRConverter<ScopeContext> {

    private static final Import LOG_IMPORT = new Import("ballerina", "log");

    private static final String CUSTOM_LEVEL = "custom";
    private static final String HEADERS_LEVEL = "headers";
    private static final String FULL_LEVEL = "full";
    private static final Set<String> KNOWN_LEVELS = Set.of("simple", HEADERS_LEVEL, FULL_LEVEL, CUSTOM_LEVEL);
    private static final Set<String> KNOWN_CATEGORIES = Set.of("INFO", "TRACE", "DEBUG", "WARN", "ERROR", "FATAL");

    @Override
    public void convert(SynapseNode node, ScopeContext context) {
        Log log = (Log) node;
        String level = normalize("level", log.level(), KNOWN_LEVELS, Synapse.DEFAULT_LOG_LEVEL, context);
        log.unrecognizedChildren().forEach(child -> reportUnrecognizedChild(child, context));

        String separator = StringTemplate.escapeText(log.separator());
        PropertiesResult properties = propertiesBody(log, separator, context);
        if (FULL_LEVEL.equals(level) || properties.needsContext()) {
            context.ensureContextAvailable();
        }

        String body = properties.body();
        if (FULL_LEVEL.equals(level)) {
            // ctx.payload is anydata; a string template's ${...} only accepts a basic type, so it must be
            // converted first, exactly like a non-literal property value is (see propertiesBody).
            String payloadEntry = "Payload: ${"
                    + TypeConverter.convertAnyData("ctx.payload", SynapseType.STRING, context.shared()) + "}";
            body = body.isEmpty() ? payloadEntry : body + separator + payloadEntry;
        }
        if (!CUSTOM_LEVEL.equals(level)) {
            reportUnreplicatedBuiltinFields(log, level, context);
        }

        context.importStatements().add(LOG_IMPORT);
        context.statements().add(new Statement.BallerinaStatement(
                "log:" + logFunctionFor(normalize("category", log.category(), KNOWN_CATEGORIES,
                        Synapse.DEFAULT_LOG_CATEGORY, context)) + "(" + new StringTemplate(body) + ");"));
    }

    // The joined "name = value" text for a <log>'s properties, plus whether building it referenced ctx
    // so the caller knows whether to ensure ctx is in scope without a second pass over log.properties().
    private record PropertiesResult(String body, boolean needsContext) {
    }

    private static PropertiesResult propertiesBody(Log log, String escapedSeparator, ScopeContext context) {
        List<String> parts = new ArrayList<>();
        boolean needsContext = false;
        for (Property property : log.properties()) {
            String name = StringTemplate.escapeText(property.name());
            if (property.hasOmElement()) {
                // An inline XML child value: convert it to its string representation, the same way any
                // other anydata-typed value logged here is.
                String xmlLiteral = new XMLTemplate(property.omElement()).toString();
                parts.add(name + " = ${"
                        + TypeConverter.convertAnyData(xmlLiteral, SynapseType.STRING, context.shared()) + "}");
                continue;
            }
            needsContext |= property.hasExpression();
            String raw = property.hasExpression() ? property.expression() : property.value();
            PropertyConverter.resolveExpression(raw, !property.hasExpression(), SynapseType.STRING, context)
                    .ifPresent(value -> parts.add(name + " = ${" + value + "}"));
        }
        return new PropertiesResult(String.join(escapedSeparator, parts), needsContext);
    }

    private static String logFunctionFor(String category) {
        return switch (category) {
            case "DEBUG", "TRACE" -> "printDebug";
            case "WARN" -> "printWarn";
            case "ERROR", "FATAL" -> "printError";
            default -> "printInfo";
        };
    }

    // Shared by level and category, which validate identically and differ only in the attribute name, the
    // known-value set, and the fallback.
    private static String normalize(String attribute, String value, Set<String> known, String fallback,
                                     ScopeContext context) {
        if (known.contains(value)) {
            return value;
        }
        String file = context.shared().currentFile();
        String detail = "Unrecognized log " + attribute + " '" + value + "'; falling back to '" + fallback + "'.";
        context.statements().add(new Statement.Comment("TODO: " + detail));
        context.shared().reportUnsupported(
                new UnsupportedEntry("Unsupported log attribute", "log", file, detail,
                        "<log " + attribute + "=\"" + value + "\">"));
        return fallback;
    }

    // <log>'s only valid child is <property>; anything else is captured verbatim by the reader
    // (Synapse.Log#unrecognizedChildren) rather than silently dropped, matching how an unrecognized
    // mediator/attribute elsewhere in this converter is surfaced instead of ignored.
    private static void reportUnrecognizedChild(String rawXml, ScopeContext context) {
        String file = context.shared().currentFile();
        String detail = "A <log> child other than <property> is not supported; manual conversion required.";
        context.statements().add(new Statement.Comment("TODO: " + detail + "\nOriginal Synapse:\n" + rawXml));
        context.shared().reportUnsupported(
                new UnsupportedEntry("Unsupported log child", "log", file, detail, rawXml));
    }

    // The built-in fields real Synapse adds for a given non-custom level, per LogMediator's own
    // getSimpleLogMessage/getHeadersLogMessage: 'headers' only ever adds correlation_id and SOAP header
    // blocks, never the To/From/etc. set, which is exclusive to 'simple'/'full'.
    private static String missingBuiltinFields(String level) {
        return HEADERS_LEVEL.equals(level)
                ? "correlation_id and the SOAP header blocks"
                : "To/From/WSAction/SOAPAction/ReplyTo/MessageID, and correlation_id";
    }

    private static void reportUnreplicatedBuiltinFields(Log log, String level, ScopeContext context) {
        String file = context.shared().currentFile();
        String detail = "Synapse's '" + level + "' log level also logs built-in fields ("
                + missingBuiltinFields(level) + ") that have no equivalent in the generated Context; only the "
                + "listed <property> values" + (FULL_LEVEL.equals(level) ? " and the current payload are" : " are")
                + " logged. Manual conversion required for full parity.";
        context.statements().add(new Statement.Comment("TODO: " + detail));
        context.shared().reportUnsupported(
                new UnsupportedEntry("Partially supported log level", "log", file, detail,
                        "<log level=\"" + level + "\">"));
    }
}
