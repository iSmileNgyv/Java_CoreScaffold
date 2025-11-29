package com.ismile.core.chronovcs.service.storage.impl;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "chronovcs.storage")
@Getter
@Setter
public class StorageProperties {

    /**
     * Local filesystem base path, e.g. /var/chronovcs/storage or ./storage
     */
    private String localBasePath;
}