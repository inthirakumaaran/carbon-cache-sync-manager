/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.cache.sync.jms.manager.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.wso2.carbon.cache.sync.jms.manager.JMSProducer;
import org.wso2.carbon.cache.sync.jms.manager.JMSConsumer;
import org.wso2.carbon.cache.sync.jms.manager.JMSUtils;
import org.wso2.carbon.cache.sync.jms.manager.CrossClusterMessageDispatcher;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.cache.CacheInvalidationRequestPropagator;
import javax.cache.CacheInvalidationRequestSender;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;

/**
 * Service component for the JMS cache manager.
 */
@Component(
        name = "org.wso2.carbon.cache.sync.jms.manager.JMSManagerServiceComponent",
        immediate = true
)
public class JMSManagerServiceComponent {

    private static final Log log = LogFactory.getLog(JMSManagerServiceComponent.class);
    private ServiceRegistration serviceRegistrationForCacheEntry = null;
    private ServiceRegistration serviceRegistrationForRequestSend = null;
    private ServiceRegistration serviceRegistrationForCacheRemoval = null;
    private ServiceRegistration serviceRegistrationForCacheUpdate = null;
    private ServiceRegistration serviceRegistrationForCachePropagation = null;
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Activate
    protected void activate(ComponentContext context) {

        // Continue polling until JMS Manager configurations are fully loaded.
        startCacheInvalidatorOnConfigLoaded(context);
    }

    protected void deactivate(ComponentContext context) {

        // Cleanup resources.
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        // Shutdown ActiveMQ producer.
        JMSProducer.getInstance().shutdownExecutorService();
        JMSConsumer.getInstance().closeResources();

        // Unregistering the listener service.
        if (serviceRegistrationForCacheEntry != null) {
            serviceRegistrationForCacheEntry.unregister();
        }
        if (serviceRegistrationForRequestSend != null) {
            serviceRegistrationForRequestSend.unregister();
        }
        if (serviceRegistrationForCacheRemoval != null) {
            serviceRegistrationForCacheRemoval.unregister();
        }
        if (serviceRegistrationForCacheUpdate != null) {
            serviceRegistrationForCacheUpdate.unregister();
        }
        if (serviceRegistrationForCachePropagation != null) {
            serviceRegistrationForCachePropagation.unregister();
        }
        if (log.isDebugEnabled()) {
            log.debug("Cache Sync JMS Manager Service bundle is deactivated.");
        }
    }

    private void startCacheInvalidatorOnConfigLoaded(ComponentContext context) {

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (JMSUtils.isMBCacheInvalidatorEnabled() != null) {
                    startClient(context);
                    scheduler.shutdown();
                }
            } catch (Exception e) {
                log.error("Error while checking Cache Sync JMS Manager Configurations", e);
            }
            // Check every 10 seconds, start immediately.
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void startClient(ComponentContext context) {

        JMSProducer producer = JMSProducer.getInstance();
        serviceRegistrationForCacheEntry = context.getBundleContext().registerService(
                CacheEntryListener.class.getName(), producer, null);
        serviceRegistrationForRequestSend = context.getBundleContext().registerService(
                CacheInvalidationRequestSender.class.getName(), producer, null);
        serviceRegistrationForCacheRemoval = context.getBundleContext().registerService(
                CacheEntryRemovedListener.class.getName(), producer, null);
        serviceRegistrationForCacheUpdate = context.getBundleContext().registerService(
                CacheEntryUpdatedListener.class.getName(), producer, null);
        serviceRegistrationForCachePropagation = context.getBundleContext().registerService(
                CacheInvalidationRequestPropagator.class.getName(), new CrossClusterMessageDispatcher(), null);
        producer.startService();
        JMSConsumer.getInstance().startService();
        log.info("Cache Sync JMS Manager Service bundle activated successfully.");
    }
}
