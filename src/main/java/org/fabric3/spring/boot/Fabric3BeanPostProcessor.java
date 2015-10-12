package org.fabric3.spring.boot;

import java.util.ArrayList;
import java.util.List;

import org.fabric3.api.node.Domain;
import org.fabric3.api.node.Fabric;
import org.fabric3.api.node.ServiceIntrospector;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Introspects Spring beans to determine if they are bound to an endpoint and, if so, deploys them to the Fabric3 container.
 *
 * Deployment is done lazily via {@link #deployFabricServices} since the entire Spring application context must be initialized as Fabric3 may require the
 * embedded Servlet container (a Spring bean) to be operational.
 */
public class Fabric3BeanPostProcessor implements BeanPostProcessor {
    private Domain domain;
    private ServiceIntrospector serviceIntrospector;

    private List<Runnable> deployments = new ArrayList<>();

    public Fabric3BeanPostProcessor(Fabric fabric) {
        this.domain = fabric.getDomain();
        this.serviceIntrospector = fabric.getSystemService(ServiceIntrospector.class);
    }

    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (serviceIntrospector.exportsEndpoints(bean.getClass())) {
            // service is a remote endpoint; track it for deployment later when the Fabric3 remote transports are started
            deployments.add(() -> domain.deploy(beanName, bean));
        }
        return bean;
    }

    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /**
     * Callback to deploy Spring beans as remote endpoints.
     */
    public void deployFabricServices() {
        deployments.forEach(Runnable::run);
        deployments.clear();
    }
}
