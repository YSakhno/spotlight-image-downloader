package io.ysakhno.tools.spotlight.imagedownloader.commands

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * Unit tests for [SkippableCommand] class.
 *
 * @author Yurii Sakhno
 */
class SkippableCommandTest : FunSpec({

    /** A mock implementation of [SkippableCommand] that tracks whether [runForReal] has been called. */
    class MockSkippable : SkippableCommand() {
        /** The flag that tracks whether [runForReal] has been called. Initially it is `false. */
        var isRunForRealCalled = false
            private set

        /** Sets the [isRunForRealCalled] flag to `true` when called. */
        override fun runForReal() {
            echo("Method runForReal() called")
            isRunForRealCalled = true
        }
    }

    test("should skip execution when isSkipRun is true") {
        val mockCommand = MockSkippable()
        val root = Root(isSkipRun = true).apply { subcommands(mockCommand) }

        val result = root.test("mock-skippable")

        mockCommand.isRunForRealCalled.shouldBeFalse()

        result.statusCode shouldBe 0
        result.stdout shouldContain "Execution of command '"
        result.stdout shouldContain " > mock-skippable' has been skipped"
        result.stderr.shouldBeEmpty()
    }
    test("should execute runForReal when isSkipRun is false") {
        val mockCommand = MockSkippable()
        val root = Root(isSkipRun = false).apply { subcommands(mockCommand) }

        val result = root.test("mock-skippable")

        mockCommand.isRunForRealCalled.shouldBeTrue()

        result.statusCode shouldBe 0
        result.stdout shouldNotContain "skipped"
        result.stdout shouldContain "Method runForReal() called"
        result.stderr.shouldBeEmpty()
    }
})
