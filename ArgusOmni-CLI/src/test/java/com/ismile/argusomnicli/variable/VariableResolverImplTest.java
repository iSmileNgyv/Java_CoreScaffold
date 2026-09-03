package com.ismile.argusomnicli.variable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every step's input passes through here: urls, request bodies, shell commands.
 *
 * A resolver that quietly produces the wrong text is the worst kind of failure in a test
 * runner — the suite reports success while having asserted against something other than
 * what was meant.
 */
class VariableResolverImplTest {

    private VariableResolverImpl resolver;
    private VariableContext context;

    @BeforeEach
    void setUp() {
        resolver = new VariableResolverImpl(new BuiltInFunctions());
        context = new VariableContext();
    }

    @Test
    void itSubstitutesValuesIntoATemplate() {
        context.set("host", "localhost");
        context.set("port", 8081);

        assertEquals("http://localhost:8081/health",
                resolver.resolve("http://{{host}}:{{port}}/health", context));
    }

    @Test
    void anUnknownNameBecomesEmptyRatherThanStayingLiteral() {
        /*
         * Leaving `{{missing}}` in place would send the braces to the server and the
         * failure would be blamed on the request. An empty value is wrong too, but it is
         * visibly wrong at the point of use.
         */
        assertEquals("value=", resolver.resolve("value={{missing}}", context));
    }

    @Test
    void aValueContainingDollarOrBackslashIsInsertedLiterally() {
        // Regex replacement treats $ and \ specially; a password would be mangled.
        context.set("secret", "a$1b\\c");

        assertEquals("pw=a$1b\\c", resolver.resolve("pw={{secret}}", context));
    }

    @Test
    void nestedPropertiesAreReadFromMapsAndObjects() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "Baku");
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("address", address);
        context.set("user", user);

        assertEquals("Baku", resolver.resolve("{{user.address.city}}", context));
        // A missing branch yields empty, not an exception mid-suite.
        assertEquals("", resolver.resolve("{{user.address.street}}", context));
    }

    @Test
    void builtInFunctionsRunAndTheirArgumentMayBeAVariable() {
        context.set("raw", "a b");

        /*
         * Form encoding, so a space becomes `+`. That is right for a query value and
         * wrong inside a path segment, where `+` means a literal plus — worth knowing
         * before using this in a url path.
         */
        assertEquals("a+b", resolver.resolve("{{url_encode:raw}}", context));
        // A literal argument works as well as a variable name.
        assertEquals("YQ==", resolver.resolve("{{base64:a}}", context));

        String uuid = resolver.resolve("{{uuid:x}}", context);
        assertEquals(36, uuid.length());
    }

    @Test
    void nullAndPlainTextPassThroughUnchanged() {
        assertNull(resolver.resolve(null, context));
        assertEquals("no variables here", resolver.resolve("no variables here", context));
    }

    @Test
    void structuresAreResolvedThroughout() {
        context.set("id", "42");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("path", "/users/{{id}}");
        body.put("tags", List.of("user-{{id}}", "static"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resolved = (Map<String, Object>) resolver.resolveObject(body, context);

        assertEquals("/users/42", resolved.get("path"));
        assertEquals(List.of("user-42", "static"), resolved.get("tags"));
    }
}
