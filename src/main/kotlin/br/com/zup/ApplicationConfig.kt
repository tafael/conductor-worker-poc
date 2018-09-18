package br.com.zup

import br.com.zup.worker.SampleWorker
import com.netflix.conductor.client.http.MetadataClient
import com.netflix.conductor.client.http.TaskClient
import com.netflix.conductor.client.http.WorkflowClient
import com.netflix.conductor.client.task.WorkflowTaskCoordinator
import org.apache.logging.log4j.LogManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SpringBootApplication
@Configuration
open class ApplicationConfig {

    @Value("\${conductor.server.root-uri}")
    lateinit var rootUri: String

    @Bean
    open fun taskClient(): TaskClient {
        val taskClient = TaskClient()
        //Point this to the server API
        taskClient.setRootURI(rootUri)
        return taskClient
    }

    @Bean
    open fun metadataClient(): MetadataClient {
        val metadataClient = MetadataClient()
        metadataClient.setRootURI(rootUri)
        return metadataClient
    }

    @Bean
    open fun workflowClient(): WorkflowClient =
            WorkflowClient().apply {
                setRootURI(rootUri)
            }

    @Bean
    open fun coordinator(
            taskClient: TaskClient
    ): WorkflowTaskCoordinator {
        //number of threads used to execute workers.  To avoid starvation, should be same or more than number of workers
        val threadCount = 2

        val worker1 = SampleWorker("task_1")

        //Create WorkflowTaskCoordinator
        val builder = WorkflowTaskCoordinator.Builder()
        val coordinator = builder
                .withWorkers(worker1)
                .withThreadCount(threadCount)
                .withTaskClient(taskClient).build()

        //Start for polling and execution of the tasks
        coordinator.init()

        return coordinator

    }

}

private val logger = LogManager.getLogger(ApplicationConfig::class.java)

fun main(args: Array<String>) {

    val app = SpringApplication.run(ApplicationConfig::class.java, *args)
    logger.info(
            """|
                   |------------------------------------------------------------
                   | Conductor poc is running!
                   |------------------------------------------------------------""".trimMargin()
    )

    // prevent application from closing
    while (true) {
    }

}
