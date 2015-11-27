//
//  ========================================================================
//  Copyright (c) 1995-2015 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.webapp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.TopologicalSort;
import org.eclipse.jetty.util.annotation.Name;

/* ------------------------------------------------------------------------------- */
/** Base Class for WebApplicationContext Configuration.
 * This class can be extended to customize or extend the configuration
 * of the WebApplicationContext. 
 */
public interface Configuration 
{
    public final static String ATTR="org.eclipse.jetty.webapp.configuration";

    /* ------------------------------------------------------------------------------- */
    /** Get the name of the configuration used for a {@link TopologicalSort}
     * @return The name of the Configuration (defaults to classname)
     */
    public default String getName() { return this.getClass().getName(); } 

    /* ------------------------------------------------------------------------------- */
    /** Get known Configuration Dependencies.
     * @return The names of Configurations that {@link TopologicalSort} must order 
     * before this configuration.
     */
    public default List<String> getBeforeThis() { return Collections.emptyList(); }

    /* ------------------------------------------------------------------------------- */
    /** Get known Configuration Dependents.
     * @return The names of Configurations that {@link TopologicalSort} must order 
     * after this configuration.
     */
    public default List<String> getAfterThis(){ return Collections.emptyList(); }
    
    /* ------------------------------------------------------------------------------- */
    /** Set up for configuration.
     * <p>
     * Typically this step discovers configuration resources
     * @param context The context to configure
     * @throws Exception if unable to pre configure
     */
    public void preConfigure (WebAppContext context) throws Exception;
    
    
    /* ------------------------------------------------------------------------------- */
    /** Configure WebApp.
     * <p>
     * Typically this step applies the discovered configuration resources to
     * either the {@link WebAppContext} or the associated {@link MetaData}.
     * @param context The context to configure
     * @throws Exception if unable to configure
     */
    public void configure (WebAppContext context) throws Exception;
    
    
    /* ------------------------------------------------------------------------------- */
    /** Clear down after configuration.
     * @param context The context to configure
     * @throws Exception if unable to post configure
     */
    public void postConfigure (WebAppContext context) throws Exception;
    
    /* ------------------------------------------------------------------------------- */
    /** DeConfigure WebApp.
     * This method is called to undo all configuration done. This is
     * called to allow the context to work correctly over a stop/start cycle
     * @param context The context to configure
     * @throws Exception if unable to deconfigure
     */
    public void deconfigure (WebAppContext context) throws Exception;

    /* ------------------------------------------------------------------------------- */
    /** Destroy WebApp.
     * This method is called to destroy a webappcontext. It is typically called when a context 
     * is removed from a server handler hierarchy by the deployer.
     * @param context The context to configure
     * @throws Exception if unable to destroy
     */
    public void destroy (WebAppContext context) throws Exception;
    

    /* ------------------------------------------------------------------------------- */
    /** Clone configuration instance.
     * <p>
     * Configure an instance of a WebAppContext, based on a template WebAppContext that 
     * has previously been configured by this Configuration.
     * @param template The template context
     * @param context The context to configure
     * @throws Exception if unable to clone
     */
    public void cloneConfigure (WebAppContext template, WebAppContext context) throws Exception;
    
    public class ClassList 
    {        
        List<Configuration> _instances = new ArrayList<>();
        
        /* ------------------------------------------------------------ */
        /** Get/Set/Create the server default Configuration ClassList.
         * <p>Get the class list from: a Server bean; or the attribute (which can
         * either be a ClassList instance or an String[] of class names); or a new instance
         * with default configuration classes.</p>
         * <p>This method also adds the obtained ClassList instance as a dependent bean
         * on the server and clears the attribute</p>
         * @param server The server the default is for
         * @return the server default ClassList instance of the configuration classes for this server. Changes to this list will change the server default instance.
         */
        public static ClassList setServerDefault(Server server) throws ClassNotFoundException, InstantiationException, IllegalAccessException
        {
            ClassList cl=server.getBean(ClassList.class);
            if (cl!=null)
                return cl;
            cl=serverDefault(server);
            server.addBean(cl);
            server.setAttribute(Configuration.ATTR,null);
            return cl;
        }

        /* ------------------------------------------------------------ */
        /** Get/Create the server default Configuration ClassList.
         * <p>Get the class list from: a Server bean; or the attribute (which can
         * either be a ClassList instance or an String[] of class names); or a new instance
         * with default configuration classes.
         * @param server The server the default is for
         * @return A copy of the server default ClassList instance of the configuration classes for this server. Changes to the returned list will not change the server default.
         */
        public static ClassList serverDefault(Server server)
        {
            ClassList cl=null;
            if (server!=null)
            {
                cl= server.getBean(ClassList.class);
                if (cl!=null)
                    return new ClassList(cl);
                Object attr = server.getAttribute(Configuration.ATTR);
                if (attr instanceof ClassList)
                    return new ClassList((ClassList)attr);
                if (attr instanceof String[])
                    return new ClassList((String[])attr);
            }
            return new ClassList();
        }
        
        public ClassList()
        {
            this(WebAppContext.DEFAULT_CONFIGURATION_CLASSES);
        }
        
        private Configuration newConfiguration(String classname)
        {
            try
            {
                Class<Configuration> clazz = Loader.loadClass(classname);
                return clazz.newInstance();
            }
            catch (ClassNotFoundException | InstantiationException | IllegalAccessException e)
            {
                throw new RuntimeException(e);
            }
        }
        
        public ClassList(String[] classes)
        {
            for (String c:classes)
                _instances.add(newConfiguration(c));
        }

        public ClassList(List<String> classes)
        {
            for (String c:classes)
                _instances.add(newConfiguration(c));
        }

        public ClassList(Configuration.ClassList classlist)
        {
            _instances.addAll(classlist._instances);
        }

        public void add(@Name("configClass")String... configClass)
        {
            loop: for (String c : configClass)
            {
                Configuration configuration = newConfiguration(c);
                ListIterator<Configuration> i = _instances.listIterator();
                while(i.hasNext())
                {
                    Configuration next = i.next();
                    if (next.getName().equals(configuration.getName()))
                    {
                        i.set(configuration);
                        continue loop;
                    }
                }
                _instances.add(configuration);
            }
        }
        
        public void addIfAbsent(@Name("configClass")String... configClass)
        {
            for (String c : configClass)
            {
                Configuration configuration = newConfiguration(c);
                
                if (!_instances.stream().map(i->{return i.getName();}).anyMatch(n->{return configuration.getName().equals(n);}))
                    _instances.add(configuration);
            }
        }
        
        public void addAfter(@Name("afterClass") String afterClass,@Name("configClass")String... configClass)
        {
            if (configClass!=null && afterClass!=null)
            {
                ListIterator<Configuration> iter = _instances.listIterator();
                while (iter.hasNext())
                {
                    Configuration c=iter.next();
                    if (afterClass.equals(c.getClass().getName()) || afterClass.equals(c.getName()))
                    {
                        for (String cc: configClass)
                            iter.add(newConfiguration(cc));
                        return;
                    }
                }
            }
            throw new IllegalArgumentException("afterClass '"+afterClass+"' not found in "+this);
        }

        public void addBefore(@Name("beforeClass") String beforeClass,@Name("configClass")String... configClass)
        {
            if (configClass!=null && beforeClass!=null)
            {
                ListIterator<Configuration> iter = _instances.listIterator();
                while (iter.hasNext())
                {
                    Configuration c=iter.next();
                    if (beforeClass.equals(c.getClass().getName()) || beforeClass.equals(c.getName()))
                    {
                        iter.previous();
                        for (String cc: configClass)
                            iter.add(newConfiguration(cc));
                        return;
                    }
                }
            }
            
            throw new IllegalArgumentException("beforeClass '"+beforeClass+"' not found in "+this);
        }

        public int size()
        {
            return _instances.size();
        }

        public String[] toArray(String[] asArray)
        {
            return _instances.stream().map(i->{return i.getClass().getName();}).toArray(n->{return new String[n];});
        }

        public List<Configuration> getConfigurations()
        {
            return _instances.stream().map(i->{return newConfiguration(i.getClass().getName());}).collect(Collectors.toList());
        }
     
        @Override
        public String toString()
        {
            return getConfigurations().toString();
        }
    }
}
