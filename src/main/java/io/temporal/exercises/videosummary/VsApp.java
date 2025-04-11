package io.temporal.exercises.videosummary;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;

import java.util.concurrent.CompletableFuture;

public class VsApp {
    public static void main(String[] args) throws Exception {
        // A WorkflowServiceStubs communicates with the Temporal front-end service.
        WorkflowServiceStubs serviceStub = WorkflowServiceStubs.newLocalServiceStubs();

        // A WorkflowClient wraps the stub.
        // It can be used to start, signal, query, cancel, and terminate Workflows.
        WorkflowClient client = WorkflowClient.newInstance(serviceStub);

        // Workflow options configure  Workflow stubs.
        // A WorkflowId prevents duplicate instances, which are removed.
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(VsSharedKeys.VIDEO_SUMMARY_TASK_QUEUE)
                .setWorkflowId("video-summary-workflow")
                .build();

        // WorkflowStubs enable calls to methods as if the Workflow object is local
        // but actually perform a gRPC call to the Temporal Service.
        VsWorkflow workflow = client.newWorkflowStub(VsWorkflow.class, options);

        String videoUrl = "File URL";
        String orgLanguage = "En";
        String targetLanguage = "Fr";
        String apiKey = "API KEY";
        String apiSecret = "API SECRET";
        String region = "us-east-1";
        String bucketName = "Bucket name";

        VsJobDetails jobDetails = new VsJobDetails(videoUrl, orgLanguage, targetLanguage, apiKey, apiSecret, region, bucketName);

        // Sync call
        // String result = workflow.getVideoSummary(jobDetails);
        // System.out.println(result);

        // Async execution
        WorkflowExecution we = WorkflowClient.start(workflow::getVideoSummary, jobDetails);
        System.out.println(String.format("Workflow completed successfully: [WorkflowID: %s][RunID: %s]",
                we.getWorkflowId(), we.getRunId()));

        // Recreate stub from execution ID
        workflow = client.newWorkflowStub(VsWorkflow.class, we.getWorkflowId());

        // Convert to untyped stub and get future result
        WorkflowStub untypedStub = WorkflowStub.fromTyped(workflow);
        CompletableFuture<String> resultFuture = untypedStub.getResultAsync(String.class);

        // Add callback
        resultFuture.whenComplete((result, ex) -> {
            if (ex != null) {
                System.out.println("Workflow failed: " + ex.getMessage());
            } else {
                System.out.println("Workflow completed with result: " + result);
            }
        });
    }
}
