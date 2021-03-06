package br.com.zup.integration

import br.com.zup.ApplicationConfig
import br.com.zup.client.EventClient
import com.netflix.conductor.client.http.MetadataClient
import com.netflix.conductor.client.http.WorkflowClient
import com.netflix.conductor.common.metadata.events.EventHandler
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [ApplicationConfig::class])
class EventTest {

    @Autowired
    lateinit var metadataClient: MetadataClient

    @Autowired
    lateinit var workflowClient: WorkflowClient

    @Autowired
    lateinit var eventClient: EventClient

    val completeEventEmitterWorkflowDef = completeEventEmitterWorkflowDef()

    val failEventEmitterWorkflowDef = failEventEmitterWorkflowDef()

    val startWorkflowEmitterWorkflowDef = startWorkflowEmitterWorkflowDef()

    val waitWorkflowDef = receiverWorkflowDef()

    val completeTaskEventHandler = completeTaskEventHandler()

    val failTaskEventHandler = failTaskEventHandler()

    val startWorkflowHandler = startWorkflowHandler()

    @BeforeTest
    fun setup() {
        metadataClient.registerTaskDefs(
            listOf(
                taskDef("complete_event"),
                taskDef("fail_event"),
                taskDef("wait_1"),
                taskDef("start_workflow")
            )
        )
        metadataClient.registerWorkflowDef(completeEventEmitterWorkflowDef)
        metadataClient.registerWorkflowDef(failEventEmitterWorkflowDef)
        metadataClient.registerWorkflowDef(startWorkflowEmitterWorkflowDef)
        metadataClient.registerWorkflowDef(waitWorkflowDef)
        eventClient.addEventHandler(completeTaskEventHandler)
        eventClient.addEventHandler(failTaskEventHandler)
        eventClient.addEventHandler(startWorkflowHandler)
    }

    @AfterTest
    fun cleanUp() {
        metadataClient.unregisterTaskDef("complete_event")
        metadataClient.unregisterTaskDef("fail_event")
        metadataClient.unregisterTaskDef("wait_1")
        metadataClient.unregisterTaskDef("start_workflow")
        metadataClient.unregisterWorkflowDef(
            completeEventEmitterWorkflowDef.name,
            completeEventEmitterWorkflowDef.version
        )
        metadataClient.unregisterWorkflowDef(failEventEmitterWorkflowDef.name, failEventEmitterWorkflowDef.version)
        metadataClient.unregisterWorkflowDef(
            startWorkflowEmitterWorkflowDef.name,
            startWorkflowEmitterWorkflowDef.version
        )
        metadataClient.unregisterWorkflowDef(waitWorkflowDef.name, waitWorkflowDef.version)
        eventClient.removeEventHandler(completeTaskEventHandler.name)
        eventClient.removeEventHandler(failTaskEventHandler.name)
        eventClient.removeEventHandler(startWorkflowHandler.name)
    }

    @Test
    fun `should finish workflow execution after event`() {
        // workflow that waits for event
        val waitWorkflowId = workflowClient.startWorkflow(StartWorkflowRequest().apply {
            name = waitWorkflowDef.name
        })

        // workflow that emits the event
        workflowClient.startWorkflow(StartWorkflowRequest().apply {
            name = completeEventEmitterWorkflowDef.name
            input = mapOf(
                "waitWorkflowId" to waitWorkflowId
            )
        })

        Thread.sleep(3000)

        val workflow = workflowClient.getWorkflow(waitWorkflowId, false)

        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.status)
    }

    @Test
    fun `should fail workflow execution after event`() {
        // workflow that waits for event
        val waitWorkflowId = workflowClient.startWorkflow(StartWorkflowRequest().apply {
            name = waitWorkflowDef.name
        })

        // workflow that emits the event
        workflowClient.startWorkflow(StartWorkflowRequest().apply {
            name = failEventEmitterWorkflowDef.name
            input = mapOf(
                "waitWorkflowId" to waitWorkflowId
            )
        })

        Thread.sleep(3000)

        val workflow = workflowClient.getWorkflow(waitWorkflowId, false)

        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.status)
    }

    @Test
    fun `should start workflow execution after event`() {
        // workflow that emits the event
        val workflowId = workflowClient.startWorkflow(StartWorkflowRequest().apply {
            name = startWorkflowEmitterWorkflowDef.name
            input = mapOf(
                "workflowType" to waitWorkflowDef.name
            )
        })

        Thread.sleep(3000)

//        val workflow = workflowClient.getWorkflow(workflowId, true)
//        val startedWorkflowId: String = workflow.tasks.get(0).outputData.get("workflowId") as String
//
//        val startedWorkflow = workflowClient.getWorkflow(startedWorkflowId, false)
//
//        assertNotNull(startedWorkflow)
//        assertEquals(waitWorkflowDef.name, startedWorkflow.workflowName)
    }

    private fun completeTaskEventHandler() =
        EventHandler().apply {
            name = "complete_task_event_handler"
            event = "conductor:complete_event_emitter_workflow:complete_event"
            actions = listOf(
                EventHandler.Action().apply {
                    action = EventHandler.Action.Type.complete_task
                    complete_task = EventHandler.TaskDetails().apply {
                        workflowId = "\${waitWorkflowId}"
                        taskRefName = "wait_1"
                    }
                }
            )
            isActive = true
        }

    private fun failTaskEventHandler() =
        EventHandler().apply {
            name = "fail_task_event_handler"
            event = "conductor:fail_event_emitter_workflow:fail_event"
            actions = listOf(
                EventHandler.Action().apply {
                    action = EventHandler.Action.Type.fail_task
                    fail_task = EventHandler.TaskDetails().apply {
                        workflowId = "\${waitWorkflowId}"
                        taskRefName = "wait_1"
                    }
                }
            )
            isActive = true
        }

    private fun startWorkflowHandler(): EventHandler =
        EventHandler().apply {
            name = "start_workflow_event_handler"
            event = "conductor:start_workflow:start_workflow"
            actions = listOf(
                EventHandler.Action().apply {
                    action = EventHandler.Action.Type.start_workflow
                    start_workflow = EventHandler.StartWorkflow().apply {
                        name = waitWorkflowDef.name
                        version = waitWorkflowDef.version
                        correlationId = "started_by_event"
                    }
                }
            )
            isActive = true
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

    private fun completeEventEmitterWorkflowDef(): WorkflowDef =
        WorkflowDef().apply {
            name = "complete_event_emitter_workflow"
            description = "Complete Event Emitter"
            version = 1
            tasks = listOf(
                emitCompleteEventTask()
            )
            isRestartable = true
            schemaVersion = 2
        }

    private fun emitCompleteEventTask(): WorkflowTask =
        WorkflowTask().apply {
            name = "complete_event"
            taskReferenceName = "complete_event"
            inputParameters = mapOf(
                "waitWorkflowId" to "\${workflow.input.waitWorkflowId}"
            )
            type = "EVENT"
            sink = "conductor"
        }

    private fun failEventEmitterWorkflowDef(): WorkflowDef =
        WorkflowDef().apply {
            name = "fail_event_emitter_workflow"
            description = "Fail Event Emitter"
            version = 1
            tasks = listOf(
                emitFailEventTask()
            )
            isRestartable = true
            schemaVersion = 2
        }

    private fun emitFailEventTask(): WorkflowTask =
        WorkflowTask().apply {
            name = "fail_event"
            taskReferenceName = "fail_event"
            inputParameters = mapOf(
                "waitWorkflowId" to "\${workflow.input.waitWorkflowId}"
            )
            type = "EVENT"
            sink = "conductor"
        }

    private fun receiverWorkflowDef(): WorkflowDef =
        WorkflowDef().apply {
            name = "receiver"
            description = "receiver"
            version = 1
            tasks = listOf(
                waitTask()
            )
            isRestartable = true
            schemaVersion = 2
        }

    private fun waitTask(): WorkflowTask =
        WorkflowTask().apply {
            name = "wait_1"
            taskReferenceName = "wait_1"
            type = "WAIT"
        }

    private fun startWorkflowEmitterWorkflowDef() =
        WorkflowDef().apply {
            name = "start_workflow"
            description = "Start Workflow"
            version = 1
            tasks = listOf(
                startWorkflowEventTask()
            )
            isRestartable = true
            schemaVersion = 2
        }

    private fun startWorkflowEventTask(): WorkflowTask =
        WorkflowTask().apply {
            name = "start_workflow"
            taskReferenceName = "start_workflow"
            inputParameters = mapOf(
                "workflowType" to "\${workflow.input.workflowType}"
            )
            type = "EVENT"
            sink = "conductor"
        }

}
