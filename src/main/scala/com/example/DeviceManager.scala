package com.example

import akka.actor.typed.ActorRef

object DeviceManager {
  sealed trait Command
  final case class RequestTrackDevice(groupId: String, deviceId: String, replyTo: ActorRef[DeviceRegistered])
    extends DeviceManager.Command with DeviceGroup.Command
  final case class DeviceRegistered(device: ActorRef[Device.Command])
  final case class RequestDeviceList(requestId: Long, groupId: String, replyTo: ActorRef[ReplyDeviceList])
    extends DeviceManager.Command with DeviceGroup.Command
  final case class ReplyDeviceList(requestId: Long, ids: Set[String])
}
