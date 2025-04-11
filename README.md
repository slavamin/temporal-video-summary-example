# Temporal Video Summary Application

## Overview

This example demonstrates a video summary application built using Temporal for workflow orchestration and AWS services for video processing. The application takes a video URL, processes it to extract text, translates the text, and generates a summary.

## Architecture

The application consists of two main executables that interact with the Temporal server:

1. **VsApp** (Client Executable): Connects to the Temporal server and can send commands such as Start workflow, GetStatus, and Cancel. It initiates the workflow by providing job details with configuration parameters.

2. **VsWorkflowWorker** (Worker Executable): Connects to the Temporal server and subscribes to the `VIDEO_SUMMARY_TASK_QUEUE`. It registers workflow and activity implementations and processes tasks dispatched by the server.

## Workflow Process

The video summary workflow follows these steps:

1. **Upload to S3**: The video file is uploaded to Amazon S3 storage for processing
2. **Transcribe Video**: The uploaded video is transcribed to text using Amazon Transcribe
3. **Translate Text**: The transcribed text is translated to the target language using Amazon Translate
4. **Generate Summary**: A summary is created from the translated text using Amazon Comprehend

If any step fails after the upload, a compensating action is taken to delete the uploaded file from S3.

## Fault Tolerance

The application includes several fault-tolerance features:

- **Retry Options**: Automatic retry of failed activities with exponential backoff
- **Activity Timeouts**: Different timeouts for standard vs. video processing activities
- **Compensating Actions**: Cleanup of resources if steps fail after S3 upload
- **State Persistence**: Temporal maintains workflow state, allowing recovery from worker failures

## Diagrams

The repository includes two HTML diagrams that visualize the application architecture:

- **[workflow-diagram.html](workflow-diagram.html)**: Illustrates the workflow steps and error handling for the video summary process
- **[component-diagram.html](component-diagram.html)**: Shows the component architecture, relationships between different parts of the system, and interactions with AWS services

Open these HTML files in a browser to view the diagrams.

## Implementation

The application is implemented using the following key components:

- **VsWorkflow/VsWorkflowImpl**: Defines and implements the workflow interface
- **VsActivity/VsActivityImpl**: Defines and implements the activities that perform the actual work
- **VsJobDetails**: Configuration record containing parameters like video URL, languages, and AWS credentials

The implementation leverages Temporal's workflow and activity patterns for reliable execution, with proper error handling and compensating actions.
