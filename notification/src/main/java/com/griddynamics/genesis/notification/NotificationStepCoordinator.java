/*
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
 *   @Description: E-mail notifications plugin
 */
package com.griddynamics.genesis.notification;

import com.griddynamics.genesis.plugin.StepExecutionContext;
import com.griddynamics.genesis.plugin.adapter.AbstractActionOrientedStepCoordinator;
import com.griddynamics.genesis.workflow.*;
import com.griddynamics.genesis.workflow.action.ExecutorThrowable;
import scala.collection.Seq;

import static com.griddynamics.genesis.utils.ScalaUtils.*;

@SuppressWarnings("unchecked")
public class NotificationStepCoordinator extends AbstractActionOrientedStepCoordinator implements StepCoordinator {

    private EmailSenderConfiguration emailSenderConfiguration;

    private boolean failed = false;

    public NotificationStepCoordinator(StepExecutionContext context,
                                       NotificationStep step,
                                       EmailSenderConfiguration emailSenderConfiguration) {
        super(context, step);
        this.emailSenderConfiguration = emailSenderConfiguration;
    }

    @Override
    public ActionExecutor getActionExecutor(Action action) {
        if (action instanceof NotificationAction) {
            return new NotificationActionExecutor((NotificationAction) action, emailSenderConfiguration);
        } else {
            throw new RuntimeException("Invalid action type");
        }
    }

    @Override
    protected boolean isFailed() {
        return failed;
    }

    @Override
    public Seq onActionFinish(ActionResult result) {
        if (result instanceof NotificationResult) {
            failed = !((NotificationResult) result).isSuccess();
        } else if (result instanceof ExecutorThrowable) {
            failed = true;
        }
        return list();
    }

    @Override
    public Seq onStepInterrupt(Signal signal) {
        return list();
    }

    @Override
    public Seq onStepStart() {
        return list(new NotificationActionExecutor(new NotificationAction((NotificationStep) step()), emailSenderConfiguration));
    }

}
