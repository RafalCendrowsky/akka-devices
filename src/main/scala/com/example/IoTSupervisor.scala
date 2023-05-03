package com.example

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, PostStop}

object IoTSupervisor {
  def apply(): Behavior[Nothing] =
    Behaviors.setup[Nothing]{ context =>
      context.log.info("IoT Application started")
      context.spawn(DeviceManager(), "Device Manager")
      Behaviors.receiveMessage[Nothing] { _ =>
        Behaviors.unhandled
      }.receiveSignal{
        case (context, PostStop) =>
          context.log.info("IoT Application stopped")
          Behaviors.same
      }
    }
}