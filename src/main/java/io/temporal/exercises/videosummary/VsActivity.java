package io.temporal.exercises.videosummary;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface VsActivity {
    /**
     * Upload to S3.
     * @param jobDetails Job details containing the video URL and S3 configuration
     * @param results Action results
     */
    void uploadToS3(VsJobDetails jobDetails, VsActionReturnVals results);

    /**
     * Delete from S3. (Compensating action)
     * @param results Action results
     */
    void deleteFromS3(VsActionReturnVals results);

    /**
     * Transcribe the video.
     * @param jobDetails Job details containing the video URL and S3 configuration
     * @param results Action results
     */
    void transcribe(VsJobDetails jobDetails, VsActionReturnVals results);

    /**
     * Convert the transcription to the target language using the target language from job details.
     * @param jobDetails Job details containing the video URL and S3 configuration
     * @param results Action results
     */
    void convertOriginalTextToTargetLanguage(VsJobDetails jobDetails, VsActionReturnVals results);

    /**
     * Generate a summary of the video.
     * @param jobDetails Job details containing the video URL and S3 configuration
     * @param results Action results
     * @return Summary text
     */
    String generateSummary(VsJobDetails jobDetails, VsActionReturnVals results);
}
