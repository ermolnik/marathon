package com.malinskiy.marathon.scenario

import com.malinskiy.marathon.device.DeviceProvider
import com.malinskiy.marathon.execution.TestStatus
import com.malinskiy.marathon.spek.initKoin
import com.malinskiy.marathon.test.StubDevice
import com.malinskiy.marathon.test.Test
import com.malinskiy.marathon.test.assert.shouldBeEqualTo
import com.malinskiy.marathon.test.setupMarathon
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineContext
import org.amshove.kluent.shouldBe
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.File
import java.util.concurrent.TimeUnit

class DisconnectingScenarios : Spek({
    initKoin()

    given("two healthy devices") {
        on("execution of two tests while one device disconnects") {
            it("should pass") {
                var output: File? = null
                val context = TestCoroutineContext("testing context")

                val marathon = setupMarathon {
                    val test1 = Test("test", "SimpleTest", "test1", emptySet())
                    val test2 = Test("test", "SimpleTest", "test2", emptySet())
                    val device1 = StubDevice(serialNumber = "serial-1")
                    val device2 = StubDevice(serialNumber = "serial-2")

                    configuration {
                        output = outputDir

                        tests {
                            listOf(test1, test2)
                        }

                        vendorConfiguration.deviceProvider.context = context

                        devices {
                            delay(1000)
                            it.send(DeviceProvider.DeviceEvent.DeviceConnected(device1))
                            delay(100)
                            it.send(DeviceProvider.DeviceEvent.DeviceConnected(device2))
                            delay(5000)
                            it.send(DeviceProvider.DeviceEvent.DeviceDisconnected(device1))
                        }
                    }

                    device1.executionResults = mapOf(
                            test1 to arrayOf(TestStatus.INCOMPLETE),
                            test2 to arrayOf(TestStatus.INCOMPLETE)
                    )
                    device2.executionResults = mapOf(
                            test1 to arrayOf(TestStatus.PASSED),
                            test2 to arrayOf(TestStatus.PASSED)
                    )
                }

                val job = GlobalScope.launch(context = context) {
                    marathon.runAsync()
                }

                context.advanceTimeBy(20, TimeUnit.SECONDS)

                job.isCompleted shouldBe true

                File(output!!.absolutePath + "/test_result", "raw.json")
                        .shouldBeEqualTo(File(javaClass.getResource("/output/raw/disconnecting_scenario_1.json").file))
            }
        }
    }
})
