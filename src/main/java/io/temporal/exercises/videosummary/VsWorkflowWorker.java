package io.temporal.exercises.videosummary;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public class VsWorkflowWorker {
    public static void main(String[] args) {
        // Create a stub that accesses a Temporal Service on the local development machine
        WorkflowServiceStubs serviceStub = WorkflowServiceStubs.newLocalServiceStubs();

        // The Worker uses the Client to communicate with the Temporal Service
        WorkflowClient client = WorkflowClient.newInstance(serviceStub);

        // A WorkerFactory creates Workers
        WorkerFactory factory = WorkerFactory.newInstance(client);

        // A Worker listens to one Task Queue.
        // This Worker processes both Workflows and Activities
        Worker worker = factory.newWorker(VsSharedKeys.VIDEO_SUMMARY_TASK_QUEUE);

        // Register a Workflow implementation with this Worker
        // The implementation must be known at runtime to dispatch Workflow tasks
        // Workflows are stateful so a type is needed to create instances.
        worker.registerWorkflowImplementationTypes(VsWorkflowImpl.class);

        // Register Activity implementation(s) with this Worker.
        // The implementation must be known at runtime to dispatch Activity tasks
        // Activities are stateless and thread safe so a shared instance is used.
        worker.registerActivitiesImplementations(new VsActivityImpl());

        System.out.println("Worker is running and actively polling Task Queue: " + VsSharedKeys.VIDEO_SUMMARY_TASK_QUEUE);

        // Start all registered Workers. The Workers will start polling the Task Queue.
        factory.start();
    }
}
