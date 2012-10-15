/*
QuiXProc: efficient evaluation of XProc Pipelines.
Copyright (C) 2011-2012 Innovimax
2008-2012 Mark Logic Corporation.
Portions Copyright 2007 Sun Microsystems, Inc.
All rights reserved.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 3
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

package com.xmlcalabash.core;

import innovimax.quixproc.codex.util.PipedDocument;
import innovimax.quixproc.codex.util.QConfig;
import innovimax.quixproc.codex.util.StepContext;
import innovimax.quixproc.codex.util.Tracing;
import innovimax.quixproc.codex.util.Waiting;

import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Vector;
import java.util.logging.Logger;

import javax.xml.transform.URIResolver;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import org.apache.commons.httpclient.Cookie;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import com.xmlcalabash.config.XProcConfigurer;
import com.xmlcalabash.functions.BaseURI;
import com.xmlcalabash.functions.Cwd;
import com.xmlcalabash.functions.IterationPosition;
import com.xmlcalabash.functions.IterationSize;
import com.xmlcalabash.functions.ResolveURI;
import com.xmlcalabash.functions.StepAvailable;
import com.xmlcalabash.functions.SystemProperty;
import com.xmlcalabash.functions.ValueAvailable;
import com.xmlcalabash.functions.VersionAvailable;
import com.xmlcalabash.functions.XPathVersionAvailable;
import com.xmlcalabash.io.ReadableData;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.model.DeclareStep;
import com.xmlcalabash.model.Parser;
import com.xmlcalabash.model.PipelineLibrary;
import com.xmlcalabash.runtime.XLibrary;
import com.xmlcalabash.runtime.XPipeline;
import com.xmlcalabash.runtime.XRootStep;
import com.xmlcalabash.runtime.XStep;
import com.xmlcalabash.util.DefaultXProcConfigurer;
import com.xmlcalabash.util.DefaultXProcMessageListener;
import com.xmlcalabash.util.JSONtoXML;
import com.xmlcalabash.util.StepErrorListener;
import com.xmlcalabash.util.URIUtils;
import com.xmlcalabash.util.XProcURIResolver;
// Innovimax: new import
// Innovimax: new import
// Innovimax: new import
// Innovimax: new import
// Innovimax: new import
// Innovimax: new import

/**
 *
 * @author ndw
 */
public class XProcRuntime {
    protected Logger logger = Logger.getLogger("com.xmlcalabash");
    private Processor processor = null;
    private Parser parser = null;
    private XProcURIResolver uriResolver = null;
    private XProcConfiguration config = null;
    private Vector<XStep> reported = new Vector<XStep> ();
    private QName errorCode = null;
    private XdmNode errorNode = null;
    private String errorMessage = null;
    private Hashtable<QName, DeclareStep> declaredSteps = new Hashtable<QName,DeclareStep> ();
    private DeclareStep pipeline = null;
    private XPipeline xpipeline = null;
    private Vector<Throwable> errors = null;
    private static String episode = null;
    private Hashtable<String,Vector<XdmNode>> collections = null;
    private URI staticBaseURI = null;
    private boolean allowGeneralExpressions = true;
    private boolean allowXPointerOnText = true;
    private boolean transparentJSON = false;
    private String jsonFlavor = JSONtoXML.MARKLOGIC;
    private boolean useXslt10 = false;
    private XProcData xprocData = null;
    private Logger log = null;
    private XProcMessageListener msgListener = null;
    private PipelineLibrary standardLibrary = null;
    private XLibrary xStandardLibrary = null;
    private Hashtable<String,Vector<Cookie>> cookieHash = new Hashtable<String,Vector<Cookie>> ();
    private XProcConfigurer configurer = null;
    private String htmlParser = null;

    public XProcRuntime(XProcConfiguration config) {
        this.config = config;
        processor = config.getProcessor();

        if (config.xprocConfigurer != null) {
            try {
                String className = config.xprocConfigurer;
                Constructor constructor = Class.forName(className).getConstructor(XProcRuntime.class);
                configurer = (XProcConfigurer) constructor.newInstance(this);
            } catch (Exception e) {
                throw new XProcException(e);
            }
        } else {
            configurer = new DefaultXProcConfigurer(this);
        }

        xprocData = new XProcData(this);

        processor.registerExtensionFunction(new Cwd(this));
        processor.registerExtensionFunction(new BaseURI(this));
        processor.registerExtensionFunction(new ResolveURI(this));
        processor.registerExtensionFunction(new SystemProperty(this));
        processor.registerExtensionFunction(new StepAvailable(this));
        processor.registerExtensionFunction(new IterationSize(this));
        processor.registerExtensionFunction(new IterationPosition(this));
        processor.registerExtensionFunction(new ValueAvailable(this));
        processor.registerExtensionFunction(new VersionAvailable(this));
        processor.registerExtensionFunction(new XPathVersionAvailable(this));

        log = Logger.getLogger(this.getClass().getName());

        Configuration saxonConfig = processor.getUnderlyingConfiguration();
        uriResolver = new XProcURIResolver(this);
        saxonConfig.setURIResolver(uriResolver);
        staticBaseURI = URIUtils.cwdAsURI();

        try {
            if (config.uriResolver != null) {
                uriResolver.setUnderlyingURIResolver((URIResolver) Class.forName(config.uriResolver).newInstance());
            }
            if (config.entityResolver != null) {
                uriResolver.setUnderlyingEntityResolver((EntityResolver) Class.forName(config.entityResolver).newInstance());
            }

            if (config.errorListener != null) {
                msgListener = (XProcMessageListener) Class.forName(config.errorListener).newInstance();
            } else {
                msgListener = new DefaultXProcMessageListener();
            }
        } catch (Exception e) {
            throw new XProcException(e);
        }

        processor.getUnderlyingConfiguration().setURIResolver(uriResolver);

        StepErrorListener errListener = new StepErrorListener(this);
        saxonConfig.setErrorListener(errListener);

        allowGeneralExpressions = config.extensionValues;
        allowXPointerOnText = config.xpointerOnText;
        transparentJSON = config.transparentJSON;
        jsonFlavor = config.jsonFlavor;
        useXslt10 = config.useXslt10;

        for (String className : config.extensionFunctions) {
            try {
                ExtensionFunctionDefinition def = (ExtensionFunctionDefinition) Class.forName(className).newInstance();
                finer(null, null, "Instantiated: " + className);
                processor.registerExtensionFunction(def);
            } catch (NoClassDefFoundError ncdfe) {
                finer(null, null, "Failed to instantiate extension function: " + className);
            } catch (Exception e) {
                finer(null, null, "Failed to instantiate extension function: " + className);
            }
        }

        htmlParser = config.htmlParser;

        reset();
    }

    public XProcRuntime(XProcRuntime runtime) {
        processor = runtime.processor;
        parser = runtime.parser;
        uriResolver = runtime.uriResolver;
        config = runtime.config;
        staticBaseURI = runtime.staticBaseURI;
        useXslt10 = runtime.useXslt10;
        log = runtime.log;
        msgListener = runtime.msgListener;
        standardLibrary = runtime.standardLibrary;
        xStandardLibrary = runtime.xStandardLibrary;
        cookieHash = runtime.cookieHash;
        configurer = runtime.configurer;
        allowGeneralExpressions = runtime.allowGeneralExpressions;
        allowXPointerOnText = runtime.allowXPointerOnText;
        transparentJSON = runtime.transparentJSON;
        jsonFlavor = runtime.jsonFlavor;
    }

    public XProcConfigurer getConfigurer() {
        return configurer;
    }

    public void setConfigurer(XProcConfigurer configurer) {
        this.configurer = configurer;
    }

    public XProcData getXProcData() {
        return xprocData;
    }

    public boolean getDebug() {
        return config.debug;
    }

    public URI getStaticBaseURI() {
        return staticBaseURI;
    }

    public String getSendmailHost() {
        return config.mailHost;
    }

    public String getSendmailPort() {
        return config.mailPort;
    }

    public String getSendmailUsername() {
        return config.mailUser;
    }

    public String getSendmailPassword() {
        return config.mailPass;
    }

    public void setURIResolver(URIResolver resolver) {
        uriResolver.setUnderlyingURIResolver(resolver);
    }

    public void setEntityResolver(EntityResolver resolver) {
        uriResolver.setUnderlyingEntityResolver(resolver);
    }

    public XProcURIResolver getResolver() {
        return uriResolver;
    }

    public XProcMessageListener getMessageListener() {
      return msgListener;
    }

    public void setMessageListener(XProcMessageListener listener) {
      msgListener = listener;
    }
    
    public void setCollection(URI href, Vector<XdmNode> docs) {
        if (collections == null) {
            collections = new Hashtable<String,Vector<XdmNode>> ();
        }
        collections.put(href.toASCIIString(), docs);
    }

    public Vector<XdmNode> getCollection(URI href) {
        if (collections == null) {
            return null;
        }
        if (collections.containsKey(href.toASCIIString())) {
            return collections.get(href.toASCIIString());
        }
        return null;
    }

    public boolean getSafeMode() {
        return config.safeMode;
    }

    public boolean getAllowGeneralExpressions() {
        return allowGeneralExpressions;
    }

    public boolean getAllowXPointerOnText() {
        return allowXPointerOnText;
    }

    public boolean transparentJSON() {
        return transparentJSON;
    }

    public String jsonFlavor() {
        return jsonFlavor;
    }

    public String htmlParser() {
        return htmlParser;
    }

    public boolean getUseXslt10Processor() {
        return useXslt10;
    }

    public void cache(XdmNode doc, URI baseURI) {
        uriResolver.cache(doc, baseURI);
    }

    public XProcConfiguration getConfiguration() {
        return config;
    }

    public Parser getParser() {
        return parser;
    }

    public String getEpisode() {
        if (episode == null) {
            MessageDigest digest = null;
            GregorianCalendar calendar = new GregorianCalendar();
            try {
                digest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException nsae) {
                throw XProcException.dynamicError(36);
            }

            byte[] hash = digest.digest(calendar.toString().getBytes());
            episode = "CB";
            for (byte b : hash) {
                episode = episode + Integer.toHexString(b & 0xff);
            }
        }

        return episode;
    }

    public String getLanguage() {
        // Translate _ to - for compatibility with xml:lang
        return Locale.getDefault().toString().replace('_', '-');

    }

    public String getProductName() {
        return "XML Calabash";
    }

    public String getProductVersion() {
        return XProcConstants.XPROC_VERSION;
    }

    // Innovimax: modified function
    public String getVendor() {
        return "innovimax";
    }    

    public String getVendorURI() {
        return "http://xmlcalabash.com/";
    }

    public String getXProcVersion() {
        return "1.0";
    }

    public String getXPathVersion() {
        return "2.0";
    }

    public boolean getPSVISupported() {
        return config.schemaAware;
    }

    public XLibrary getStandardLibrary() {
        if (xStandardLibrary == null) {
            xStandardLibrary = new XLibrary(this, standardLibrary);

            if (errorCode != null) {
                throw new XProcException(errorCode, errorMessage);
            }
        }

        return xStandardLibrary;
    }

    private void reset() {
        reported = new Vector<XStep> ();
        errorCode = null;
        errorMessage = null;
        declaredSteps = new Hashtable<QName,DeclareStep> ();
        //explicitDeclarations = false;
        pipeline = null;
        xpipeline = null;
        errors = null;
        episode = null;
        collections = null;
        cookieHash = new Hashtable<String,Vector<Cookie>> ();

        xprocData = new XProcData(this);

        parser = new Parser(this);
        try {
            // FIXME: I should *do* something with these libraries, shouldn't I?
            standardLibrary = parser.loadStandardLibrary();
            if (errorCode != null) {
                throw new XProcException(errorCode, errorMessage);
            }
        } catch (FileNotFoundException ex) {
            throw new XProcException(XProcConstants.dynamicError(9), ex);
        } catch (URISyntaxException ex) {
            throw new XProcException(XProcConstants.dynamicError(9), ex);
        } catch (SaxonApiException ex) {
            throw new XProcException(XProcConstants.dynamicError(9), ex);
        }
    }

    // FIXME: This design sucks
    public XPipeline load(String pipelineURI) throws SaxonApiException {
        for (String map : config.loaders.keySet()) {
            boolean data = map.startsWith("data:");
            String pattern = map.substring(5);
            if (pipelineURI.matches(pattern)) {
                return runPipelineLoader(pipelineURI, config.loaders.get(map), data);
            }
        }

        try {
            return _load(pipelineURI);
        } catch (SaxonApiException sae) {
            error(sae);
            throw sae;
        } catch (XProcException xe) {
            error(xe);
            throw xe;
        }
    }

    private XPipeline _load(String pipelineURI) throws SaxonApiException {
        reset();
        configurer.getXMLCalabashConfigurer().configRuntime(this);
        pipeline = parser.loadPipeline(pipelineURI);
        if (errorCode != null) {
            throw new XProcException(errorCode, errorMessage);
        }

        XRootStep root = new XRootStep(this);
        DeclareStep decl = pipeline.getDeclaration();
        decl.setup();

        if (errorCode != null) {
            throw new XProcException(errorCode, errorNode, errorMessage);
        }

        xpipeline = new XPipeline(this, pipeline, root);
        xpipeline.instantiate(decl);

        if (errorCode != null) {
            throw new XProcException(errorCode, errorMessage);
        }

        return xpipeline;
    }

    // FIXME: This design sucks
    public XPipeline use(XdmNode p_pipeline) throws SaxonApiException {
        try {
            return _use(p_pipeline);
        } catch (SaxonApiException sae) {
            error(sae);
            throw sae;
        } catch (XProcException xe) {
            error(xe);
            throw xe;
        }
    }
    private XPipeline _use(XdmNode p_pipeline) throws SaxonApiException {
        reset();
        configurer.getXMLCalabashConfigurer().configRuntime(this);
        pipeline = parser.usePipeline(p_pipeline);
        if (errorCode != null) {
            throw new XProcException(errorCode, errorMessage);
        }

        XRootStep root = new XRootStep(this);
        DeclareStep decl = pipeline.getDeclaration();
        decl.setup();

        if (errorCode != null) {
            throw new XProcException(errorCode, errorMessage);
        }

        xpipeline = new XPipeline(this, pipeline, root);
        xpipeline.instantiate(decl);

        if (errorCode != null) {
            throw new XProcException(errorCode, errorMessage);
        }

        return xpipeline;
    }

    // FIXME: This design sucks
    public XLibrary loadLibrary(String libraryURI) throws SaxonApiException {
        for (String map : config.loaders.keySet()) {
            boolean data = map.startsWith("data:");
            String pattern = map.substring(5);
            if (libraryURI.matches(pattern)) {
                return runLibraryLoader(libraryURI, config.loaders.get(map), data);
            }
        }

        try {
            return _loadLibrary(libraryURI);
        } catch (SaxonApiException sae) {
            error(sae);
            throw sae;
        } catch (XProcException xe) {
            error(xe);
            throw xe;
        }
    }

    private XLibrary _loadLibrary(String libraryURI) throws SaxonApiException {

        PipelineLibrary plibrary = parser.loadLibrary(libraryURI);
        if (errorCode != null) {
            throw new XProcException(errorCode, errorMessage);
        }

        XLibrary xlibrary = new XLibrary(this, plibrary);

        if (errorCode != null) {
            throw new XProcException(errorCode, errorMessage);
        }

        return xlibrary;
    }

    // FIXME: This design sucks
    public XLibrary useLibrary(XdmNode library) throws SaxonApiException {
        try {
            return _useLibrary(library);
        } catch (SaxonApiException sae) {
            error(sae);
            throw sae;
        } catch (XProcException xe) {
            error(xe);
            throw xe;
        }
    }

    private XLibrary _useLibrary(XdmNode library) throws SaxonApiException {
        PipelineLibrary plibrary = parser.useLibrary(library);
        if (errorCode != null) {
            throw new XProcException(errorCode, errorMessage);
        }

        XLibrary xlibrary = new XLibrary(this, plibrary);

        if (errorCode != null) {
            throw new XProcException(errorCode, errorMessage);
        }

        return xlibrary;
    }

    private XPipeline runPipelineLoader(String pipelineURI, String loaderURI, boolean data) throws SaxonApiException {
        XdmNode pipeDoc = runLoader(pipelineURI, loaderURI, data);
        return use(pipeDoc);
    }

    private XLibrary runLibraryLoader(String pipelineURI, String loaderURI, boolean data) throws SaxonApiException {
        XdmNode libDoc = runLoader(pipelineURI, loaderURI, data);
        return useLibrary(libDoc);
    }
    
    // Innovimax: modified function
    private XdmNode runLoader(String pipelineURI, String loaderURI, boolean data) throws SaxonApiException {
        XPipeline loader = null;

        // Innovimax: instantiate step context
        StepContext stepContext = new StepContext();
        
        try {
            loader = _load(loaderURI);
        } catch (SaxonApiException sae) {
            error(sae);
            throw sae;
        } catch (XProcException xe) {
            error(xe);
            throw xe;
        }

        XdmNode pipeDoc = null;
        if (data) {
            ReadableData rdata = new ReadableData(this, XProcConstants.c_result, getStaticBaseURI().resolve(pipelineURI).toASCIIString(), "text/plain");
            pipeDoc = rdata.read(stepContext);
        } else {
            pipeDoc = parse(pipelineURI, getStaticBaseURI().toASCIIString());
        }

        loader.clearInputs("source");
        loader.writeTo("source", pipeDoc);
        loader.run();
        ReadablePipe xformed = loader.readFrom("result");
        pipeDoc = xformed.read(stepContext);

        reset();
        return pipeDoc;
    }
    
    public Processor getProcessor() {
        return processor;        
    }

    public XdmNode parse(String uri, String base) {
        return parse(uri, base, false);
    }

    public XdmNode parse(String uri, String base, boolean validate) {
        return uriResolver.parse(uri, base, validate);
    }

    public XdmNode parse(InputSource isource) {
        return uriResolver.parse(isource);
    }

    public void declareStep(QName name, DeclareStep step) {
        if (declaredSteps.containsKey(name)) {
            throw new XProcException(step, "Duplicate declaration for " + name);
        } else {
            declaredSteps.put(name, step);
        }
    }

    public DeclareStep getBuiltinDeclaration(QName name) {
        if (declaredSteps.containsKey(name)) {
            return declaredSteps.get(name);
        } else {
            throw XProcException.staticError(44, null, "Unexpected step name: " + name);
        }
    }

    public void clearCookies(String key) {
        if (cookieHash.containsKey(key)) {
            cookieHash.get(key).clear();
        }
    }

    public void addCookie(String key, Cookie cookie) {
        if (!cookieHash.containsKey(key)) {
            cookieHash.put(key, new Vector<Cookie> ());
        }

        cookieHash.get(key).add(cookie);
    }

    public Vector<Cookie> getCookies(String key) {
        if (cookieHash.containsKey(key)) {
            return cookieHash.get(key);
        } else {
            return new Vector<Cookie> ();
        }
    }

    public QName getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    // ===========================================================
    // This logging stuff is still accessed through XProcRuntime
    // so that messages can be formatted in a common way and so
    // that errors can be trapped.

    public void error(XProcRunnable step, XdmNode node, String message, QName code) {
        if (errorCode == null) {
            errorCode = code;
            errorNode = node;
            errorMessage = message;
        }

        msgListener.error(step, node, message, code);
    }

    public void error(Throwable error) {
        msgListener.error(error);
    }

    public void warning(XProcRunnable step, XdmNode node, String message) {
        msgListener.warning(step, node, message);
    }

    public void warning(Throwable error) {
        msgListener.warning(error);
    }

    // Innovimax: modified function
    public void info(XProcRunnable step, XdmNode node, String message) {
        // Innovimax: testing performances
        if (!qconfig.isTraceAll()) return;         
        msgListener.info(step, node, message);
    }

    // Innovimax: modified function
    public void fine(XProcRunnable step, XdmNode node, String message) {
        // Innovimax: testing performances
        if (!qconfig.isTraceAll()) return;         
        msgListener.fine(step, node, message);
    }

    // Innovimax: modified function
    public void finer(XProcRunnable step, XdmNode node, String message) {
        // Innovimax: testing performances
        if (!qconfig.isTraceAll()) return;         
        msgListener.finer(step, node, message);
    }

    // Innovimax: modified function
    public void finest(XProcRunnable step, XdmNode node, String message) {
        // Innovimax: testing performances
        if (!qconfig.isTraceAll()) return;         
        msgListener.finest(step, node, message);
    }

    // ===========================================================

    // Innovimax: modified function
    public void reportStep(XStep step) {
        // Innovimax: testing performances
        if (true) return;         
        reported.add(step);
    }

    public void start(XPipeline pipe) {
    }

    public void finish(XPipeline pipe) {
    }
    
    //*************************************************************************
    //*************************************************************************        
    //*************************************************************************
    // INNOVIMAX IMPLEMENTATION
    //*************************************************************************
    //*************************************************************************
    //*************************************************************************       
            
    private QConfig qconfig = null;  // Innovimax: new property      
    
    // Innovimax: new constructor
    public XProcRuntime() {
      // nop
    }    
    
    // Innovimax: new function
    public void setQConfig(QConfig qconfig) {     
        this.qconfig = qconfig;           
        qconfig.start(this);      
    }
    
    // Innovimax: new function
    public QConfig getQConfig() {
        return qconfig;
    }     
    
    // Innovimax: new function
    public Tracing getTracer() {
        return qconfig.getTracer();
    }        
    
    // Innovimax: new function
    public Waiting newWaiterInstance(XStep xstep, int channel, ReadablePipe pipe, PipedDocument doc, String message) {
        Waiting waiter = qconfig.getWaiter().newInstance(xstep,channel,pipe,doc,message);
        waiter.setRuntime(this);
        return waiter;
    }            
    
    // Innovimax: new function    
    public boolean isStreamAll() {
        return qconfig.isStreamAll();
    }  
    
    // Innovimax: new function      
    public boolean isDOMAll() {
        return qconfig.isDOMAll();
    }      
    
    // Innovimax: new function
    public void startSpying() {
        qconfig.startSpying();
    }  
    
    // Innovimax: new function
    public void stopSpying() {
        qconfig.stopSpying();
    }           
}
