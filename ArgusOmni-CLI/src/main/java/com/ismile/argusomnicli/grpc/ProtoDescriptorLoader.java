package com.ismile.argusomnicli.grpc;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;

/**
 * Loads proto descriptors from .proto files or descriptor sets.
 * Follows Single Responsibility - only loads proto descriptors.
 */
@Component
public class ProtoDescriptorLoader {

    /**
     * Load proto descriptor from file.
     * Supports both .proto and .pb (descriptor set) files.
     *
     * @param path Path to proto file
     * @return File descriptor
     */
    public Descriptors.FileDescriptor load(String path) throws Exception {
        File file = new File(path);

        if (!file.exists()) {
            throw new IllegalArgumentException("Proto file not found: " + path);
        }

        if (path.endsWith(".pb")) {
            return loadDescriptorSet(file);
        } else if (path.endsWith(".proto")) {
            return loadProtoFile(file);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + path);
        }
    }

    private Descriptors.FileDescriptor loadDescriptorSet(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            DescriptorProtos.FileDescriptorSet descriptorSet =
                    DescriptorProtos.FileDescriptorSet.parseFrom(input);

            if (descriptorSet.getFileCount() == 0) {
                throw new IllegalArgumentException("Empty descriptor set");
            }

            // Build file descriptor
            DescriptorProtos.FileDescriptorProto fileProto = descriptorSet.getFile(0);
            return Descriptors.FileDescriptor.buildFrom(fileProto, new Descriptors.FileDescriptor[0]);
        }
    }

    private Descriptors.FileDescriptor loadProtoFile(File file) throws Exception {
        // TODO: Implement proto file parsing
        // This requires proto compiler integration
        // For MVP, we can require descriptor sets (.pb files)
        throw new UnsupportedOperationException(
                "Direct .proto parsing not yet supported. Please provide .pb descriptor set file.");
    }
}
