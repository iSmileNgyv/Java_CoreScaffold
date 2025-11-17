package com.ismile.core.chronovcscli.core.hash.impl;

import com.ismile.core.chronovcscli.core.hash.HashEngine;
import com.ismile.core.chronovcscli.utils.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

@Component
@Slf4j
public class Sha256HashEngine implements HashEngine {
    private static final String ALGORITHM = "SHA-256";
    @Override
    public String hashBytes(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(data);
            return HashUtils.toHex(hashBytes);
        } catch(Exception e) {
            throw new RuntimeException("Failed to hash byte", e);
        }
    }

    @Override
    public String hashString(String text) {
        return hashBytes(text.getBytes());
    }

    @Override
    public String hashFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] buffer = new byte[8192];
            int read;
            while((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HashUtils.toHex(digest.digest());
        } catch(Exception e) {
            throw new RuntimeException("Failed to hash file", e);
        }
    }
}
