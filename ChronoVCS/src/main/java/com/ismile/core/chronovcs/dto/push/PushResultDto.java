package com.ismile.core.chronovcs.dto.push;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PushResultDto {
    private String branch;
    private String newHeadCommitId;
    private boolean fastForward;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String branch;
        private String newHeadCommitId;
        private boolean fastForward;

        public Builder branch(String branch) {
            this.branch = branch;
            return this;
        }

        public Builder newHeadCommitId(String newHeadCommitId) {
            this.newHeadCommitId = newHeadCommitId;
            return this;
        }

        public Builder fastForward(boolean fastForward) {
            this.fastForward = fastForward;
            return this;
        }

        public PushResultDto build() {
            return new PushResultDto(branch, newHeadCommitId, fastForward);
        }
    }
}