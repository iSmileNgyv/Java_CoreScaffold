package com.ismile.argusomnicli.extractor;

import java.util.Map;

/**
 * Interface Segregation Principle (ISP):
 * Focused interface for extracting data from responses.
 *
 * Single Responsibility Principle (SRP):
 * Only responsible for extracting variables.
 */
public interface ResponseExtractor {
    /**
     * Extracts variables from response based on extraction config.
     *
     * @param response Response object
     * @param extractConfig Extraction configuration (key -> path/expression)
     * @return Extracted variables
     */
    Map<String, Object> extract(Object response, Map<String, String> extractConfig) throws Exception;
}
