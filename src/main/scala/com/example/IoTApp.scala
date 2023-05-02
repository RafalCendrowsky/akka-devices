package com.example

import akka.actor.typed.ActorSystem

object IoTApp {
  def main(args: Array[String]): Unit = {
    ActorSystem[Nothing](IoTSupervisor(), "iot-system")
  }
}
