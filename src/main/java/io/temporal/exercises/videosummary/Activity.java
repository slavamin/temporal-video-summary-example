package io.temporal.exercises.videosummary;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface Activity {
    /**
     * Upload to S3.
     * @param jobDetails Job details containing the video URL and S3 configuration
     * @return The S3 key (path) where the file was uploaded
     */
    String uploadToS3(JobDetails jobDetails);

    /**
     * Delete from S3. (Compensating action)
     * @param s3Key The S3 key (path) where the video file was uploaded
     */
    void deleteFromS3(String s3Key);

    /**
     * Transcribe the video.
     * @param jobDetails Job details containing the video URL and S3 configuration
     * @param s3Key The S3 key (path) where the file was uploaded
     * @return The transcription result
     */
    String transcribe(JobDetails jobDetails, String s3Key);

    /**
     * Convert the transcription to the target language using the target language from job details.
     * @param jobDetails Job details containing the video URL and S3 configuration
     * @param originalText The original transcription
     * @return The converted text  
     */
    String convertOriginalTextToTargetLanguage(JobDetails jobDetails, String originalText);

    /**
     * Generate a summary of the video.
     * @param jobDetails Job details containing the video URL and S3 configuration
     * @param targetText The text in the target language
     * @return The summary
     */
    String generateSummary(JobDetails jobDetails, String targetText);
}
