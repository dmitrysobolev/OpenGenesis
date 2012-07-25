/**
 * Copyright (c) 2010-2012 Grid Dynamics Consulting Services, Inc, All Rights Reserved
 *   http://www.griddynamics.com
 *
 *   This library is free software; you can redistribute it and/or modify it under the terms of
 *   the GNU Lesser General Public License as published by the Free Software Foundation; either
 *   version 2.1 of the License, or any later version.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *   AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *   IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *   DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 *   FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 *   DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 *   SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *   OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *   OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *   @Project:     Genesis
 *   @Description: Execution Workflow Engine
 */
package com.griddynamics.genesis.logging

import com.griddynamics.genesis.util.Logging
import com.griddynamics.genesis.service.StoreService
import akka.actor.{ActorRef, Actor}

class LoggerActor(val service: StoreService) extends Actor with Logging {
  protected def receive = {
    case Log(id, message) => {
      service.writeLog(id, message)
    }
    case ActionBasedLog(actionUUID, message) => {
      service.writeLog(actionUUID, message)
    }
  }
}

case class Log(stepId : Int, message: String)

case class ActionBasedLog(actionUID: String, message: String)

trait InternalLogger {
   def stepId : Int
   def writeLog(message : String) {
       LoggerWrapper.writeLog(stepId, message)
   }
}

object LoggerWrapper extends Logging {

  def start(storeService: StoreService) = {
    Actor.registry.actorsFor(classOf[LoggerActor]).isEmpty match {
      case true => {
        val loggerRef: ActorRef = Actor.actorOf(new LoggerActor(storeService))
        loggerRef.start()
      }
      case false =>
    }
  }

  def writeLog(actionUUID: String, message: String) {
    Actor.registry.actorsFor(classOf[LoggerActor]).foreach {
      _ ! ActionBasedLog(actionUUID, message)
    }
  }

  def writeLog(id: Int, message: String) {
    Actor.registry.actorsFor(classOf[LoggerActor]).foreach {
      _ ! Log(id, message)
    }
  }
}