package com.ismile.core.chronovcs.service.storage.impl;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "chronovcs.storage.local")
public class LocalStorageProperties {

    /**
     * Base directory where blobs will be stored on local filesystem.
     * Example: /var/lib/chronovcs/blobs
     */
    private String basePath;
}
