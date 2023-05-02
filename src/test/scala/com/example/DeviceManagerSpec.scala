package com.example

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.example.DeviceManager.{DeviceRegistered, RequestTrackDevice}
import org.scalatest.wordspec.AnyWordSpecLike

class DeviceManagerSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Device manager" must {
    "be able to register a new group" in {
      val probe = createTestProbe[DeviceRegistered]()
      val managerActor = spawn(DeviceManager())

      managerActor ! RequestTrackDevice("group", "device1", probe.ref)
      probe.receiveMessage()
    }

    "be able to list deviceIds for groups" in {
      val probe = createTestProbe[DeviceRegistered]()
      val managerActor = spawn(DeviceManager())

      managerActor ! RequestTrackDevice("group", "device1", probe.ref)
      managerActor ! RequestTrackDevice("group", "device2", probe.ref)
      managerActor ! RequestTrackDevice("group2", "device3", probe.ref)

      val deviceListProbe = createTestProbe[DeviceManager.ReplyDeviceList]()
      managerActor ! DeviceManager.RequestDeviceList(requestId = 0, "group", deviceListProbe.ref)
      deviceListProbe.expectMessage(DeviceManager.ReplyDeviceList(requestId = 0, Set("device1", "device2")))

      managerActor ! DeviceManager.RequestDeviceList(requestId = 1, "group2", deviceListProbe.ref)
      deviceListProbe.expectMessage(DeviceManager.ReplyDeviceList(requestId = 1, Set("device3")))

      managerActor ! DeviceManager.RequestDeviceList(requestId = 1, "group3", deviceListProbe.ref)
      deviceListProbe.expectMessage(DeviceManager.ReplyDeviceList(requestId = 1, Set("device3")))
    }
  }

}
