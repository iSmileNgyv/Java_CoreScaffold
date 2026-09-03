package com.ismile.argusomnicli.executor;

import com.ismile.argusomnicli.model.BashConfig;
import com.ismile.argusomnicli.model.StepType;
import com.ismile.argusomnicli.model.TestStep;
import com.ismile.argusomnicli.runner.ExecutionContext;
import com.ismile.argusomnicli.variable.BuiltInFunctions;
import com.ismile.argusomnicli.variable.VariableContext;
import com.ismile.argusomnicli.variable.VariableResolverImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A bash step publishes values for the steps that follow it.
 *
 * A test suite is a chain: prepare something, read what it produced, use it next. If an
 * extracted value never reaches the shared context, `{{name}}` resolves to nothing and
 * the command runs with a gap where the value should be — without failing. The suite
 * still reports success, which is worse than an error.
 */
class BashExecutorTest {

    private BashExecutor executor;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        executor = new BashExecutor(new VariableResolverImpl(new BuiltInFunctions()));
        context = new ExecutionContext(new VariableContext(), false);
    }

    private TestStep step(String command, Map<String, String> extract) {
        BashConfig config = new BashConfig();
        config.setCommand(command);

        TestStep step = new TestStep();
        step.setName("step");
        step.setType(StepType.BASH);
        step.setBash(config);
        step.setExtract(extract);

        return step;
    }

    @Test
    void anExtractedValueIsVisibleToLaterSteps() throws Exception {
        Map<String, String> extract = new LinkedHashMap<>();
        extract.put("greeting", "all");

        ExecutionResult first = executor.execute(step("echo hello-world", extract), context);

        assertTrue(first.isSuccess());
        assertEquals("hello-world", first.getExtractedVariables().get("greeting"));
        // The report is not enough: the next step reads from the context.
        assertEquals("hello-world", context.getVariable("greeting"));

        ExecutionResult second = executor.execute(step("echo got-{{greeting}}", extract), context);

        assertEquals("got-hello-world", second.getExtractedVariables().get("greeting"));
    }

    @Test
    void extractionModesReadTheRightPartOfTheOutput() throws Exception {
        Map<String, String> all = Map.of("value", "all");
        Map<String, String> last = Map.of("value", "last");
        Map<String, String> second = Map.of("value", "line:1");
        Map<String, String> regex = Map.of("value", "version=([0-9.]+)");

        assertEquals("one\ntwo\nthree",
                executor.execute(step("printf 'one\\ntwo\\nthree\\n'", all), context)
                        .getExtractedVariables().get("value"));

        assertEquals("three",
                executor.execute(step("printf 'one\\ntwo\\nthree\\n'", last), context)
                        .getExtractedVariables().get("value"));

        assertEquals("two",
                executor.execute(step("printf 'one\\ntwo\\nthree\\n'", second), context)
                        .getExtractedVariables().get("value"));

        // A capturing group returns the group, not the whole match.
        assertEquals("1.4.2",
                executor.execute(step("echo version=1.4.2", regex), context)
                        .getExtractedVariables().get("value"));
    }

    @Test
    void aFailingCommandIsReportedAsAFailure() throws Exception {
        ExecutionResult result = executor.execute(step("exit 3", Map.of()), context);

        assertFalse(result.isSuccess());
        assertEquals(3, result.getStatusCode());
    }

    @Test
    void anExpectedNonZeroExitCodeCountsAsSuccess() throws Exception {
        BashConfig config = new BashConfig();
        config.setCommand("exit 3");
        config.setExpectedExitCode(3);

        TestStep step = new TestStep();
        step.setName("step");
        step.setType(StepType.BASH);
        step.setBash(config);

        // Testing that something fails is a normal thing to want.
        assertTrue(executor.execute(step, context).isSuccess());
    }

    @Test
    void theExecutorOnlyClaimsBashSteps() {
        TestStep bash = step("echo x", Map.of());
        assertTrue(executor.supports(bash));

        TestStep rest = new TestStep();
        rest.setType(StepType.REST);
        assertFalse(executor.supports(rest));

        // A bash step with no configuration is not runnable.
        TestStep empty = new TestStep();
        empty.setType(StepType.BASH);
        assertFalse(executor.supports(empty));
    }
}
