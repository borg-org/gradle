/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.integration

import org.gradle.api.JavaVersion

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

import org.gradle.kotlin.dsl.fixtures.classEntriesFor
import org.gradle.kotlin.dsl.support.zipTo

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matcher
import org.junit.Assert.assertThat
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue

import java.io.File


abstract class AbstractKotlinIntegrationTest : AbstractIntegrationTest() {

    protected
    open val defaultSettingsScript
        get() = ""

    protected
    val repositoriesBlock
        get() = """
            repositories {
                gradlePluginPortal()
            }
        """

    protected
    fun withDefaultSettings() =
        withDefaultSettingsIn(".")

    protected
    fun withDefaultSettingsIn(baseDir: String) =
        withSettingsIn(baseDir, defaultSettingsScript)

    protected
    fun withSettings(script: String, produceFile: (String) -> File = ::newFile): File =
        withSettingsIn(".", script, produceFile)

    protected
    fun withSettingsIn(baseDir: String, script: String, produceFile: (String) -> File = ::newFile): File =
        withFile("$baseDir/settings.gradle.kts", script, produceFile)

    protected
    fun withBuildScript(script: String, produceFile: (String) -> File = ::newFile): File =
        withBuildScriptIn(".", script, produceFile)

    protected
    fun withBuildScriptIn(baseDir: String, script: String, produceFile: (String) -> File = ::newFile): File =
        withFile("$baseDir/build.gradle.kts", script, produceFile)

    protected
    fun withFile(fileName: String, text: String = "", produceFile: (String) -> File = ::newFile) =
        writeFile(produceFile(fileName), text)

    protected
    fun writeFile(file: File, text: String): File =
        file.apply { writeText(text) }

    protected
    fun withBuildSrc() =
        withFile("buildSrc/src/main/groovy/build/Foo.groovy", """
            package build
            class Foo {}
        """)

    protected
    fun withKotlinBuildSrc() {
        withDefaultSettingsIn("buildSrc")
        withBuildScriptIn("buildSrc", """
            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock
        """)
    }

    protected
    fun withClassJar(fileName: String, vararg classes: Class<*>) =
        withZip(fileName, classEntriesFor(*classes))

    protected
    fun withZip(fileName: String, entries: Sequence<Pair<String, ByteArray>>): File =
        newFile(fileName).also {
            zipTo(it, entries)
        }

    protected
    fun newFile(fileName: String): File {
        return canonicalFile(fileName).apply {
            parentFile.mkdirs()
            createNewFile()
        }
    }

    protected
    fun newDir(relativePath: String): File =
        existing(relativePath).apply { assert(mkdirs()) }

    protected
    fun newOrExisting(fileName: String) =
        existing(fileName).let {
            when {
                it.isFile -> it
                else -> newFile(fileName)
            }
        }

    protected
    fun existing(relativePath: String): File =
        canonicalFile(relativePath)

    private
    fun canonicalFile(relativePath: String) =
        File(testDirectory, relativePath).canonicalFile

    fun build(vararg arguments: String): ExecutionResult =
        executer.withArguments(*arguments).run()

    protected
    fun buildFailureOutput(vararg arguments: String): String =
        buildAndFail(*arguments).error

    protected
    fun buildAndFail(vararg arguments: String): ExecutionFailure =
        executer.withArguments(*arguments).runWithFailure()

    protected
    fun assumeJavaLessThan9() {
        assumeTrue("Test disabled under JDK 9 and higher", JavaVersion.current() < JavaVersion.VERSION_1_9)
    }

    protected
    fun assumeJavaLessThan11() {
        assumeTrue("Test disabled under JDK 11 and higher", JavaVersion.current() < JavaVersion.VERSION_11)
    }

    protected
    fun assumeNonEmbeddedGradleExecuter() {
        assumeFalse(GradleContextualExecuter.isEmbedded())
    }

    protected
    fun canPublishBuildScan() {
        assertThat(
            build("tasks", "--scan").output,
            containsBuildScanPluginOutput())
    }

    protected
    fun containsBuildScanPluginOutput(): Matcher<String> = allOf(
        containsString("Publishing build scan..."),
        not(containsString("The build scan plugin was applied after other plugins."))
    )
}
