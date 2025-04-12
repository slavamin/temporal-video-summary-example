# VsWorkflowImpl Workflow Diagram

A visual representation of the Temporal video summary workflow implementation

## Workflow Process

```mermaid
%%{init: {'theme': 'default', 'flowchart': {'useMaxWidth': false, 'htmlLabels': true, 'curve': 'cardinal', 'nodeSpacing': 70, 'rankSpacing': 90}, 'themeVariables': {'fontSize': '16px', 'fontFamily': 'arial', 'primaryColor': '#193566', 'primaryTextColor': '#fff'}, 'securityLevel': 'loose'} }%%
%%{config: { 'width': 650, 'height': 700 } }%%
graph TB
    A([Start]) --> B[Upload to S3]
    B -->|Success| C[Transcribe Video]
    B -->|Failure| D["Return null (End Workflow)"]
    
    C -->|Success| E[Convert Text to Target Language]
    C -->|Failure| F["Delete from S3 (Compensating Action)"]
    
    E -->|Success| G[Generate Summary]
    E -->|Failure| F
    
    G -->|Success| H["Return Summary (End Workflow)"]
    G -->|Failure| F
    
    F --> I["Return null (End Workflow)"]
    
    classDef default fill:#f9f9f9,stroke:#333,stroke-width:1px;
    classDef success fill:#d4edda,stroke:#28a745,stroke-width:2px;
    classDef error fill:#f8d7da,stroke:#dc3545,stroke-width:2px;
    classDef action fill:#e6f3ff,stroke:#0275d8,stroke-width:2px;
    classDef start fill:#193566,color:#fff,stroke:#193566,stroke-width:2px;
    
    class A start;
    class B,C,E,G action;
    class D,F,I error;
    class H success;
```

## Workflow Configuration

### Retry Options
- **Initial Interval:** 1 second
- **Maximum Interval:** 30 seconds
- **Backoff Coefficient:** 2 (exponential backoff)
- **Maximum Attempts:** 10

### Default Activity Options
- **Start to Close Timeout:** 15 minutes
- **Schedule to Close Timeout:** 30 minutes
- **Heartbeat Timeout:** 15 minutes

### Video Processing Options
- **Start to Close Timeout:** 30 minutes
- **Schedule to Close Timeout:** 45 minutes
- **Heartbeat Timeout:** 30 minutes
- **Applied to:** uploadToS3, transcribe

## Implementation Details

- **Implementation Class:** `VsWorkflowImpl`
- **Interface:** `VsWorkflow`
- **Main Method:** `getVideoSummary(VsJobDetails jobDetails)`

## Workflow Steps

### 1. Upload to S3
Uploads the video file to Amazon S3 storage for processing.

**Error Handling:**
If upload fails, the workflow ends and returns null without compensating action.

### 2. Transcribe Video
Transcribes the uploaded video to text using the configured transcription service.

**Error Handling:**
If transcription fails, the workflow attempts to delete the uploaded file from S3 as a compensating action.

### 3. Convert Text to Target Language
Translates the transcribed text to the target language specified in the job details.

**Error Handling:**
If translation fails, the workflow attempts to delete the uploaded file from S3 as a compensating action.

### 4. Generate Summary
Creates a concise summary of the translated text.

**Error Handling:**
If summary generation fails, the workflow attempts to delete the uploaded file from S3 as a compensating action.

### 5. Compensating Action (if needed)
Deletes the video file from S3 storage if any step after upload fails.

**Error Handling:**
If deletion from S3 fails, an exception is thrown.
