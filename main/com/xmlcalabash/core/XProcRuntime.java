/*
QuiXProc: efficient evaluation of XProc Pipelines.
Copyright (C) 2011 Innovimax
2008-2011 Mark Logic Corporation.
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

import com.xmlcalabash.util.DefaultXProcMessageListener;
import com.xmlcalabash.util.StepErrorListener;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.SaxonApiException;
import com.xmlcalabash.model.Parser;
import com.xmlcalabash.model.DeclareStep;
import com.xmlcalabash.model.PipelineLibrary;
import com.xmlcalabash.util.XProcURIResolver;
import com.xmlcalabash.util.URIUtils;
import com.xmlcalabash.util.Reporter;
import com.xmlcalabash.io.ReadablePipe;  // Innovimax: new import

import java.util.logging.Logger;
import java.util.Hashtable;
import java.util.Vector;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.URL;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.xmlcalabash.runtime.*;
import com.xmlcalabash.functions.*;

import javax.xml.transform.URIResolver;

import org.apache.commons.httpclient.Cookie;
import org.xml.sax.EntityResolver;

import innovimax.quixproc.codex.util.QConfig;
import innovimax.quixproc.codex.util.Tracing;
import innovimax.quixproc.codex.util.Waiting;
import innovimax.quixproc.codex.util.PipedDocument;

public class XProcRuntime {
    protected Logger logger = Logger.getLogger("com.xmlcalabash");
    private Processor processor = null;
    private Parser parser = null;
    private XProcURIResolver uriResolver = null;
    private XProcConfiguration config = null;
    private Vector<XStep> reported = new Vector<XStep> ();
    private String phoneHomeURL = "http://xproc.org/cgi-bin/phonehome";
    private boolean phoneHome = true;
    private Thread phoneHomeThread = null;
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
    private XProcData xprocData = null;
    private Logger log = null;
    private XProcMessageListener msgListener = null;
    private PipelineLibrary standardLibrary = null;
    private XLibrary xStandardLibrary = null;
    private Hashtable<String,Vector<Cookie>> cookieHash = new Hashtable<String,Vector<Cookie>> ();

    public XProcRuntime(XProcConfiguration config) {
        this.config = config;
        processor = config.getProcessor();

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

        StepErrorListener errListener = new StepErrorListener(this);
        saxonConfig.setErrorListener(errListener);

        allowGeneralExpressions = config.extensionValues;

        reset();
    }

    public XProcRuntime(XProcRuntime runtime) {
        processor = runtime.processor;
        parser = runtime.parser;
        uriResolver = runtime.uriResolver;
        config = runtime.config;
        phoneHome = runtime.phoneHome;
        staticBaseURI = runtime.staticBaseURI;
        allowGeneralExpressions = runtime.allowGeneralExpressions;
        log = runtime.log;
        msgListener = runtime.msgListener;
        standardLibrary = runtime.standardLibrary;
        xStandardLibrary = runtime.xStandardLibrary;
        cookieHash = runtime.cookieHash;
    }

    public XProcData getXProcData() {
        return xprocData;
    }

    public void setPhoneHome(boolean phoneHome) {
        this.phoneHome = phoneHome;
    }

    public boolean getDebug() {
        return config.debug;
    }

    public URI getStaticBaseURI() {
        return staticBaseURI;
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
        return "Innovimax";
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

        String phone = System.getProperty("com.xmlcalabash.phonehome");
        if (phone != null && ("0".equals(phone) || "no".equals(phone) || "false".equals(phone))) {
            finest(null, null,"Phonehome suppressed by user.");
            phoneHome = false;
        }

        if (phoneHomeURL == null) {
            phoneHome = false;
        }

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
        pipeline = parser.loadPipeline(pipelineURI);
        if (errorCode != null) {
            throw new XProcException(errorCode, errorMessage);
        }

        XRootStep root = new XRootStep(this);
        DeclareStep decl = pipeline.getDeclaration();
        decl.setup();

        phoneHome(decl);

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
        pipeline = parser.usePipeline(p_pipeline);
        if (errorCode != null) {
            throw new XProcException(errorCode, errorMessage);
        }

        XRootStep root = new XRootStep(this);
        DeclareStep decl = pipeline.getDeclaration();
        decl.setup();

        phoneHome(decl);

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

    public Processor getProcessor() {
        return processor;        
    }

    public XdmNode parse(String uri, String base) {
        return parse(uri, base, false);
    }

    public XdmNode parse(String uri, String base, boolean validate) {
        XdmNode doc = uriResolver.parse(uri, base, validate);
        return doc;
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

    /*
    public void makeBuiltinsExplicit() {
        explicitDeclarations = true;
    }

    public void clearBuiltins() {
        if (explicitDeclarations) {
            throw XProcException.staticError(50);
        }

        Vector<QName> delete = new Vector<QName> ();
        for (QName type : declaredSteps.keySet()) {
            if (XProcConstants.NS_XPROC.equals(type.getNamespaceURI())) {
                delete.add(type);
            }
        }

        for (QName type : delete) {
            declaredSteps.remove(type);
        }
    }
    */
    
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

    public void info(XProcRunnable step, XdmNode node, String message) {
        // Innovimax: testing performances
        if (!qconfig.isTraceAll()) return;      
        msgListener.info(step, node, message);
    }

    public void fine(XProcRunnable step, XdmNode node, String message) {
        // Innovimax: testing performances
        if (!qconfig.isTraceAll()) return;      
        msgListener.fine(step, node, message);
    }

    public void finer(XProcRunnable step, XdmNode node, String message) {
        // Innovimax: testing performances
        if (!qconfig.isTraceAll()) return;      
        msgListener.finer(step, node, message);
    }

    public void finest(XProcRunnable step, XdmNode node, String message) {
        // Innovimax: testing performances
        if (!qconfig.isTraceAll()) return;      
        msgListener.finest(step, node, message);
    }

    // ===========================================================

    public void reportStep(XStep step) {
        // Innovimax: testing performances
        if (true) return;      
        reported.add(step);
    }

    public void start(XPipeline pipe) {
    }

    public void finish(XPipeline pipe) {
        // Innovimax: testing performances
        if (true) return;      
        int seconds = 0;
        try {
            while (phoneHomeThread != null && phoneHomeThread.isAlive()) {
                phoneHomeThread.join(1000);
                seconds++;
                if (seconds == 4) {
                    warning(null, null, "Please wait...sending statistics to xproc.org");
                }
            }
        } catch(InterruptedException ie) {
            // I don't care
        }

        if (phoneHome) {
            PhoneHome phone = new PhoneHome(this, reported);
            phoneHomeThread = new Thread(phone);
            phoneHomeThread.start();
        }

        seconds = 0;
        try {
            while (phoneHomeThread != null && phoneHomeThread.isAlive()) {
                phoneHomeThread.join(1000);
                seconds++;
                if (seconds == 4) {
                    warning(null, null, "Please wait...sending statistics to xproc.org");
                }
            }
        } catch(InterruptedException ie) {
            // I don't care
        }
    }

    public void phoneHome(DeclareStep decl) {
        if (phoneHome) {
            PhoneHome phone = new PhoneHome(this, decl);
            phoneHomeThread = new Thread(phone);
            phoneHomeThread.start();
        }
    }

    public void phoneHome(Exception ex) {
        // We only get here if something went ``wrong; so the earlier thread might still be running
        int seconds = 0;
        try {
            while (phoneHomeThread != null && phoneHomeThread.isAlive()) {
                phoneHomeThread.join(1000);
                seconds++;
                if (seconds == 4) {
                    warning(null, null, "Please wait...sending statistics to xproc.org");
                }
                if (seconds > 16) {
                    // Give up.
                    break;
                }
            }
        } catch(InterruptedException ie) {
            // I don't care
        }

        if (phoneHome) {
            PhoneHome phone = new PhoneHome(this, ex);
            phoneHomeThread = new Thread(phone);
            phoneHomeThread.start();
        }
    }

    private class PhoneHome implements Runnable {
        private Throwable ex = null;
        private DeclareStep step = null;
        private XProcRuntime runtime = null;
        private Vector<XStep> reported = null;

        public PhoneHome(XProcRuntime runtime, DeclareStep decl) {
            this.runtime = runtime;
            step = decl;
        }

        public PhoneHome(XProcRuntime runtime, Vector<XStep> reported) {
            this.runtime = runtime;
            this.reported = reported;
        }

        public PhoneHome(XProcRuntime runtime, Throwable ex) {
            this.runtime = runtime;
            this.ex = ex;
        }

        public void run() {
            String email = System.getProperty("com.xmlcalabash.phonehome.email");

            try {
                URL url = new URL(phoneHomeURL);
                URLConnection conn = url.openConnection();
                conn.setDoOutput(true);
                PrintStream pr = new PrintStream(conn.getOutputStream());

                //String fn = "/tmp/phonehome-pipeline.xml";
                String gi = "pipeline-report";
                if (reported != null) {
                    gi = "runtime-report";
                    //fn = "/tmp/phonehome-report.xml";
                }
                if (ex != null) {
                    gi = "error-report";
                    //fn = "/tmp/phonehome-error.xml";
                }

                //PrintStream pr = new PrintStream(new File(fn));

                pr.println("<" + gi + " xmlns='http://xmlcalabash.com/ns/phonehome'>");
                if (email != null) {
                    pr.println("<email>" + email + "</email>");
                }
                pr.println("<general-value-extension>" + runtime.allowGeneralExpressions + "</general-value-extension>");
                pr.println("<version>" + getXProcVersion() + "</version>");
                pr.println("<product-name>" + getProductName() + "</product-name>");
                pr.println("<product-version>" + getProductVersion() + "</product-version>");
                pr.println("<episode>" + getEpisode() + "</episode>");

                if (ex != null) {
                    while (ex != null) {
                        pr.println("<failure>" + ex + "</failure>");
                        ex = ex.getCause();
                    }
                }

                if (step != null) {
                    Reporter reporter = new Reporter(runtime, pr);
                    reporter.report(step);
                }

                if (reported != null) {
                    pr.println("<steps>");
                    for (XStep step: reported) {
                        DeclareStep dstep = step.getDeclareStep();
                        if (dstep != null) {
                            pr.println("  <step type='" + dstep.getDeclaredType().getClarkName() + "'/>");
                        } else {
                            pr.println("  <step className='" + step.getClass().getName() + "'/>");
                        }
                    }
                    pr.println("</steps>");
                }
                
                pr.println("</" + gi + ">");
                pr.flush();

                // Get the response
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = rd.readLine()) != null) {
                    //System.err.println("R: " + line);
                }
                rd.close();

                pr.close();
            } catch (Exception e) {
                finest(null, null,"Failed to phone home: " + e);
            }
        }
    }
    
    //*************************************************************************
    //*************************************************************************        
    //*************************************************************************
    // INNOVIMAX IMPLEMENTATION
    //*************************************************************************
    //*************************************************************************
    //*************************************************************************       
            
    private QConfig qconfig = null;  // Innovimax: new property      
    
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
    public Waiting getWaiter() {
        return qconfig.getWaiter();
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
    public void startSpying(String[] args) {
        qconfig.getSpy().start(args);
    }  
    
    // Innovimax: new function
    public void stopSpying() {
        qconfig.getSpy().stop();
    }                            
}
