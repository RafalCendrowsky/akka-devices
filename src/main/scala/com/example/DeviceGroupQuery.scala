package com.example

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.example.DeviceGroupQuery.{CollectionTimeout, DeviceTerminated, WrappedRespondTemperature}

import scala.concurrent.duration.FiniteDuration

object DeviceGroupQuery {
  trait Command
  final case class WrappedRespondTemperature(response: Device.RespondTemperature) extends Command
  private final case class DeviceTerminated(deviceId: String) extends Command
  private case object CollectionTimeout extends Command

  def apply(
    deviceIdToActor: Map[String, ActorRef[Device.Command]],
    requestId: Long,
    requester: ActorRef[DeviceManager.RespondAllTemperatures],
    timeout: FiniteDuration
  ): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        timers.startSingleTimer(CollectionTimeout, CollectionTimeout, timeout)
        val respondTemperatureAdapter = context.messageAdapter(WrappedRespondTemperature.apply)
        deviceIdToActor.foreach {
          case (deviceId, device) =>
            context.watchWith(device, DeviceTerminated(deviceId))
            device ! Device.ReadTemperature(0, respondTemperatureAdapter)
        }
        new DeviceGroupQuery(deviceIdToActor, requestId, requester, context)
          .deviceGroupQuery(Map.empty, deviceIdToActor.keySet)
      }
    }
  }
}

class DeviceGroupQuery(
  deviceIdToActor: Map[String, ActorRef[Device.Command]],
  requestId: Long,
  requester: ActorRef[DeviceManager.RespondAllTemperatures],
  context: ActorContext[DeviceGroupQuery.Command]) {
  import DeviceManager.DeviceNotAvailable
  import DeviceManager.DeviceTimedOut
  import DeviceManager.RespondAllTemperatures
  import DeviceManager.Temperature
  import DeviceManager.TemperatureNotAvailable
  import DeviceManager.TemperatureReading

  def deviceGroupQuery(repliesSoFar: Map[String, TemperatureReading], stillWaiting: Set[String]): Behavior[DeviceGroupQuery.Command] =
    Behaviors.receiveMessage[DeviceGroupQuery.Command] {

      case WrappedRespondTemperature(response) =>
        val reading = response.value match {
          case Some(value) => Temperature(value)
          case None => TemperatureNotAvailable
        }
        val deviceId = response.deviceId
        respondWhenAllCollected(repliesSoFar + (deviceId -> reading), stillWaiting - deviceId)

      case DeviceTerminated(deviceId) =>
        if (stillWaiting(deviceId))
          respondWhenAllCollected(repliesSoFar + (deviceId -> DeviceNotAvailable), stillWaiting - deviceId)
        else
          Behaviors.same

      case CollectionTimeout =>
        respondWhenAllCollected(repliesSoFar ++ stillWaiting.map(deviceId => deviceId -> DeviceTimedOut) , Set.empty)
    }

  def respondWhenAllCollected(repliesSoFar: Map[String, TemperatureReading], stillWaiting: Set[String]): Behavior[DeviceGroupQuery.Command] =
    if (stillWaiting.isEmpty) {
      requester ! RespondAllTemperatures(requestId, repliesSoFar)
      Behaviors.stopped
    } else {
      deviceGroupQuery(repliesSoFar, stillWaiting)
    }
}
