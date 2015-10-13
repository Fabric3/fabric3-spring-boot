package org.fabric3.spring.boot;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.fabric3.api.node.Domain;
import org.fabric3.api.node.Fabric;
import org.fabric3.api.node.service.InjectorFactory;
import org.fabric3.api.node.service.ServiceIntrospector;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
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
    private InjectorFactory injectorFactory;

    private List<Runnable> deployments = new ArrayList<>();

    public Fabric3BeanPostProcessor(Fabric fabric) {
        this.domain = fabric.getDomain();
        this.serviceIntrospector = fabric.getSystemService(ServiceIntrospector.class);
        this.injectorFactory = fabric.getSystemService(InjectorFactory.class);
    }

    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (serviceIntrospector.exportsEndpoints(bean.getClass())) {
            // service is a remote endpoint; track it for deployment later when the Fabric3 remote transports are started
            deployments.add(() -> domain.deploy(beanName, bean));
        }
        Class<?> beanClass = bean.getClass();
        Map<AccessibleObject, Supplier<Object>> injectors = injectorFactory.getInjectors(beanClass);
        for (Map.Entry<AccessibleObject, Supplier<Object>> entry : injectors.entrySet()) {
            try {
                if (entry.getKey() instanceof Method) {
                    Method method = (Method) entry.getKey();
                    method.setAccessible(true);
                    method.invoke(bean, entry.getValue().get());
                } else if (entry.getKey() instanceof Field) {
                    Field field = (Field) entry.getKey();
                    field.setAccessible(true);
                    field.set(bean, entry.getValue().get());

                }
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new BeanInitializationException("Error injecting " + beanClass.getName(), e);
            }
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
