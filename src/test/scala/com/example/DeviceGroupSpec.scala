package com.example

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.example.Device.{RecordTemperature, TemperatureRecorded}
import com.example.DeviceManager.{DeviceRegistered, ReplyDeviceList, RequestAllTemperatures, RequestDeviceList, RequestTrackDevice, RespondAllTemperatures, Temperature, TemperatureNotAvailable}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

class DeviceGroupSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Device group" must {
    "be able to register a device actor" in {
      val probe = createTestProbe[DeviceRegistered]()
      val groupActor = spawn(DeviceGroup("group"))

      groupActor ! RequestTrackDevice("group", "device1", probe.ref)
      val registered1 = probe.receiveMessage()
      val device1 = registered1.device

      groupActor ! RequestTrackDevice("group", "device2", probe.ref)
      val registered2 = probe.receiveMessage()
      val device2 = registered2.device

      device1 should !== (device2)

      val recordProbe = createTestProbe[TemperatureRecorded]()
      device1 ! RecordTemperature(requestId = 0, 1.0, recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(requestId = 0))
      device2 ! RecordTemperature(requestId = 1, 2.0, recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(requestId = 1))

    }

    "ignore requests for wrong groupId" in {
      val registeredProbe = createTestProbe[DeviceRegistered]()
      val deviceListProbe = createTestProbe[ReplyDeviceList]()
      val groupActor = spawn(DeviceGroup("group"))

      groupActor ! RequestTrackDevice("wrongGroup", "device1", registeredProbe.ref)
      registeredProbe.expectNoMessage(500.milliseconds)

      groupActor ! RequestDeviceList(requestId = 0, "wrongGroup", deviceListProbe.ref)
      deviceListProbe.expectNoMessage(500.milliseconds)
    }

    "return same actor for same deviceId" in {
      val probe = createTestProbe[DeviceRegistered]()
      val groupActor = spawn(DeviceGroup("group"))

      groupActor ! RequestTrackDevice("group", "device1", probe.ref)
      val registered1 = probe.receiveMessage()
      val device1 = registered1.device

      groupActor ! RequestTrackDevice("group", "device1", probe.ref)
      val registered2 = probe.receiveMessage()
      val device2 = registered2.device

      device1 should === (device2)
    }

    "return deviceIds for active devices" in {
      val registeredProbe = createTestProbe[DeviceRegistered]()
      val groupActor = spawn(DeviceGroup("group"))

      groupActor ! RequestTrackDevice("group", "device1", registeredProbe.ref)

      groupActor ! RequestTrackDevice("group", "device2", registeredProbe.ref)

      val deviceListProbe = createTestProbe[ReplyDeviceList]()
      groupActor ! RequestDeviceList(requestId = 0, "group", deviceListProbe.ref)
      deviceListProbe.expectMessage(ReplyDeviceList(requestId = 0, Set("device1", "device2")))
    }

    "be able to list active devices after one stops" in {
      val registeredProbe = createTestProbe[DeviceRegistered]()
      val deviceListProbe = createTestProbe[ReplyDeviceList]()
      val groupActor = spawn(DeviceGroup("group"))

      groupActor ! RequestTrackDevice("group", "device1", registeredProbe.ref)
      groupActor ! RequestTrackDevice("group", "device2", registeredProbe.ref)
      val toShutDown = registeredProbe.receiveMessage().device

      toShutDown ! Device.Passivate
      registeredProbe.expectTerminated(toShutDown, registeredProbe.remainingOrDefault)

      registeredProbe.awaitAssert {
        groupActor ! RequestDeviceList(requestId = 0, "group", deviceListProbe.ref)
        deviceListProbe.expectMessage(ReplyDeviceList(requestId = 0, Set("device2")))
      }
    }

    "be able to collect temperatures from all active devices" in {
      val registeredProbe = createTestProbe[DeviceRegistered]()
      val groupActor = spawn(DeviceGroup("group"))

      groupActor ! RequestTrackDevice("group", "device1", registeredProbe.ref)
      val deviceActor1 = registeredProbe.receiveMessage().device

      groupActor ! RequestTrackDevice("group", "device2", registeredProbe.ref)
      val deviceActor2 = registeredProbe.receiveMessage().device

      groupActor ! RequestTrackDevice("group", "device3", registeredProbe.ref)
      registeredProbe.receiveMessage()

      val recordProbe = createTestProbe[TemperatureRecorded]()
      deviceActor1 ! RecordTemperature(requestId = 0, 1.0, recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(requestId = 0))
      deviceActor2 ! RecordTemperature(requestId = 1, 2.0, recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(requestId = 1))

      val allTempProbe = createTestProbe[RespondAllTemperatures]()
      groupActor ! RequestAllTemperatures(requestId = 0, groupId = "group", allTempProbe.ref)
      allTempProbe.expectMessage(
        RespondAllTemperatures(
          requestId = 0,
          temperatures =
            Map("device1" -> Temperature(1.0), "device2" -> Temperature(2.0), "device3" -> TemperatureNotAvailable)))
    }
  }
}
