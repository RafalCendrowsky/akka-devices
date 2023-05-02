package com.example

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import com.example.Device.Command

object Device {
  sealed trait Command
  final case class ReadTemperature(requestId: Long, replyTo: ActorRef[RespondTemperature]) extends Command
  final case class RespondTemperature(requestId: Long, value: Option[Double]) extends Command
  final case class RecordTemperature(requestId: Long, value: Double, replyTo: ActorRef[TemperatureRecorded])
    extends Command
  final case class TemperatureRecorded(requestId: Long)

  def apply(groupId: String, deviceId: String): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info2("Device actor {}-{} started", groupId, deviceId)
      new Device(context, groupId, deviceId).device(None)
    }
}

class Device private (context: ActorContext[Command], groupId: String, deviceId: String) {
  import Device._

  private def device(reading: Option[Double]): Behavior[Command] = {
    Behaviors.receiveMessage[Command] {
      case ReadTemperature(requestId, replyTo) =>
        context.log.info("ReadTemperature({})", requestId)
        replyTo ! RespondTemperature(requestId, reading)
        Behaviors.same
      case RecordTemperature(requestId, value, replyTo) =>
        context.log.info("RecordTemperature({}, {})", requestId, value)
        replyTo ! TemperatureRecorded(requestId)
        device(Some(value))
    }.receiveSignal( {
      case (context, PostStop) =>
        context.log.info2("Device actor {}-{} stopped", groupId, deviceId)
        Behaviors.same
    })
  }
}