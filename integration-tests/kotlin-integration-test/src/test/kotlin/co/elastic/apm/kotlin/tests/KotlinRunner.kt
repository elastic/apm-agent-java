package co.elastic.apm.kotlin.tests

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object KotlinRunner {
    @Throws(IOException::class, InterruptedException::class)
    fun exec(clazz: Class<*>): Int {
        val javaHome = System.getProperty("java.home")
        val javaBin = javaHome + File.separator + "bin" + File.separator + "java"
        val classpath = System.getProperty("java.class.path")
        val className = clazz.name

        val command = ArrayList<String>()
        command.add(javaBin)
        command.add("-cp")
        command.add(classpath)
        command.add(className)

        val builder = ProcessBuilder(command)
        builder.environment()["ELASTIC_APM_ENABLE_EXPERIMENTAL_INSTRUMENTATIONS"] = "true"
        val process = builder.inheritIO().start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
        return process.exitValue()
    }
}
