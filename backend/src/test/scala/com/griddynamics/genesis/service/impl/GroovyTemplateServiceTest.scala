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
package com.griddynamics.genesis.service.impl

import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import org.junit.Test
import com.griddynamics.genesis.util.IoUtil
import org.mockito.Mockito
import com.griddynamics.genesis.plugin._
import reflect.BeanProperty
import org.springframework.core.convert.support.ConversionServiceFactory
import com.griddynamics.genesis.workflow.Step
import com.griddynamics.genesis.template.{VersionedTemplate, TemplateRepository}

case class DoNothingStep(name: String) extends Step

class DoNothingStepBuilderFactory extends StepBuilderFactory {
    val stepName = "teststep"

    def newStepBuilder = new StepBuilder {
        @BeanProperty var text: String = _

        def getDetails = DoNothingStep(text)
    }
}

class GroovyTemplateServiceTest extends AssertionsForJUnit with MockitoSugar {
    val templateRepository = mock[TemplateRepository]
    val body = IoUtil.streamAsString(classOf[GroovyTemplateServiceTest].getResourceAsStream("/groovy/ExampleEnv.genesis"))
    Mockito.when(templateRepository.listSources).thenReturn(Map(VersionedTemplate("1") -> body))
    val templateService = new GroovyTemplateService(templateRepository,
        List(new DoNothingStepBuilderFactory), ConversionServiceFactory.createDefaultConversionService())

    @Test def testEmbody() {
        val res = templateService.findTemplate("TestEnv", "0.1").get.createWorkflow.embody(Map("nodesCount" -> "1", "test" -> "test"))
        assert(res.size === 2)
        assert(res.head.phase == "provision")

        templateService.findTemplate("TestEnv", "0.1").get.destroyWorkflow.embody(Map())
    }

    @Test(expected = classOf[IllegalArgumentException])
    def testEmbodyWrongVariableCount() {
        templateService.findTemplate("TestEnv", "0.1").get.createWorkflow.embody(Map("nodesCount" -> "1"))
    }

    @Test def testValidateNoVariable() {
        val res = templateService.findTemplate("TestEnv", "0.1").get.createWorkflow.validate(Map())
        assert(res.size === 2)
        assert(res.head.variableName === "nodesCount")
    }

    @Test def testValidateWrongVariable() {
        val res = templateService.findTemplate("TestEnv", "0.1").get.createWorkflow.validate(Map("nodesCount" -> "nothing", "test" -> "test"))
        assert(res.size === 1)
        assert(res.head.variableName === "nodesCount")
    }

    @Test def testValidationError() {
        val res = templateService.findTemplate("TestEnv", "0.1").get.createWorkflow.validate(Map("nodesCount" -> "0", "test" -> "test"))
        assert(res.size === 1)
        assert(res.head.variableName === "nodesCount")
    }

    @Test def testValidate() {
        val res = templateService.findTemplate("TestEnv", "0.1").get.createWorkflow.validate(Map("nodesCount" -> "1", "test" -> "test"))
        assert(res.isEmpty)
    }

    @Test def testListTemplates() {
        val res = templateService.listTemplates
        assert(res.size === 1)
        assert(res.head === ("TestEnv", "0.1"))
    }

    @Test def testDescribeTemplate() {
        val res = templateService.findTemplate("TestEnv", "0.1").get
        assert(res.listWorkflows.size === 2)
        assert(res.listWorkflows.filter(_.name == "create").headOption.isDefined)
        assert(res.createWorkflow.variableDescriptions.filter(_.name == "nodesCount").headOption.isDefined)
        assert(res.createWorkflow.variableDescriptions.filter(_.name == "optional").headOption.get.defaultValue == "1")
        assert(res.createWorkflow.variableDescriptions.filter(_.name == "optionalNoValue").headOption.get.isOptional)
    }
}