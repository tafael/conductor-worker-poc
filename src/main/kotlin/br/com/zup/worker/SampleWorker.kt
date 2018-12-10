package br.com.zup.worker

import com.netflix.conductor.client.worker.Worker
import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.metadata.tasks.TaskResult.Status

class SampleWorker(
        private val taskDefName: String
) : Worker {

    override fun getTaskDefName(): String = taskDefName

    // this should return false when this worker decide to ignore this task.
    override fun preAck(task: Task?): Boolean {
        return true;
    }

    override fun execute(task: Task): TaskResult {

        println("Executing $taskDefName")

        println(task.inputData["input_1"])

        val result = TaskResult(task)
        result.setStatus(Status.COMPLETED)

        //Register the output of the task
        result.outputData["foo"] = "foo"
        result.outputData["bar"] = "bar"

        result.log("Executed task with success !!!")

        return result
    }

}