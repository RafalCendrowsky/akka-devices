package com.example

import akka.actor.typed.{ActorRef, Behavior, PostStop, Terminated}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import com.example.DeviceGroup.{Command, DeviceTerminated}
import com.example.DeviceManager.{DeviceRegistered, ReplyDeviceList, RequestDeviceList, RequestTrackDevice}

object DeviceGroup {
  trait Command
  private final case class DeviceTerminated(device: ActorRef[Device.Command], groupId: String, deviceId: String)
    extends Command

  def apply(groupId: String): Behavior[Command] = {
    Behaviors.setup[Command] { context =>
      context.log.info("DeviceGroup {} started", groupId)
      new DeviceGroup(context, groupId).deviceGroup(Map.empty)
    }
  }

}

class DeviceGroup(context: ActorContext[Command], groupId: String) {

  def deviceGroup(deviceIdToActor: Map[String, ActorRef[Device.Command]]): Behavior[Command] = {
    Behaviors.receiveMessage[Command] {

      case RequestTrackDevice(`groupId`, deviceId, replyTo) =>
        deviceIdToActor.get(deviceId) match {
          case Some(deviceActor) =>
            replyTo ! DeviceRegistered(deviceActor)
            Behaviors.same
          case None =>
            context.log.info("Creating device actor for {}", deviceId)
            val deviceActor = context.spawn(Device(groupId, deviceId), s"device-$deviceId")
            context.watchWith(deviceActor, DeviceTerminated(deviceActor, groupId, deviceId))
            replyTo ! DeviceRegistered(deviceActor)
            deviceGroup(deviceIdToActor + (deviceId -> deviceActor))
        }

      case RequestTrackDevice(gId, _, _) =>
        context.log.warn2("Ignoring TrackDevice request for {}. This actor is responsible for {}.", gId, groupId)
        Behaviors.same

      case DeviceTerminated(_, _, deviceId) =>
        context.log.info("Device actor for {} has been terminated", deviceId)
        deviceGroup(deviceIdToActor - deviceId)

      case RequestDeviceList(requestId, `groupId`, replyTo) =>
          replyTo ! ReplyDeviceList(requestId, deviceIdToActor.keySet)
          Behaviors.same

      case RequestDeviceList(_, gId, _) =>
        context.log.warn2("Ignoring RequestDeviceList for {}. This actor is responsible for {}.", gId, groupId)
        Behaviors.same

    }.receiveSignal {
      case (context, PostStop) =>
        context.log.info("DeviceGroup {} stopped", groupId)
        Behaviors.same
    }
  }
}
