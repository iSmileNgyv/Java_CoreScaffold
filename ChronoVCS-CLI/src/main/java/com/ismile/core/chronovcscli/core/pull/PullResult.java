package com.ismile.core.chronovcscli.core.pull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullResult {
    private boolean success;
    private String message;
    private int commitsDownloaded;

    public static PullResult success(String message, int commitsDownloaded) {
        return PullResult.builder()
                .success(true)
                .message(message)
                .commitsDownloaded(commitsDownloaded)
                .build();
    }

    public static PullResult error(String message) {
        return PullResult.builder()
                .success(false)
                .message(message)
                .commitsDownloaded(0)
                .build();
    }
}
