package org.fabric3.spring.boot;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.util.Map;

import org.fabric3.api.node.Fabric;
import org.fabric3.api.node.FabricException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

/**
 * Configures the Spring application context with Fabric3 services.
 *
 *
 * Usage is as follows:
 *
 * <pre>
 * 1. A SpringApplication is created.
 *
 * 2. The Fabric3 container is bootstrapped and configured with extensions.
 *
 * 3. The SpringApplication is intialized with the Fabric3 container using {@link #initialize(SpringApplication, Fabric, Map)}.
 *
 * 4. The {@link #startFabric(ApplicationContext, Fabric)} method is called to deploy endpoints and start receiving remote requests.
 * </pre>
 *
 * The following demonstrates how this sequence is performed:
 *
 * <pre>
 * <code>
 *
 *        // create the Spring boot application
 *        SpringApplication application = new SpringApplication(StartSample.class);
 *
 *        Fabric fabric = Bootstrap.initialize();
 *
 *        // enable the rest extension
 *        fabric.addProfile("rs");
 *
 *        // create config properties, including the context path HTTP/S services are published under
 *        String contextPath = System.getProperty("fabric3.context.path", "/fabric3");
 *        Map<String, Object> properties = Collections.<String, Object>singletonMap("http.context", contextPath);
 *
 *        // configure the application context with Fabric3 services and start the core Fabric3 runtime
 *        SpringApplicationConfigurer.initialize(application, fabric, properties);
 *
 *        // run the application
 *        ApplicationContext context = application.run(args);
 *
 *        // enable remote endpoints
 *        SpringApplicationConfigurer.startFabric(context, fabric);
 *
 * </code>
 * </pre>
 */
public class SpringApplicationConfigurer {
    private static String CONTEXT_PATH = "";
    private static Servlet DISPATCHER_INSTANCE;

    /**
     * Initializes the Spring application with Fabric3 services. This method must be called prior to running the Spring application.
     *
     * @param application the application
     * @param fabric      the Fabric3 runtime
     * @param properties  initialization properties. Currently, one property is supported: {@code fabric3.context.path} is used to configure the context path
     *                    for HTTP/S endpoints.
     */
    public static void initialize(SpringApplication application, Fabric fabric, Map<String, Object> properties) {

        DISPATCHER_INSTANCE = fabric.createTransportDispatcher(Servlet.class, properties);
        fabric.startRuntime();

        CONTEXT_PATH = (String) properties.getOrDefault("http.context", "/fabric3");

        final Fabric3BeanPostProcessor postProcessor = new Fabric3BeanPostProcessor(fabric);

        application.addListeners(new ApplicationListener<ApplicationEvent>() {
            public void onApplicationEvent(ApplicationEvent event) {
                if (event instanceof ApplicationPreparedEvent) {
                    ConfigurableApplicationContext context = ((ApplicationPreparedEvent) event).getApplicationContext();
                    ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
                    if (!(beanFactory instanceof BeanDefinitionRegistry)) {
                        throw new UnsupportedOperationException("BeanFactory must implements BeanDefinitionRegistry: " + beanFactory.getClass());
                    }
                    BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

                    beanFactory.registerSingleton(SpringApplicationConfigurer.class.getName(), new SpringApplicationConfigurer());

                    GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
                    beanDefinition.setBeanClassName(SpringApplicationConfigurer.class.getName());
                    beanDefinition.setFactoryMethodName("createServletDispatcher");
                    beanDefinition.setLazyInit(false);

                    registry.registerBeanDefinition("Fabric3HttpDispatcher", beanDefinition);

                    beanFactory.registerSingleton("Fabric3PostProcessor", postProcessor);
                } else if (event instanceof ContextClosedEvent) {
                    System.out.println("Stopping Fabric3");
                    fabric.stop();
                }
            }
        });

    }

    /**
     * Starts remote endpoints and receiving requests for the application context.
     *
     * @param context the application context
     * @param fabric  the Fabric3 runtime
     */
    public static void startFabric(ApplicationContext context, Fabric fabric) {
        final ServletContext servletContext = context.getBean(ServletContext.class);
        try {
            // Configure the transport dispatcher with the servlet context of the Spring servlet container. This must be done here as the embedded servlet
            // container is a Spring bean and not available until after the application context has been fully initialized.
            DISPATCHER_INSTANCE.init(new Fabric3ServletConfig(servletContext));
        } catch (ServletException e) {
            throw new FabricException(e);
        }

        fabric.startTransports();

        // deploy the remote services introspected during initialization of the application context
        context.getBean(Fabric3BeanPostProcessor.class).deployFabricServices();

    }

    /**
     * Creates a registration bean for the Fabric3 servlet transport dispatcher that addes it the the embedded Spring servlet container.
     *
     * The F3 dispatcher acts as a bridge from the embedded Spring Boot servlet container to the Fabric3 servlet host used by bindings to receive HTTP
     * requests.
     *
     * This method is a hack as Spring does not have a way to register beans which are introspected and wired. A bean instance is used as Fabric3 must create
     * the dispatcher and pass it to the application context.  The singleton registration method exposed by Spring does not introspect beans, which is required
     * for Servlet beans. Instead, the workaround is to register a bean definition when the application context is created that points to this factory method.
     * The factory method has access to the Fabric3 dispatcher servlet which is registered with the embedded servlet container.
     *
     * @return the registration bean
     */
    public static ServletRegistrationBean createServletDispatcher() {
        if (DISPATCHER_INSTANCE == null) {
            throw new IllegalStateException("Configurer not initialized with a dispatcher");
        }
        return new ServletRegistrationBean(DISPATCHER_INSTANCE, CONTEXT_PATH + "/*");
    }

}
