# Video Summary Application - Component Diagram

Architecture overview of the Temporal video summary application

## Component Architecture

```mermaid
%%{init: {'theme': 'default', 'flowchart': {'useMaxWidth': false, 'htmlLabels': true, 'curve': 'basis', 'nodeSpacing': 15, 'rankSpacing': 20}, 'themeVariables': {'fontSize': '10px', 'fontFamily': 'arial'}, 'securityLevel': 'loose'} }%%
flowchart TB
    %% Define nodes with clear IDs
    clientApp[VsApp<br>Client Executable]
    temporalServer[Temporal Server]
    taskQueue[VIDEO_SUMMARY_TASK_QUEUE]
    worker[VsWorkflowWorker<br>Worker Executable]
    
    %% Interfaces and implementations
    workflowIf[VsWorkflow<br>Interface]
    workflowImpl[VsWorkflowImpl]
    activityIf[VsActivity<br>Interface]
    activityImpl[VsActivityImpl]
    
    %% Model
    jobDetails[VsJobDetails<br>Configuration]
    
    %% AWS Services
    s3[Amazon S3]
    transcribe[Amazon Transcribe]
    translate[Amazon Translate]
    comprehend[Amazon Comprehend]
    
    %% Clear client-server relationships
    clientApp -->|1. Connect| temporalServer
    clientApp -->|2. Start workflow<br>GetStatus<br>Cancel| temporalServer
    temporalServer -->|3. Dispatch tasks| taskQueue
    worker -.->|4. Subscribe to| taskQueue
    worker -->|5. Send/Receive events| temporalServer
    
    %% Worker registration (cleaner paths)
    worker -->|registers| workflowImpl
    worker -->|registers| activityImpl
    
    %% Interface Implementation (well-aligned arrows)
    workflowImpl -->|implements| workflowIf
    activityImpl -->|implements| activityIf
    
    %% Core relationships (straightened for clarity)
    workflowImpl -->|calls| activityIf
    
    %% Data relationships (better positioned)
    clientApp -->|creates| jobDetails
    workflowImpl -.->|uses| jobDetails
    activityImpl -.->|uses| jobDetails
    
    %% AWS service interactions (spread out for readability)
    activityImpl -->|uploadToS3<br>deleteFromS3| s3
    activityImpl -->|transcribe| transcribe
    activityImpl -->|convertToTargetLang| translate
    activityImpl -->|generateSummary| comprehend
    
    %% Workflow sequence
    subgraph workflowSteps[Workflow Execution Sequence]
        direction TB
        step1[Upload to S3] --> step2[Transcribe Video]
        step2 --> step3[Translate Text]
        step3 --> step4[Generate Summary]
    end
    
    %% Connect workflow to the sequence
    workflowImpl -.->|orchestrates via<br>activity calls| workflowSteps
    
    %% Styling for better visuals
    classDef client fill:#e6f3ff,stroke:#4e73df,stroke-width:2px
    classDef server fill:#f3e6ff,stroke:#7B60E0,stroke-width:2px
    classDef queue fill:#ffe6e6,stroke:#e83e8c,stroke-width:2px,stroke-dasharray:5,5
    classDef interface fill:#f9f9f9,stroke:#333,stroke-width:1px,stroke-dasharray:5,5
    classDef implementation fill:#f9f9f9,stroke:#333,stroke-width:2px
    classDef aws fill:#fff9e6,stroke:#FF9900,stroke-width:2px
    classDef model fill:#e6ffe6,stroke:#28a745,stroke-width:2px
    classDef steps fill:#f8f9fa,stroke:#333,stroke-width:1px
    
    %% Apply styles
    class clientApp,worker client
    class temporalServer server
    class taskQueue queue
    class workflowIf,activityIf interface
    class workflowImpl,activityImpl implementation
    class s3,transcribe,translate,comprehend aws
    class jobDetails model
    class workflowSteps,step1,step2,step3,step4 steps
```

## Component Overview

### Executables
- **VsApp**: Client executable that connects to the Temporal server and can send commands such as Start workflow, Cancel, GetStatus, etc.
- **VsWorkflowWorker**: Worker executable that connects to the Temporal server and subscribes to `VIDEO_SUMMARY_TASK_QUEUE`, sending and receiving events to orchestrate workflow execution

### Temporal Components
- **Temporal Server**: Orchestration engine that manages workflow execution, task queues, and state
- **Task Queue**: `VIDEO_SUMMARY_TASK_QUEUE` - Communication channel between the Temporal server and workers

### Workflow Components
- **VsWorkflow**: Interface defining the workflow contract with `getVideoSummary` method
- **VsWorkflowImpl**: Implementation that orchestrates the video processing activities with error handling and compensating actions

### Activity Components
- **VsActivity**: Interface defining the available activities
- **VsActivityImpl**: Implementation that performs the actual video processing work
- **Activities**:
  - `uploadToS3`: Uploads video file to S3 storage
  - `transcribe`: Transcribes video to text
  - `convertOriginalTextToTargetLanguage`: Translates text
  - `generateSummary`: Creates a summary from the translated text
  - `deleteFromS3`: Compensating action to remove files if needed

### Data Models
- **VsJobDetails**: Configuration record that contains:
  - Video URL
  - Original language
  - Target language
  - AWS credentials and configuration
- **VsSharedKeys**: Contains shared constants including task queue names

### External Services
- **Amazon S3**: Storage for video files and transcription results
- **Amazon Transcribe**: Speech-to-text service for video transcription
- **Amazon Translate**: Translation service for converting text between languages
- **Amazon Comprehend**: Text analysis service used for generating summaries

## Key Interactions

### 1. Client-Server Communication
- Client executable (`VsApp`) connects to the Temporal server
- Client sends commands like Start workflow, GetStatus, or Cancel to the Temporal server
- Client creates the `VsJobDetails` with configuration parameters for the workflow

### 2. Worker-Server Communication
- Worker executable (`VsWorkflowWorker`) connects to the Temporal server
- Worker subscribes to `VIDEO_SUMMARY_TASK_QUEUE`
- Worker sends and receives events to/from the Temporal server
- These events orchestrate the execution of activities in the workflow

### 3. Workflow Execution
- Workflow implementation (`VsWorkflowImpl`) controls the execution flow
- Activity implementation (`VsActivityImpl`) performs the actual work:
  - Uploading the video to S3
  - Transcribing the video using Amazon Transcribe
  - Translating the text using Amazon Translate
  - Generating a summary using Amazon Comprehend
  - Performing compensating actions (deleting from S3) if needed

## Fault-Tolerance Features
- **Retry Options**: Configures automatic retry of failed activities with exponential backoff
- **Activity Timeouts**: Different timeouts for standard vs. video processing activities
- **Compensating Actions**: If steps fail after S3 upload, the application tries to clean up by deleting the files
- **State Persistence**: Temporal maintains workflow state, allowing recovery from worker failures
