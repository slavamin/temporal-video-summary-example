package io.temporal.exercises.videosummary;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.util.Map;

public class WorkflowImpl implements Workflow {
    private final RetryOptions retryoptions = RetryOptions.newBuilder()
            .setInitialInterval(Duration.ofSeconds(1)) // Wait 1 second before first retry
            .setMaximumInterval(Duration.ofSeconds(30)) // Do not exceed 20 seconds between retries
            .setBackoffCoefficient(2) // Wait 1 second, then 2, then 4, etc
            .setMaximumAttempts(10) // Fail after 10 attempts
            .build();

    private final ActivityOptions defaultOptions = ActivityOptions.newBuilder()
            .setRetryOptions(retryoptions) // Apply the RetryOptions defined above
            .setStartToCloseTimeout(Duration.ofMinutes(15)) // Max execution time for single Activity
            .setScheduleToCloseTimeout(Duration.ofMinutes(30)) // Entire duration from scheduling to completion including queue time
            .setHeartbeatTimeout(Duration.ofMinutes(15))
            .build();

    private final ActivityOptions videoProcessingOptions = ActivityOptions.newBuilder()
            .setRetryOptions(retryoptions) // Apply the RetryOptions defined above
            .setStartToCloseTimeout(Duration.ofMinutes(30)) // Max execution time for single Activity
            .setScheduleToCloseTimeout(Duration.ofMinutes(45)) // Entire duration from scheduling to completion including queue time
            .setHeartbeatTimeout(Duration.ofMinutes(30))
            .build();

    private final Map<String, ActivityOptions> perActivityMethodOptions = Map.of(
            "uploadToS3", videoProcessingOptions,
            "transcribe", videoProcessingOptions
    );

    private final Activity activityStub = io.temporal.workflow.Workflow.
            newActivityStub(Activity.class, defaultOptions, perActivityMethodOptions);

    @Override
    public String getVideoSummary(JobDetails jobDetails) {
        final String s3Key;

        try {
            s3Key = activityStub.uploadToS3(jobDetails);
        }
        catch (Exception e) {
            System.out.println("Failed to upload video file to S3 bucket: " + jobDetails);
            return null; // End transaction without compensating action.
        }

        String originalText = null;

        try {
            originalText = activityStub.transcribe(jobDetails, s3Key);
        }
        catch (Exception e) {
            System.out.println("Failed to transcribe the video: " + jobDetails);
        }

        String targetText = null;

        if(originalText != null) {
            try {
                targetText = activityStub.convertOriginalTextToTargetLanguage(jobDetails, originalText);
            }
            catch (Exception e) {
                System.out.println("Failed to translate the original text to target language: " + jobDetails);
            }
        }

        String summary = null;

        if(targetText != null) {
            try {
                 summary = activityStub.generateSummary(jobDetails, targetText);
                 return summary;
            }
            catch (Exception e) {
                System.out.println("Failed to generate summary: " + jobDetails);
            }
        }

        // Take compensating action
        try {
            activityStub.deleteFromS3(s3Key);
            return summary;
        }
        catch (Exception e) {
            System.out.println("Failed to delete uploaded video file from S3 bucket: " + jobDetails);
            throw(e);
        }
    }
}
