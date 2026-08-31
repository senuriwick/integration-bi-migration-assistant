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
package synapse.reader;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import synapse.model.Synapse;
import synapse.model.Synapse.Api;
import synapse.model.Synapse.ClassMediator;
import synapse.model.Synapse.FaultSequence;
import synapse.model.Synapse.FaultSequenceRef;
import synapse.model.Synapse.InSequence;
import synapse.model.Synapse.InboundEndpoint;
import synapse.model.Synapse.KeyStoreConfig;
import synapse.model.Synapse.Log;
import synapse.model.Synapse.Param;
import synapse.model.Synapse.PayloadFactory;
import synapse.model.Synapse.Property;
import synapse.model.Synapse.Resource;
import synapse.model.Synapse.Respond;
import synapse.model.Synapse.Sequence;
import synapse.model.Synapse.SequenceMediator;
import synapse.model.Synapse.SynapseNode;
import synapse.model.Synapse.Unsupported;
import synapse.model.Synapse.UnsupportedArtifact;
import synapse.model.SynapseType;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class SynapseModelGenerator {

    private static final String API_TAG = "api";
    private static final String SEQUENCE_TAG = "sequence";
    private static final String DEFINITIONS_TAG = "definitions";
    private static final String RESOURCE_TAG = "resource";
    private static final String IN_SEQUENCE_TAG = "inSequence";
    private static final String FAULT_SEQUENCE_TAG = "faultSequence";
    private static final String PAYLOAD_FACTORY_TAG = "payloadFactory";
    private static final String RESPOND_TAG = "respond";
    private static final String PROPERTY_TAG = "property";
    private static final String LOG_TAG = "log";
    private static final String DEFAULT_LOG_SEPARATOR = ", ";
    private static final String FORMAT_TAG = "format";
    private static final String CLASS_TAG = "class";
    private static final String INBOUND_ENDPOINT_TAG = "inboundEndpoint";
    private static final String PARAMETERS_TAG = "parameters";
    private static final String PARAMETER_TAG = "parameter";
    private static final String KEYSTORE_PARAM_NAME = "keystore";
    private static final String KEY_STORE_TAG = "KeyStore";
    private static final String KEY_STORE_LOCATION_TAG = "Location";
    private static final String KEY_STORE_TYPE_TAG = "Type";
    private static final String KEY_STORE_PASSWORD_TAG = "Password";
    private static final String KEY_STORE_KEY_PASSWORD_TAG = "KeyPassword";
    private static final String HTTPS_PROTOCOL = "https";

    private static final String DEFAULT_PROPERTY_SCOPE = "default";
    private static final String DEFAULT_PROPERTY_ACTION = "set";

    // Unsupported mediators whose children are themselves mediator sequences (control-flow wrappers),
    // so nested recognised mediators can still be converted. Every other unsupported mediator is
    // treated as an opaque leaf.
    private static final Set<String> CONTAINER_MEDIATOR_TAGS =
            Set.of("filter", "switch", "foreach", "iterate", "clone", "aggregate");

    // Child elements of a container mediator that hold a mediator sequence to descend into. Note
    // <sequence> is deliberately excluded: a keyed <sequence key="..."/> is a call mediator, while an
    // anonymous <sequence> is a container — they are told apart by their 'key' attribute at read time.
    private static final Set<String> MEDIATOR_SEQUENCE_TAGS =
            Set.of("then", "else", "case", "default", "target", "onComplete", IN_SEQUENCE_TAG);

    public static List<SynapseNode> generateModel(Element rootElement) {
        List<SynapseNode> nodes = new ArrayList<>();

        // A <definitions> root wraps several artifacts; every other root is a single artifact (a
        // supported <api>/<sequence>, or an unsupported one such as <proxy> captured for the report).
        if (DEFINITIONS_TAG.equals(rootElement.getTagName())) {
            for (Element child : childElements(rootElement)) {
                nodes.add(readArtifact(child));
            }
            return nodes;
        }

        nodes.add(readArtifact(rootElement));
        return nodes;
    }

    @NotNull
    private static SynapseNode readArtifact(Element element) {
        return switch (element.getTagName()) {
            case API_TAG -> readApi(element);
            case SEQUENCE_TAG -> readSequence(element);
            case INBOUND_ENDPOINT_TAG -> readInboundEndpoint(element);
            default -> new UnsupportedArtifact(element.getTagName(), element.getAttribute("name"),
                    serializeElement(element));
        };
    }

    @NotNull
    private static InboundEndpoint readInboundEndpoint(Element element) {
        assert element != null : "element must not be null";
        String name = element.getAttribute("name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Synapse inboundEndpoint must define a non-empty 'name'.");
        }
        String onErrorKey = element.getAttribute("onError");
        boolean isHttpsProtocol = HTTPS_PROTOCOL.equalsIgnoreCase(element.getAttribute("protocol"));

        List<Param> parameters = new ArrayList<>();
        Optional<KeyStoreConfig> keyStore = Optional.empty();
        for (Element child : childElements(element)) {
            if (PARAMETERS_TAG.equals(child.getTagName())) {
                for (Element parameter : childElements(child)) {
                    if (!PARAMETER_TAG.equals(parameter.getTagName())) {
                        continue;
                    }
                    // An https keystore parameter nests a <KeyStore> element; read as a plain Param,
                    // getTextContent() would flatten it into a whitespace blob. Gated to https only so a
                    // keystore on any other protocol still reaches reportUnsupportedParameter as before.
                    if (isHttpsProtocol && KEYSTORE_PARAM_NAME.equals(parameter.getAttribute("name"))) {
                        keyStore = readKeyStoreConfig(parameter);
                    } else {
                        parameters.add(new Param(parameter.getAttribute("name"), parameter.getTextContent().trim()));
                    }
                }
            }
        }

        return new InboundEndpoint(name, element.getAttribute("protocol"), element.getAttribute("class"),
                element.getAttribute("sequence"),
                onErrorKey.isBlank() ? new FaultSequenceRef.None() : new FaultSequenceRef.KeyRef(onErrorKey),
                parameters, keyStore, Boolean.parseBoolean(element.getAttribute("suspend")),
                serializeElement(element));
    }

    // Reads <parameter name="keystore"><KeyStore><Location/><Type/><Password/><KeyPassword/></KeyStore>
    // </parameter>; each KeyStore child is a leaf, so getTextContent() is safe on them.
    @NotNull
    private static Optional<KeyStoreConfig> readKeyStoreConfig(Element keystoreParameter) {
        for (Element keyStoreElement : childElements(keystoreParameter)) {
            if (!KEY_STORE_TAG.equals(keyStoreElement.getTagName())) {
                continue;
            }
            String location = "";
            String type = "";
            String password = "";
            Optional<String> keyPassword = Optional.empty();
            for (Element field : childElements(keyStoreElement)) {
                switch (field.getTagName()) {
                    case KEY_STORE_LOCATION_TAG -> location = field.getTextContent().trim();
                    case KEY_STORE_TYPE_TAG -> type = field.getTextContent().trim();
                    case KEY_STORE_PASSWORD_TAG -> password = field.getTextContent().trim();
                    case KEY_STORE_KEY_PASSWORD_TAG -> keyPassword = Optional.of(field.getTextContent().trim());
                    default -> { }
                }
            }
            return Optional.of(new KeyStoreConfig(location, type, password, keyPassword));
        }
        return Optional.empty();
    }

    private static Api readApi(Element element) {
        String name = element.getAttribute("name");
        String context = element.getAttribute("context");

        List<SynapseNode> resources = new ArrayList<>();
        for (Element child : childElements(element)) {
            if (RESOURCE_TAG.equals(child.getTagName())) {
                resources.add(readResource(child));
            }
        }

        return new Api(name, context, resources);
    }

    private static Resource readResource(Element element) {
        String methods = element.getAttribute("methods");
        String faultSequenceKey = element.getAttribute("faultSequence");

        String uriTemplate = element.getAttribute("uri-template");
        String urlMapping = element.getAttribute("url-mapping");
        // A resource with neither 'uri-template' nor 'url-mapping' matches any path in Synapse; it is
        // converted to a Ballerina rest path parameter rather than being rejected.
        boolean matchAnyPath = uriTemplate.isEmpty() && urlMapping.isEmpty();
        String template = !uriTemplate.isEmpty() ? uriTemplate : urlMapping;

        String path = "";
        String query = "";
        int queryStart = template.indexOf('?');
        if (queryStart >= 0) {
            path = template.substring(0, queryStart);
            query = template.substring(queryStart + 1);
        } else {
            path = template;
        }

        List<String> queryParams = new ArrayList<>();
        if (!query.isEmpty()) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                String key = eq >= 0 ? pair.substring(0, eq) : pair;
                if (!key.isEmpty()) {
                    queryParams.add(key);
                }
            }
        }

        // A faultSequence="X" attribute takes priority over an inline <faultSequence>, mirroring
        // Synapse's own ResourceFactory#configureSequences: once the attribute is present, the inline
        // element is never parsed into a mediator sequence at all.
        boolean hasFaultSequenceKey = !faultSequenceKey.isBlank();
        InSequence inSequence = null;
        FaultSequence faultSequence = null;
        for (Element child : childElements(element)) {
            if (IN_SEQUENCE_TAG.equals(child.getTagName()) && inSequence == null) {
                inSequence = readInSequence(child);
            } else if (!hasFaultSequenceKey && FAULT_SEQUENCE_TAG.equals(child.getTagName())
                    && faultSequence == null) {
                faultSequence = readFaultSequence(child);
            }
        }

        FaultSequenceRef faultSequenceRef = hasFaultSequenceKey ? new FaultSequenceRef.KeyRef(faultSequenceKey)
                : faultSequence != null ? new FaultSequenceRef.Inline(faultSequence) : new FaultSequenceRef.None();
        return new Resource(methods, path, matchAnyPath, queryParams, inSequence, faultSequenceRef);
    }

    private static Sequence readSequence(Element element) {
        String name = element.getAttribute("name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Synapse sequence must define a non-empty 'name'.");
        }
        String onError = element.getAttribute("onError");
        String description = element.getAttribute("description");
        return new Sequence(name, onError, description, readMediators(element));
    }

    private static InSequence readInSequence(Element element) {
        return new InSequence(readMediators(element));
    }

    private static FaultSequence readFaultSequence(Element element) {
        return new FaultSequence(readMediators(element));
    }

    private static List<SynapseNode> readMediators(Element element) {
        List<SynapseNode> mediators = new ArrayList<>();
        for (Element child : childElements(element)) {
            mediators.add(readMediator(child));
        }
        return mediators;
    }

    @NotNull
    private static SynapseNode readMediator(Element child) {
        return switch (child.getTagName()) {
            case PAYLOAD_FACTORY_TAG -> readPayloadFactory(child);
            case RESPOND_TAG -> new Respond();
            case PROPERTY_TAG -> readProperty(child);
            case LOG_TAG -> readLog(child);
            case SEQUENCE_TAG -> {
                String key = child.getAttribute("key");
                if (key.isBlank()) {
                    throw new IllegalArgumentException("Synapse sequence mediator must define a non-empty 'key'.");
                }
                yield new SequenceMediator(key);
            }
            case CLASS_TAG -> readClass(child);
            // An unsupported mediator is captured verbatim as a to-do rather than dropped. For a
            // control-flow wrapper, its nested mediator sequences are still read so supported children
            // can be converted; all other mediators are opaque leaves.
            default -> new Unsupported(child.getTagName(), serializeElement(child),
                    CONTAINER_MEDIATOR_TAGS.contains(child.getTagName())
                            ? collectNestedMediators(child) : List.of());
        };
    }

    @NotNull
    private static List<SynapseNode> collectNestedMediators(Element wrapper) {
        List<SynapseNode> nested = new ArrayList<>();
        for (Element child : childElements(wrapper)) {
            if (isMediatorSequenceContainer(child)) {
                nested.addAll(collectNestedMediators(child));
            } else {
                nested.add(readMediator(child));
            }
        }
        return nested;
    }

    private static boolean isMediatorSequenceContainer(Element element) {
        if (MEDIATOR_SEQUENCE_TAGS.contains(element.getTagName())) {
            return true;
        }
        return SEQUENCE_TAG.equals(element.getTagName()) && element.getAttribute("key").isBlank();
    }

    @NotNull
    private static Property readProperty(Element element) {
        String name = element.getAttribute("name");

        SynapseType type = SynapseType.from(element.getAttribute("type"));

        String scope = element.getAttribute("scope");
        if (scope.isEmpty()) {
            scope = DEFAULT_PROPERTY_SCOPE;
        }

        String value = element.getAttribute("value");
        String expression = element.getAttribute("expression");

        // A property carrying its value as an inline XML child element (e.g.
        // <property ...><foo>bar</foo></property>) is an OM value regardless of the declared type;
        // capture the serialized child so the converter emits it as an xml literal.
        String omElement = "";
        if (value.isEmpty() && expression.isEmpty()) {
            List<Element> children = childElements(element);
            if (!children.isEmpty()) {
                omElement = serializeElement(children.get(0));
            }
        }

        String action = element.getAttribute("action");
        if (action.isEmpty()) {
            action = DEFAULT_PROPERTY_ACTION;
        }

        return new Property(name, type, scope, value, expression, omElement, action);
    }

    // level and category default to Synapse's own LogMediator defaults (Synapse.DEFAULT_LOG_LEVEL/
    // DEFAULT_LOG_CATEGORY) when the attribute is blank, and are normalized (level lower-cased, category
    // upper-cased) so the converter only needs to reject a genuinely unrecognized value rather than
    // every casing variant. separator uses hasAttribute rather than a blank check: an explicit
    // separator="" is a deliberate "no separator", distinct from the attribute being absent altogether,
    // whereas Synapse has no such distinction for level/category.
    @NotNull
    private static Log readLog(Element element) {
        String level = element.getAttribute("level");
        level = level.isBlank() ? Synapse.DEFAULT_LOG_LEVEL : level.toLowerCase(Locale.ROOT);

        String category = element.getAttribute("category");
        category = category.isBlank() ? Synapse.DEFAULT_LOG_CATEGORY : category.toUpperCase(Locale.ROOT);

        String separator = element.hasAttribute("separator")
                ? unescapeSeparator(element.getAttribute("separator")) : DEFAULT_LOG_SEPARATOR;

        List<Property> properties = new ArrayList<>();
        List<String> unrecognizedChildren = new ArrayList<>();
        for (Element child : childElements(element)) {
            if (PROPERTY_TAG.equals(child.getTagName())) {
                properties.add(readProperty(child));
            } else {
                unrecognizedChildren.add(serializeElement(child));
            }
        }
        return new Log(level, category, separator, properties, unrecognizedChildren);
    }

    // Synapse authors write literal '\n'/'\t' escape sequences in the separator attribute text (e.g.
    // separator="\n"); LogMediator#setSeparator unescapes them to a real newline/tab at runtime, so the
    // reader does the same here.
    private static String unescapeSeparator(String separator) {
        return separator.replace("\\n", "\n").replace("\\t", "\t");
    }

    private static PayloadFactory readPayloadFactory(Element element) {
        String mediaType = element.getAttribute("media-type");
        String format = "";
        for (Element child : childElements(element)) {
            if (FORMAT_TAG.equals(child.getTagName())) {
                format = child.getTextContent().trim();
            }
        }
        return new PayloadFactory(mediaType, format);
    }

    private static String serializeElement(Element element) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(element), new StreamResult(writer));
            return writer.toString().trim();
        } catch (TransformerException e) {
            throw new IllegalStateException("Failed to serialize property element", e);
        }
    }

    private static List<Element> childElements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) node);
            }
        }
        return elements;
    }

    private static ClassMediator readClass(Element element) {
        String className = element.getAttribute("name");
        if (className.isBlank()) {
            throw new IllegalArgumentException("Synapse class mediator must define a non-empty 'name'.");
        }
        List<Property> properties = new ArrayList<>();
        for (Element child : childElements(element)) {
            if (PROPERTY_TAG.equals(child.getTagName())) {
                properties.add(readProperty(child));
            }
        }
        return new ClassMediator(className, properties);
    }
}
