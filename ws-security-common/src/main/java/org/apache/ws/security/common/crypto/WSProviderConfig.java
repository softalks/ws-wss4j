/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ws.security.common.crypto;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.Provider;
import java.security.Security;
import java.util.Enumeration;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.ws.security.common.util.Loader;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.I18n;
import org.apache.xml.security.utils.XMLUtils;


/**
 * Configure Crypto providers.
 */
public final class WSProviderConfig {
    
    private static final org.apache.commons.logging.Log LOG = 
        org.apache.commons.logging.LogFactory.getLog(WSProviderConfig.class);
    
    /**
     * a static boolean flag that determines whether default JCE providers
     * should be added at the time of construction.
     *
     * These providers, and the order in which they are added, can interfere
     * with some JVMs (such as IBMs).
     */
    private static boolean addJceProviders = true;
    
    /**
     * a boolean flag to record whether we have already been statically
     * initialized.  This flag prevents repeated and unnecessary calls
     * to static initialization code at construction time.
     */
    private static boolean staticallyInitialized = false;
    
    public static synchronized void init() {
        if (!staticallyInitialized) {
            if (addJceProviders) {
                initializeResourceBundles();
                setXmlSecIgnoreLineBreak();
                AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                    public Boolean run() {
                        addXMLDSigRIInternal();
                        addJceProvider("BC", "org.bouncycastle.jce.provider.BouncyCastleProvider");
                        return true;
                    }
                });
            }
            staticallyInitialized = true;
        }
    }
    
    /**
     * Set the value of the internal addJceProviders flag.  This flag
     * turns on (or off) automatic registration of known JCE providers
     * that provide necessary cryptographic algorithms for use with WSS4J.
     * By default, this flag is true.  You may wish (or need) to initialize 
     * the JCE manually, e.g., in some JVMs.
     */
    public static void setAddJceProviders(boolean value) {
        addJceProviders = value;
    }
    
    public static void setXmlSecIgnoreLineBreak() {
        //really need to make sure ignoreLineBreaks is set to
        boolean wasSet = false;
        try {
            // Don't override if it was set explicitly
            wasSet = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                public Boolean run() {
                    String lineBreakPropName = "org.apache.xml.security.ignoreLineBreaks";
                    if (System.getProperty(lineBreakPropName) == null) {
                        System.setProperty(lineBreakPropName, "true");
                        return false;
                    }
                    return true; 
                }
            });
        } catch (Throwable t) { //NOPMD
            //ignore
        }
        org.apache.xml.security.Init.init();
        if (!wasSet) {
            try {
                AccessController.doPrivileged(new PrivilegedExceptionAction<Boolean>() {
                    public Boolean run() throws Exception {
                        Field f = XMLUtils.class.getDeclaredField("ignoreLineBreaks");
                        f.setAccessible(true);
                        f.set(null, Boolean.TRUE);
                        return false;
                    }
                });
            } catch (Throwable t) { //NOPMD
                //ignore
            }
        }
    }
    
    private static void addXMLDSigRIInternal() {
        addJceProvider("ApacheXMLDSig", SantuarioUtil.getSantuarioProvider());
    }

    private static void initializeResourceBundles() {

        ResourceBundle resourceBundle = new ResourceBundle() {

            private final ResourceBundle wss4jSecResourceBundle = ResourceBundle.getBundle("messages.wss4j_errors");
            private final ResourceBundle xmlSecResourceBundle = ResourceBundle.getBundle(Constants.exceptionMessagesResourceBundleBase);

            @Override
            protected Object handleGetObject(String key) {
                Object value = null;
                try {
                    value = wss4jSecResourceBundle.getObject(key);
                } catch (MissingResourceException e) {
                    try {
                        value = xmlSecResourceBundle.getObject(key);
                    } catch (MissingResourceException ex) { //NOPMD
                        //ignore
                    }
                }
                return value;
            }

            @Override
            public Enumeration<String> getKeys() {
                throw new UnsupportedOperationException("getKeys not supported");
            }
        };
        I18n.init(resourceBundle);
    }

    /**
     * Add a new JCE security provider to use for WSS4J, of the specified name and class. Return
     * either the name of the previously loaded provider, the name of the new loaded provider, or
     * null if there's an exception in loading the provider. Add the provider either after the SUN
     * provider (see WSS-99), or the IBMJCE provider. Otherwise fall back to the old behaviour of
     * inserting the provider in position 2.
     * 
     * @param name
     *            The name string of the provider (this may not be the real name of the provider)
     * @param className
     *            Name of the class the implements the provider. This class must
     *            be a subclass of <code>java.security.Provider</code>
     * 
     * @return Returns the actual name of the provider that was loaded
     */
    public static String addJceProvider(String name, String className) {
        Provider currentProvider = Security.getProvider(name);
        if (currentProvider == null) {
            try {
                Class<? extends Provider> clazz = Loader.loadClass(className, false, Provider.class);
                Provider provider = clazz.newInstance();
                return addJceProvider(name, provider);
            } catch (Throwable t) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("The provider " + name + " could not be added: " + t.getMessage(), t);
                }
                return null;
            }
        }
        return currentProvider.getName();
    }
    
    /**
     * Add a new JCE security provider to use for WSS4J, of the specified name and class. Return
     * either the name of the previously loaded provider, the name of the new loaded provider, or
     * null if there's an exception in loading the provider. Add the provider either after the SUN
     * provider (see WSS-99), or the IBMJCE provider. Otherwise fall back to the old behaviour of
     * inserting the provider in position 2.
     * 
     * @param name
     *            The name string of the provider (this may not be the real name of the provider)
     * @param provider
     *            A subclass of <code>java.security.Provider</code>
     * 
     * @return Returns the actual name of the provider that was loaded
     */
    public static String addJceProvider(String name, Provider provider) {
        Provider currentProvider = Security.getProvider(name);
        if (currentProvider == null) {
            try {
                //
                // Install the provider after the SUN provider (see WSS-99)
                // Otherwise fall back to the old behaviour of inserting
                // the provider in position 2. For AIX, install it after
                // the IBMJCE provider.
                //
                int ret = 0;
                Provider[] provs = Security.getProviders();
                for (int i = 0; i < provs.length; i++) {
                    if ("SUN".equals(provs[i].getName())
                        || "IBMJCE".equals(provs[i].getName())) {
                        ret = Security.insertProviderAt(provider, i + 2);
                        break;
                    }
                }
                if (ret == 0) {
                    ret = Security.insertProviderAt(provider, 2);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                        "The provider " + provider.getName() + " - "
                         + provider.getVersion() + " was added at position: " + ret
                    );
                }
                return provider.getName();
            } catch (Throwable t) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("The provider " + name + " could not be added: " + t.getMessage(), t);
                }
                return null;
            }
        }
        return currentProvider.getName();
    }
    
    
    /**
     * Add a new JCE security provider to use for WSS4J, of the specified name and class. Return
     * either the name of the previously loaded provider, the name of the new loaded provider, or
     * null if there's an exception in loading the provider. Append the provider to the provider
     * list.
     * 
     * @param name
     *            The name string of the provider (this may not be the real name of the provider)
     * @param className
     *            Name of the class the implements the provider. This class must
     *            be a subclass of <code>java.security.Provider</code>
     * 
     * @return Returns the actual name of the provider that was loaded
     */
    public static String appendJceProvider(String name, String className) {
        Provider currentProvider = Security.getProvider(name);
        if (currentProvider == null) {
            try {
                Class<? extends Provider> clazz = Loader.loadClass(className, false, Provider.class);
                Provider provider = clazz.newInstance();
                
                int ret = Security.addProvider(provider);
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                        "The provider " + provider.getName() 
                        + " was added at position: " + ret
                    );
                }
                return provider.getName();
            } catch (Throwable t) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("The provider " + name + " could not be added: " + t.getMessage(), t);
                }
                return null;
            }
        }
        return currentProvider.getName();
    }
    
    /**
     * Add a new JCE security provider to use for WSS4J, of the specified name and class. Return
     * either the name of the previously loaded provider, the name of the new loaded provider, or
     * null if there's an exception in loading the provider. Append the provider to the provider
     * list.
     * 
     * @param name
     *            The name string of the provider (this may not be the real name of the provider)
     * @param provider
     *            A subclass of <code>java.security.Provider</code>
     * 
     * @return Returns the actual name of the provider that was loaded
     */
    public static String appendJceProvider(String name, Provider provider) {
        Provider currentProvider = Security.getProvider(name);
        if (currentProvider == null) {
            try {
                int ret = Security.addProvider(provider);
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                        "The provider " + provider.getName() 
                        + " was added at position: " + ret
                    );
                }
                return provider.getName();
            } catch (Throwable t) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("The provider " + name + " could not be added: " + t.getMessage(), t);
                }
                return null;
            }
        }
        return currentProvider.getName();
    }
    
}