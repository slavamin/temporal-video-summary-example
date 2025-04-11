package io.temporal.exercises.videosummary;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface Workflow {
    @WorkflowMethod
    String getVideoSummary(JobDetails jobDetails);
}
