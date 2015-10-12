package org.fabric3.spring.boot;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Wrapper for the Spring embedded servlet container context.
 */
public class Fabric3ServletConfig implements ServletConfig {
    private ServletContext servletContext;

    public Fabric3ServletConfig(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public String getServletName() {
        return "Fabric3";
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    public String getInitParameter(String s) {
        return null;
    }

    public Enumeration<String> getInitParameterNames() {
        return Collections.emptyEnumeration();
    }
}
