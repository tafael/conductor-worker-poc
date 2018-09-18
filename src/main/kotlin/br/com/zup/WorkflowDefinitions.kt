package br.com.zup

import com.netflix.conductor.client.http.MetadataClient
import com.netflix.conductor.client.http.WorkflowClient
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import org.springframework.stereotype.Service
import java.util.*
import javax.annotation.PostConstruct

@Service
open class WorkflowDefinitions(
        private val metadataClient: MetadataClient,
        private val workflowClient: WorkflowClient
) {

    @PostConstruct
    private fun init() {
        // register workflow definition
        val workflowDef = workflowDef()
        metadataClient.registerTaskDefs(listOf(
                taskDef("task_1"),
                taskDef("task_2"),
                taskDef("task_3"),
                taskDef("task_4"),
                taskDef("decide_task")
        ))
        metadataClient.registerWorkflowDef(workflowDef);

        // start workflow execution
        workflowClient.startWorkflow(StartWorkflowRequest().apply {
            name = workflowDef.name
            correlationId = "correlation-${UUID.randomUUID()}"
            input = testWorkflowInput()
        })

    }

    private fun taskDef(taskName: String) =
            TaskDef().apply {
                name = taskName;
                description = name
                retryCount = 3
                timeoutSeconds = 10
                concurrentExecLimit = 10
                rateLimitPerFrequency = 10
                rateLimitFrequencyInSeconds = 10
            }


    private fun workflowDef(): WorkflowDef =
            WorkflowDef().apply {
                name = "teste"
                description = "description"
                version = 1
                tasks = LinkedList(
                        listOf(
                                task1(),
                                task1http(),
                                task2(),
                                decideTask()
                        )
                )
                outputParameters = mapOf(
                        "statuses" to "\${get_es_1.output..status}",
                        "workflowIds" to "\${get_es_1.output..workflowId}"
                )
                isRestartable = true
                schemaVersion = 2
            }

    private fun task1(): WorkflowTask =
            WorkflowTask().apply {
                name = "task_1"
                taskReferenceName = "task_1"
                inputParameters = mapOf(
                        "input_1" to "\${workflow.input.input_1}"
                )
                type = WorkflowTask.Type.SIMPLE.name
            }

    private fun task1http(): WorkflowTask =
            WorkflowTask().apply {
                name = "task_1"
                taskReferenceName = "task_1_http"
                inputParameters = mapOf(
                        "http_request" to mapOf(
                                "uri" to "http://10.0.1.238:8883/success",
                                "method" to "POST",
                                "headers" to mapOf(
                                        "content-type" to "application/json",
                                        "Authorization" to "\${workflow.input.authorization}",
                                        "x-flow-id" to "\${workflow.input.flowId}",
                                        "x-execution-id" to "\${workflow.input.executionId}",
                                        "x-correlation-id" to "\${workflow.correlationId}",
                                        "foo" to "\${task_1.output.foo}",
                                        "bar" to "\${task_1.output.bar}"
                                )
                        )
                )
                type = "HTTP"
            }


    private fun task2(): WorkflowTask =
            WorkflowTask().apply {
                name = "task_2"
                taskReferenceName = "task_2"
                optional = true
                inputParameters = mapOf(
                        "http_request" to mapOf(
                                "uri" to "http://10.0.1.238:8883/callback",
                                "method" to "POST",
                                "headers" to mapOf(
                                        "content-type" to "application/json",
                                        "Authorization" to "\${workflow.input.authorization}",
                                        "x-flow-id" to "\${workflow.input.flowId}",
                                        "x-execution-id" to "\${workflow.input.executionId}",
                                        "x-correlation-id" to "\${workflow.correlationId}"
                                )
                        )
                )
                type = "HTTP"
            }


    private fun decideTask(): WorkflowTask =
            WorkflowTask().apply {
                name = "decide_task"
                taskReferenceName = "decide1"
                inputParameters = mapOf(
                        "statusCode" to "\${task_2.output.response.statusCode}"
                )
                type = WorkflowTask.Type.DECISION.name
                caseValueParam = "statusCode"
                caseExpression = "if (\$.statusCode >= 200 && \$.statusCode < 300) '200'; else '400';"
                decisionCases = mapOf(
                        "200" to LinkedList(listOf(
                                task3()
                        )),
                        "400" to LinkedList(listOf(
                                task4()
                        ))
                )
            }


    private fun task3(): WorkflowTask =
            WorkflowTask().apply {
                name = "task_3"
                taskReferenceName = "task_3"
                inputParameters = mapOf(
                        "http_request" to mapOf(
                                "uri" to "http://10.0.1.238:8883/success",
                                "method" to "POST",
                                "headers" to mapOf(
                                        "content-type" to "application/json",
                                        "Authorization" to "\${workflow.input.authorization}",
                                        "x-flow-id" to "\${workflow.input.flowId}",
                                        "x-execution-id" to "\${workflow.input.executionId}",
                                        "x-correlation-id" to "\${workflow.correlationId}"
                                )
                        )
                )
                type = "HTTP"
            }


    private fun task4(): WorkflowTask =
            WorkflowTask().apply {
                name = "task_4"
                taskReferenceName = "task_4"
                optional = true
                inputParameters = mapOf(
                        "http_request" to mapOf(
                                "uri" to "http://10.0.1.238:8883/error",
                                "method" to "POST",
                                "headers" to mapOf(
                                        "content-type" to "application/json",
                                        "Authorization" to "\${workflow.input.authorization}",
                                        "x-flow-id" to "\${workflow.input.flowId}",
                                        "x-execution-id" to "\${workflow.input.executionId}",
                                        "x-correlation-id" to "\${workflow.correlationId}"
                                )
                        )
                )
                type = "HTTP"
            }

    private fun testWorkflowInput(): Map<String, Any> = mapOf(
            "authorization" to "Bearer abcdefg",
            "flowId" to "teste",
            "executionId" to "teste-1",
            "input_1" to "input_1"
    )

}