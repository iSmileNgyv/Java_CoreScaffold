package com.ismile.argusomnicli.parser;

import com.ismile.argusomnicli.model.StepType;
import com.ismile.argusomnicli.model.TestSuite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The YAML file is where people make mistakes, so this is where the tool is judged.
 *
 * A misspelled field that is silently ignored is the dangerous case: the suite runs, the
 * assertion never happens, and everything reports green.
 */
class YamlTestParserTest {

    @TempDir
    File dir;

    private YamlTestParser parser;

    @BeforeEach
    void setUp() {
        parser = new YamlTestParser();
    }

    private File file(String name, String content) throws Exception {
        File file = new File(dir, name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));

        return file;
    }

    @Test
    void itReadsASuiteOfSteps() throws Exception {
        TestSuite suite = parser.parse(file("suite.yml", """
                tests:
                  - name: "health"
                    type: REST
                    rest:
                      url: "http://localhost/health"
                      method: GET
                    expect:
                      status: 200
                  - name: "shell"
                    type: BASH
                    bash:
                      command: "echo hi"
                """));

        assertEquals(2, suite.getTests().size());
        assertEquals(StepType.REST, suite.getTests().get(0).getType());
        assertEquals("http://localhost/health", suite.getTests().get(0).getRest().getUrl());
        assertEquals(200, suite.getTests().get(0).getExpect().getStatus());
        assertEquals(StepType.BASH, suite.getTests().get(1).getType());
    }

    @Test
    void anUnknownFieldIsRefusedRatherThanIgnored() throws Exception {
        File file = file("typo.yml", """
                tests:
                  - name: "health"
                    type: REST
                    rest:
                      url: "http://localhost/health"
                      method: GET
                    expect:
                      statuz: 200
                """);

        Exception error = assertThrows(Exception.class, () -> parser.parse(file));

        /*
         * `statuz` silently dropped would mean the response is never checked and the
         * suite passes regardless of what the server returns. The message has to name
         * the field and where it sits.
         */
        assertTrue(error.getMessage().contains("statuz"), error.getMessage());
        assertTrue(error.getMessage().contains("expect"), error.getMessage());
    }

    @Test
    void anUnknownStepTypeIsRefused() throws Exception {
        File file = file("type.yml", """
                tests:
                  - name: "x"
                    type: TELEPATHY
                """);

        assertThrows(Exception.class, () -> parser.parse(file));
    }

    @Test
    void aMissingFileIsReportedClearly() {
        Exception error = assertThrows(Exception.class,
                () -> parser.parse(new File(dir, "does-not-exist.yml")));

        assertNotNull(error.getMessage());
    }

    @Test
    void brokenYamlIsReportedAsSuch() throws Exception {
        File file = file("broken.yml", """
                tests:
                  - name: "x"
                   type: REST
                """);

        assertThrows(Exception.class, () -> parser.parse(file));
    }
}
