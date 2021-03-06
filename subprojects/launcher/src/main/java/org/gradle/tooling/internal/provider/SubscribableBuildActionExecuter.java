/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.tooling.internal.provider;

import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;

import java.util.ArrayList;
import java.util.List;

public class SubscribableBuildActionExecuter implements BuildActionExecuter<BuildActionParameters> {
    private final BuildActionExecuter<BuildActionParameters> delegate;
    private final BuildOperationListenerManager buildOperationListenerManager;
    private final List<BuildOperationListener> listeners = new ArrayList<BuildOperationListener>();
    private final List<? extends SubscribableBuildActionRunnerRegistration> registrations;

    public SubscribableBuildActionExecuter(BuildActionExecuter<BuildActionParameters> delegate, BuildOperationListenerManager buildOperationListenerManager, List<? extends SubscribableBuildActionRunnerRegistration> registrations) {
        this.delegate = delegate;
        this.buildOperationListenerManager = buildOperationListenerManager;
        this.registrations = registrations;
    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters, ServiceRegistry contextServices) {
        boolean subscribable = action instanceof SubscribableBuildAction;
        if (subscribable) {
            BuildEventConsumer eventConsumer = requestContext.getEventConsumer();
            SubscribableBuildAction subscribableBuildAction = (SubscribableBuildAction) action;
            registerListenersForClientSubscriptions(subscribableBuildAction.getClientSubscriptions(), eventConsumer);
        }
        try {
            return delegate.execute(action, requestContext, actionParameters, contextServices);
        } finally {
            for (BuildOperationListener listener : listeners) {
                buildOperationListenerManager.removeListener(listener);
            }
            listeners.clear();
        }
    }

    private void registerListenersForClientSubscriptions(BuildClientSubscriptions clientSubscriptions, BuildEventConsumer eventConsumer) {
        for (SubscribableBuildActionRunnerRegistration registration : registrations) {
            for (BuildOperationListener listener : registration.createListeners(clientSubscriptions, eventConsumer)) {
                registerListener(listener);
            }
        }
    }

    private void registerListener(BuildOperationListener listener) {
        listeners.add(listener);
        buildOperationListenerManager.addListener(listener);
    }
}
