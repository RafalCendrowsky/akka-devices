package com.example

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import com.example.DeviceManager.{Command, DeviceGroupTerminated, ReplyDeviceList, RequestAllTemperatures, RequestDeviceList, RequestTrackDevice, RespondAllTemperatures}

object DeviceManager {
  sealed trait Command
  final case class RequestTrackDevice(groupId: String, deviceId: String, replyTo: ActorRef[DeviceRegistered])
    extends DeviceManager.Command with DeviceGroup.Command
  final case class DeviceRegistered(device: ActorRef[Device.Command])
  final case class RequestDeviceList(requestId: Long, groupId: String, replyTo: ActorRef[ReplyDeviceList])
    extends DeviceManager.Command with DeviceGroup.Command
  final case class ReplyDeviceList(requestId: Long, ids: Set[String])
  private final case class DeviceGroupTerminated(groupId: String) extends DeviceManager.Command

  sealed trait TemperatureReading
  final case class RequestAllTemperatures(requestId: Long, groupId: String, replyTo: ActorRef[RespondAllTemperatures])
    extends DeviceGroupQuery.Command
      with DeviceGroup.Command
      with DeviceManager.Command
  final case class RespondAllTemperatures(requestId: Long, temperatures: Map[String, TemperatureReading])
  final case class Temperature(value: Double) extends TemperatureReading
  case object TemperatureNotAvailable extends TemperatureReading
  case object DeviceNotAvailable extends TemperatureReading
  case object DeviceTimedOut extends TemperatureReading

  def apply(): Behavior[Command] = {
    Behaviors.setup[Command] { context =>
      context.log.info("DeviceManager started")
      new DeviceManager(context).deviceManager(Map.empty)
    }
  }
}

class DeviceManager(context: ActorContext[Command]) {

  def deviceManager(groupIdToActor: Map[String, ActorRef[DeviceGroup.Command]]): Behavior[Command] = {
    Behaviors.receiveMessage[Command] {
      case trackReq @ RequestTrackDevice(groupId, _, _) =>
        groupIdToActor.get(groupId) match {
          case Some(groupActor) =>
            groupActor ! trackReq
            Behaviors.same
          case None =>
            context.log.info("Creating group actor for {}", groupId)
            val groupActor = context.spawn(DeviceGroup(groupId), s"group-$groupId")
            context.watchWith(groupActor, DeviceGroupTerminated(groupId))
            groupActor ! trackReq
            deviceManager(groupIdToActor + (groupId -> groupActor))
        }

      case req @ RequestDeviceList(requestId, groupId, replyTo) =>
        groupIdToActor.get(groupId) match {
          case Some(groupActor) =>
            groupActor ! req
          case None =>
            replyTo ! ReplyDeviceList(requestId, Set.empty)
        }
        Behaviors.same

      case req @ RequestAllTemperatures(requestId, groupId, replyTo) =>
        groupIdToActor.get(groupId) match {
          case Some(groupActor) =>
            groupActor ! req
          case None =>
            replyTo ! RespondAllTemperatures(requestId, Map.empty)
        }
        Behaviors.same

      case DeviceGroupTerminated(groupId) =>
        context.log.info("Device group actor for {} has been terminated", groupId)
        deviceManager(groupIdToActor - groupId)
        Behaviors.same

    }.receiveSignal {
      case (context, PostStop) =>
        context.log.info("DeviceManager stopped")
        Behaviors.same
    }
  }
}
