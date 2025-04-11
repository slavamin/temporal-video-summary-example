package io.temporal.exercises.videosummary;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = VsJobDetails.class)
public record VsJobDetails(
      String videoUrl, // URL of the file to translate
      String orgLanguage, // Original language
      String targetLanguage, // Language to translate to
      String apiKey, // AWS API key
      String apiSecret, // AWS API secret
      String region, // AWS region, e.g. "us-east-1";
      String bucketName // S3 bucket name
) {
    @Override
    public String toString() {
        return String.format("JobDetails{videoUrl='%s', orgLanguage=%s, targetLanguage=%s, region=%s, bucketName=%s}",
                videoUrl, orgLanguage, targetLanguage, region, bucketName);
    }
}