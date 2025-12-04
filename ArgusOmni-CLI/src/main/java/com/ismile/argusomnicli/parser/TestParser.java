package com.ismile.argusomnicli.parser;

import com.ismile.argusomnicli.model.TestSuite;

import java.io.File;

/**
 * Interface Segregation Principle (ISP):
 * Focused interface for test parsing.
 *
 * Single Responsibility Principle (SRP):
 * Only responsible for parsing test definitions.
 */
public interface TestParser {
    /**
     * Parses test definition file into TestSuite model.
     *
     * @param testFile Test definition file
     * @return Parsed test suite
     */
    TestSuite parse(File testFile) throws Exception;
}
