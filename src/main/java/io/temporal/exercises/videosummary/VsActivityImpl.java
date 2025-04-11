package io.temporal.exercises.videosummary;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.transcribe.AmazonTranscribe;
import com.amazonaws.services.transcribe.AmazonTranscribeClientBuilder;
import com.amazonaws.services.transcribe.model.GetTranscriptionJobRequest;
import com.amazonaws.services.transcribe.model.LanguageCode;
import com.amazonaws.services.transcribe.model.Media;
import com.amazonaws.services.transcribe.model.MediaFormat;
import com.amazonaws.services.transcribe.model.StartTranscriptionJobRequest;
import com.amazonaws.services.transcribe.model.TranscriptionJob;
import com.amazonaws.services.translate.AmazonTranslate;
import com.amazonaws.services.translate.AmazonTranslateClientBuilder;
import com.amazonaws.services.translate.model.TranslateTextRequest;
import com.amazonaws.services.translate.model.TranslateTextResult;
import com.amazonaws.services.comprehend.AmazonComprehend;
import com.amazonaws.services.comprehend.AmazonComprehendClientBuilder;
import com.amazonaws.services.comprehend.model.DetectKeyPhrasesRequest;
import com.amazonaws.services.comprehend.model.DetectKeyPhrasesResult;
import com.amazonaws.services.comprehend.model.KeyPhrase;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class VsActivityImpl implements VsActivity {
    // Store the last used JobDetails for use in compensating actions
    private static VsJobDetails lastJobDetails;
    
    @Override
    public void uploadToS3(VsJobDetails jobDetails, VsActionReturnVals results) {
        // Store jobDetails for potential compensation (deletion) later
        lastJobDetails = jobDetails;
        
        try {
            // Configure AWS credentials
            AWSCredentials credentials = new BasicAWSCredentials(
                    jobDetails.apiKey(),
                    jobDetails.apiSecret()
            );
            
            // Create S3 client
            AmazonS3 s3Client = AmazonS3ClientBuilder
                    .standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withRegion(Regions.fromName(jobDetails.region()))
                    .build();
            
            // Download the video from the URL
            URL url = new URL(jobDetails.videoUrl());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Create a temp file to store the downloaded video
            String fileExtension = getFileExtension(jobDetails.videoUrl());
            Path tempFile = Files.createTempFile("video-", fileExtension);
            
            // Download the file
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(tempFile.toFile())) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            
            // Generate a unique key for the S3 object
            String s3Key = "videos/" + UUID.randomUUID() + fileExtension;
            
            // Upload the file to S3
            s3Client.putObject(
                    jobDetails.bucketName(),
                    s3Key,
                    tempFile.toFile()
            );
            
            // Clean up the temp file
            Files.delete(tempFile);
            
            System.out.println("Successfully uploaded video to S3 bucket " + 
                    jobDetails.bucketName() + " with key " + s3Key);
            
            results.setS3Key(s3Key);
            
        } catch (IOException e) {
            throw io.temporal.activity.Activity.wrap(new RuntimeException("Failed to upload video to S3: " + e.getMessage(), e));
        }
    }

    @Override
    public void deleteFromS3(VsActionReturnVals results) {
        try {
            // Use the last used JobDetails for credentials
            if (lastJobDetails == null) {
                throw new IllegalStateException("No JobDetails available. Make sure this method is called after uploadToS3.");
            }
            
            // Configure AWS credentials
            AWSCredentials credentials = new BasicAWSCredentials(
                    lastJobDetails.apiKey(),
                    lastJobDetails.apiSecret()
            );
            
            // Create S3 client
            AmazonS3 s3Client = AmazonS3ClientBuilder
                    .standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withRegion(Regions.fromName(lastJobDetails.region()))
                    .build();
            
            // Delete the object from S3
            s3Client.deleteObject(lastJobDetails.bucketName(), results.getS3Key());
            
            System.out.println("Successfully deleted object from S3 bucket " + 
                    lastJobDetails.bucketName() + " with key " + results.getS3Key());
            
        } catch (Exception e) {
            throw io.temporal.activity.Activity.wrap(new RuntimeException("Failed to delete object from S3: " + e.getMessage(), e));
        }
    }

    @Override
    public void transcribe(VsJobDetails jobDetails, VsActionReturnVals results) {
        try {
            if(results.getS3Key() == null) {
                return;
            }

            // Configure AWS credentials
            AWSCredentials credentials = new BasicAWSCredentials(
                    jobDetails.apiKey(),
                    jobDetails.apiSecret()
            );
            
            // Create S3 client to get the input file URL
            AmazonS3 s3Client = AmazonS3ClientBuilder
                    .standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withRegion(Regions.fromName(jobDetails.region()))
                    .build();
            
            // Generate a pre-signed URL for the video file in S3
            java.util.Date expiration = new java.util.Date();
            long expTimeMillis = expiration.getTime();
            expTimeMillis += 1000 * 60 * 60; // Add 1 hour
            expiration.setTime(expTimeMillis);
            
            String mediaUrl = s3Client.generatePresignedUrl(
                    jobDetails.bucketName(),
                    results.getS3Key(),
                    expiration
            ).toString();
            
            // Create Transcribe client
            AmazonTranscribe transcribeClient = AmazonTranscribeClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withRegion(Regions.fromName(jobDetails.region()))
                    .build();
            
            // Create unique job name
            String jobName = "transcription-" + UUID.randomUUID().toString();
            String outputKey = "transcriptions/" + jobName + ".json";
            String outputBucket = jobDetails.bucketName();
            
            // Set up media format based on file extension
            String format = "mp4";
            if (results.getS3Key().toLowerCase().endsWith(".mp3")) {
                format = "mp3";
            } else if (results.getS3Key().toLowerCase().endsWith(".wav")) {
                format = "wav";
            }
            
            // Start transcription job
            StartTranscriptionJobRequest startJobRequest = new StartTranscriptionJobRequest()
                    .withTranscriptionJobName(jobName)
                    .withLanguageCode(LanguageCode.fromValue(jobDetails.orgLanguage()))
                    .withMediaFormat(MediaFormat.fromValue(format))
                    .withMedia(new Media().withMediaFileUri(mediaUrl))
                    .withOutputBucketName(outputBucket)
                    .withOutputKey(outputKey);
            
            transcribeClient.startTranscriptionJob(startJobRequest);
            
            // Wait for completion
            GetTranscriptionJobRequest getJobRequest = new GetTranscriptionJobRequest()
                    .withTranscriptionJobName(jobName);
            
            TranscriptionJob transcriptionJob;
            do {
                Thread.sleep(10000); // Check every 10 seconds
                transcriptionJob = transcribeClient.getTranscriptionJob(getJobRequest)
                        .getTranscriptionJob();
                
                System.out.println("Transcription job status: " + transcriptionJob.getTranscriptionJobStatus());
                
            } while (transcriptionJob.getTranscriptionJobStatus().equals("IN_PROGRESS"));
            
            if (!transcriptionJob.getTranscriptionJobStatus().equals("COMPLETED")) {
                throw new RuntimeException("Transcription job failed with status: " 
                        + transcriptionJob.getTranscriptionJobStatus());
            }
            
            // Get the transcription result
            String transcriptFileUrl = transcriptionJob.getTranscript().getTranscriptFileUri();
            
            // Download the JSON result
            URL url = new URL(transcriptFileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            StringBuilder resultBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    resultBuilder.append(line);
                }
            }
            
            // Parse the JSON to extract the actual transcription text
            String jsonResult = resultBuilder.toString();
            // Using simple string parsing instead of full JSON parsing for brevity
            // In a real implementation, you would use a JSON library
            int transcriptStart = jsonResult.indexOf("\"transcript\":\"") + 14;
            int transcriptEnd = jsonResult.indexOf("\"", transcriptStart);

            // Clean up by deleting the transcription result from S3 if needed
            // s3Client.deleteObject(outputBucket, outputKey);
            results.setOriginalText(jsonResult.substring(transcriptStart, transcriptEnd));
        } catch (Exception e) {
            throw io.temporal.activity.Activity.wrap(new RuntimeException("Failed to transcribe video: " + e.getMessage(), e));
        }
    }

    @Override
    public void convertOriginalTextToTargetLanguage(VsJobDetails jobDetails, VsActionReturnVals results) {
        try {
            if(results.getOriginalText() == null) {
                return;
            }

            // Configure AWS credentials
            AWSCredentials credentials = new BasicAWSCredentials(
                    jobDetails.apiKey(),
                    jobDetails.apiSecret()
            );
            
            // Create Amazon Translate client
            AmazonTranslate translateClient = AmazonTranslateClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withRegion(Regions.fromName(jobDetails.region()))
                    .build();
            
            // Get source language from jobDetails
            String sourceLanguage = jobDetails.orgLanguage();
            
            // Create translation request
            TranslateTextRequest translateRequest = new TranslateTextRequest()
                    .withText(results.getOriginalText())
                    .withSourceLanguageCode(sourceLanguage)
                    .withTargetLanguageCode(jobDetails.targetLanguage());
            
            // Perform translation
            TranslateTextResult translateResult = translateClient.translateText(translateRequest);
            
            System.out.println("Successfully translated text from " + sourceLanguage + 
                    " to " + jobDetails.targetLanguage());
            
            // Return the translated text
            results.setTargetText(translateResult.getTranslatedText());
            
        } catch (Exception e) {
            throw io.temporal.activity.Activity.wrap(new RuntimeException("Failed to translate text: " + e.getMessage(), e));
        }
    }

    @Override
    public String generateSummary(VsJobDetails jobDetails, VsActionReturnVals results) {
        try {
            // Configure AWS credentials
            AWSCredentials credentials = new BasicAWSCredentials(
                    jobDetails.apiKey(),
                    jobDetails.apiSecret()
            );
            
            // Create Amazon Comprehend client
            AmazonComprehend comprehendClient = AmazonComprehendClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withRegion(Regions.fromName(jobDetails.region()))
                    .build();
            
            // Get the language code for comprehend
            String languageCode = jobDetails.targetLanguage();
            
            // AWS Comprehend supports a limited set of languages for key phrases
            // If the target language is not supported, fall back to English
            if (!isLanguageSupportedForKeyPhrases(languageCode)) {
                System.out.println("Language " + languageCode + " not supported for key phrases, using English");
                languageCode = "en";
            }
            
            // Create the key phrases request
            DetectKeyPhrasesRequest keyPhrasesRequest = new DetectKeyPhrasesRequest()
                    .withText(results.getTargetText())
                    .withLanguageCode(languageCode);
            
            // Detect key phrases
            DetectKeyPhrasesResult keyPhrasesResult = comprehendClient.detectKeyPhrases(keyPhrasesRequest);
            
            // Build a summary using the key phrases
            StringBuilder summaryBuilder = new StringBuilder("Summary:\n");
            
            // Sort key phrases by score (most important first)
            List<KeyPhrase> sortedPhrases = new ArrayList<>(keyPhrasesResult.getKeyPhrases());
            sortedPhrases.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
            
            // Use the top phrases to create the summary (limit to top 10 or less)
            int phrasesToUse = Math.min(10, sortedPhrases.size());
            for (int i = 0; i < phrasesToUse; i++) {
                KeyPhrase phrase = sortedPhrases.get(i);
                summaryBuilder.append("- ").append(phrase.getText()).append("\n");
            }
            
            System.out.println("Successfully generated summary with " + phrasesToUse + " key phrases");
            
            return summaryBuilder.toString();
            
        } catch (Exception e) {
            throw io.temporal.activity.Activity.wrap(new RuntimeException("Failed to generate summary: " + e.getMessage(), e));
        }
    }

    ////////////////////////////
    //     Private stuff      //
    ////////////////////////////

    private String getFileExtension(String url) {
        // Extract file extension from URL
        int questionMarkIndex = url.indexOf('?');
        String cleanUrl = questionMarkIndex > 0 ? url.substring(0, questionMarkIndex) : url;
        int lastDotIndex = cleanUrl.lastIndexOf('.');

        if (lastDotIndex > 0) {
            return cleanUrl.substring(lastDotIndex);
        }

        // Default extension if none found
        return ".mp4";
    }

    private boolean isLanguageSupportedForKeyPhrases(String languageCode) {
        // List of languages supported by AWS Comprehend for key phrases detection
        return switch (languageCode) { // English
            // Spanish
            // French
            // German
            // Italian
            // Portuguese
            // Arabic
            // Hindi
            // Japanese
            // Korean
            // Chinese
            case "en", "es", "fr", "de", "it", "pt", "ar", "hi", "ja", "ko", "zh", "zh-TW" -> // Chinese (Traditional)
                    true;
            default -> false;
        };
    }
}
