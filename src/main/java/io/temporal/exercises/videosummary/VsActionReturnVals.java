package io.temporal.exercises.videosummary;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = VsActionReturnVals.class)
public class VsActionReturnVals {
    private String s3Key;

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getTargetText() {
        return targetText;
    }

    public void setTargetText(String targetText) {
        this.targetText = targetText;
    }

    private String originalText;
    private String targetText;
}
