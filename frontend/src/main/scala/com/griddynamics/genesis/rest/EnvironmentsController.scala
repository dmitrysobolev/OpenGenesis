/**
 * Copyright (c) 2010-2012 Grid Dynamics Consulting Services, Inc, All Rights Reserved
 * http://www.griddynamics.com
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of
 * the GNU Lesser General Public License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @Project:     Genesis
 * @Description: Execution Workflow Engine
 */
package com.griddynamics.genesis.rest

import org.springframework.stereotype.Controller
import scala.Array
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import com.griddynamics.genesis.http.TunnelFilter
import org.springframework.web.bind.annotation.{PathVariable, ResponseBody, RequestMethod, RequestMapping}
import org.springframework.beans.factory.annotation.Value
import com.griddynamics.genesis.api.{GenesisService, RequestResult, EnvironmentDetails}

@Controller
@RequestMapping(Array("/rest/projects/{projectId}/envs"))
class EnvironmentsController(genesisService: GenesisService) extends RestApiExceptionsHandler {
  import GenesisRestController._

  @Value("${genesis.system.server.mode:frontend}")
  var mode = ""

  @RequestMapping(method = Array(RequestMethod.POST))
  @ResponseBody
  def createEnv(@PathVariable projectId: Int, request: HttpServletRequest, response : HttpServletResponse) = {
    val paramsMap = extractParamsMap(request)
    val envName = extractValue("envName", paramsMap)
    val templateName = extractValue("templateName", paramsMap)
    val templateVersion = extractValue("templateVersion", paramsMap)
    val variables = extractVariables(paramsMap)

    val user = mode match {
      case "backend" => request.getHeader(TunnelFilter.SEC_HEADER_NAME)
      case _ => getCurrentUser
    }
    genesisService.createEnv(projectId.toInt, envName, user, templateName, templateVersion, variables)
  }

  @RequestMapping(value=Array("{envName}/logs/{stepId}"))
  def stepLogs(@PathVariable("projectId") projectId: Int,
               @PathVariable("envName") envName: String,
               @PathVariable stepId: Int,
               response: HttpServletResponse,
               request: HttpServletRequest) {
    assertEnvExist(projectId, envName)

    val logs: Seq[String] = genesisService.getLogs(envName, stepId)
    val text = if (logs.isEmpty)
      "No logs yet"
    else
      logs.reduceLeft(_ + "\n" + _)
    response.setContentType("text/plain")
    response.getWriter.write(text)
    response.getWriter.flush()
  }

  @RequestMapping(value = Array("{envName}"), method = Array(RequestMethod.DELETE))
  @ResponseBody
  def deleteEnv( @PathVariable("projectId") projectId: Int,
                 @PathVariable("envName") envName: String,
                 request: HttpServletRequest) = {
    assertEnvExist(projectId, envName)
    genesisService.destroyEnv(envName, Map[String, String]())
  }


  @RequestMapping(value = Array("{envName}"), method = Array(RequestMethod.GET))
  @ResponseBody
  def describeEnv(@PathVariable("projectId") projectId: Int,
                  @PathVariable("envName") envName: String,
                  response : HttpServletResponse) : EnvironmentDetails = {
    assertEnvExist(projectId, envName)
    genesisService.describeEnv(envName).getOrElse(throw new ResourceNotFoundException("Environment [" + envName + "] was not found"))
  }


  @RequestMapping(method = Array(RequestMethod.GET))
  @ResponseBody
  def listEnvs(@PathVariable("projectId") projectId: Int, request: HttpServletRequest) = genesisService.listEnvs(projectId)

  @RequestMapping(value = Array("{envName}/actions"), method = Array(RequestMethod.POST))
  @ResponseBody
  def executeAction(@PathVariable("projectId") projectId: Int,
                    @PathVariable("envName") env: String,
                    request: HttpServletRequest) = {
    assertEnvExist(projectId, env)

    val requestMap = extractParamsMap(request)
    extractNotEmptyValue("action", requestMap) match {
      case "cancel" => {
        genesisService.cancelWorkflow(env)
        RequestResult(isSuccess = true)
      }

      case "execute" => {
        val parameters = extractMapValue("parameters", requestMap)
        val workflow = extractValue("workflow", parameters)
        val variables = extractMapValue("variables", parameters)
        genesisService.requestWorkflow(env, workflow, extractVariables(variables))
      }

      case _ => throw new InvalidInputException ()
    }
  }

  private def assertEnvExist(projectId: Int, env: String) {
    if (!genesisService.isEnvExists(env, projectId)) {
      throw new ResourceNotFoundException("Environment [" + env + "] wasn't found in project [id = "+ projectId + "]")
    }
  }
}