package io.ysakhno.tools.spotlight.imagedownloader.commands

import com.github.ajalt.clikt.core.CliktCommand

/**
 * Represents a command that can optionally be skipped during execution.
 *
 * This abstract class extends the functionality of the [CliktCommand] class by adding a mechanism to control whether
 * the command's logic should run or be skipped. Skipping is controlled via the [isSkipRun][Root.isSkipRun] property of
 * the [Root] command.
 *
 * Commands inheriting from this class need to define the specific logic to execute in the [runForReal] method, which is
 * only executed when the skip flag is not set.
 *
 * The mechanism is useful for scenarios such as testing, where side effects that persist beyond test execution (e.g.,
 * file creation or database modifications) should be avoided.
 *
 * @author Yurii Sakhno
 */
abstract class SkippableCommand : CliktCommand() {

    /** Executes the core logic of the command. */
    protected abstract fun runForReal()

    /**
     * Executes the command's logic, or skips execution based on the [isSkipRun][Root.isSkipRun] flag of the root
     * command.
     */
    override fun run() = if ((currentContext.findRoot().command as Root).isSkipRun) {
        val commandTrail = (currentContext.parentNames() + commandName).joinToString(separator = " > ")
        echo("Execution of command '$commandTrail' has been skipped")
    } else {
        runForReal()
    }
}
