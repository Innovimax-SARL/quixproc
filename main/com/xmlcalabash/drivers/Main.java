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

package com.xmlcalabash.drivers;

import innovimax.quixproc.codex.util.QConfig;
import innovimax.quixproc.codex.util.Spying;
import innovimax.quixproc.codex.util.StepContext;
import innovimax.quixproc.util.ExitException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.transform.sax.SAXSource;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.xml.sax.InputSource;

import com.xmlcalabash.core.XProcConfiguration;
import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.io.ReadableData;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritableDocument;
import com.xmlcalabash.model.RuntimeValue;
import com.xmlcalabash.model.Serialization;
import com.xmlcalabash.runtime.XPipeline;
import com.xmlcalabash.util.LogOptions;
import com.xmlcalabash.util.ParseArgs;
import com.xmlcalabash.util.S9apiUtils;
import com.xmlcalabash.util.URIUtils;
// Innovimax: new import
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
public class Main {
    private static boolean errors = false;
    private static QName _code = new QName("code");
    private XProcRuntime runtime = null;
    private boolean readStdin = false;
    private Logger logger = Logger.getLogger(this.getClass().getName());
    private boolean debug = false;              

    // Innovimax: modified function    
    private void showVersion() {
        System.out.println("QuiXProc version " + XProcConstants.XPROC_VERSION + ", an XProc processor.");
        if (runtime != null) {
            System.out.print("Running on Saxon version ");
            System.out.print(runtime.getConfiguration().getProcessor().getSaxonProductVersion());
            System.out.print(", ");
            System.out.print(runtime.getConfiguration().getProcessor().getUnderlyingConfiguration().getEditionCode());
            System.out.println(" edition.");
        }
        System.out.println("Copyright (c) 2011-2012 Innovimax and 2007-2012 Norman Walsh");
        System.out.println("See http://quixproc.com/");
//        System.out.println("See docs/notices/NOTICES in the distribution for licensing");
//        System.out.println("See also http://xmlcalabash.com/ for more information");
        System.out.println("");
    }
    
    // Innovimax: modified function
    private void usage() throws IOException {
        showVersion();

        InputStream instream = getClass().getResourceAsStream("/etc/usage.txt");
        if (instream == null) {
            throw new UnsupportedOperationException("Failed to load usage text from JAR file. This \"can't happen\".");
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(instream));
        String line = null;
        while ((line = br.readLine()) != null) {
            System.err.println(line);
        }
        instream.close();
        br.close();        
        // Innovimax: exit 
        //System.exit(1);
        throw new ExitException(1,"Show usage.");         
    }

    private String errorMessage(QName code) {
        InputStream instream = getClass().getResourceAsStream("/etc/error-list.xml");
        if (instream != null) {
            XdmNode doc = runtime.parse(new InputSource(instream));
            XdmSequenceIterator iter = doc.axisIterator(Axis.DESCENDANT, new QName(XProcConstants.NS_XPROC_ERROR,"error"));
            while (iter.hasNext()) {
                XdmNode error = (XdmNode) iter.next();
                if (code.getLocalName().equals(error.getAttributeValue(_code))) {
                    return error.getStringValue();
                }
            }
        }
        return "Unknown error";
    }

    // ===========================================================
    // Logging methods repeated here so that they don't rely
    // on the XProcRuntime constructor succeeding.

    private String message(XdmNode node, String message) {
        String baseURI = "(unknown URI)";
        int lineNumber = -1;

        if (node != null) {
            baseURI = node.getBaseURI().toASCIIString();
            lineNumber = node.getLineNumber();
            return baseURI + ":" + lineNumber + ": " + message;
        } else {
            return message;
        }

    }

    public void error(Logger logger, XdmNode node, String message, QName code) {
        logger.severe(message(node, message));
    }

    public void warning(Logger logger, XdmNode node, String message) {
        logger.warning(message(node, message));
    }

    public void info(Logger logger, XdmNode node, String message) {
        logger.info(message(node, message));
    }

    public void fine(Logger logger, XdmNode node, String message) {
        logger.fine(message(node, message));
    }

    public void finer(Logger logger, XdmNode node, String message) {
        logger.finer(message(node, message));
    }

    public void finest(Logger logger, XdmNode node, String message) {
        logger.finest(message(node, message));
    }

    //*************************************************************************
    // INNOVIMAX IMPLEMENTATION
    //************************************************************************* 
    
    // data structure to hold compiled stylesheet
    public static class CompiledData implements Serializable {
      public XProcConfiguration config = null;
      public XPipeline pipeline = null;
      public StepContext stepContext = new StepContext();
      public ParseArgs cmd = new ParseArgs();                   
    }      
        
    public Spying run(String[] args, File baseDir) throws SaxonApiException, IOException, URISyntaxException {    
        throw new RuntimeException("Incorrect driver running method");
    }     
    
    // extracted from old run
    public void run(String[] args, QConfig qconfig) throws SaxonApiException, IOException, URISyntaxException {    
      
        // Innovimax: build data structure
        CompiledData data = compile(args, qconfig);
        XProcConfiguration config = data.config;
        XPipeline pipeline = data.pipeline;
        StepContext stepContext = data.stepContext;
        ParseArgs cmd = data.cmd;        
                      
        try {
            // Process parameters from the configuration...
            for (String port : config.params.keySet()) {
                Hashtable<QName,String> hash = config.params.get(port);
                if ("*".equals(port)) {
                    for (QName name : hash.keySet()) {
                        pipeline.setParameter(name, new RuntimeValue(hash.get(name)));
                    }
                } else {
                    for (QName name : hash.keySet()) {
                        pipeline.setParameter(port, name, new RuntimeValue(hash.get(name)));
                    }
                }
            }

            // Now process parameters from the command line...
            for (String port : cmd.getParameterPorts()) {
                for (QName name : cmd.getParameterNames(port)) {
                    if ("*".equals(port)) {
                        pipeline.setParameter(name, new RuntimeValue(cmd.getParameter(port, name)));
                    } else {
                        pipeline.setParameter(port, name, new RuntimeValue(cmd.getParameter(port,name)));
                    }
                }
            }

            Set<String> ports = pipeline.getInputs();
            Set<String> cmdPorts = cmd.getInputPorts();
            Set<String> cfgPorts = config.inputs.keySet();
            HashSet<String> allPorts = new HashSet<String>();
            allPorts.addAll(cmdPorts);
            allPorts.addAll(cfgPorts);

            for (String port : allPorts) {
                if (!ports.contains(port)) {
                    throw new XProcException("There is a binding for the port '" + port + "' but the pipeline declares no such port.");
                }

                pipeline.clearInputs(port);

                if (cmdPorts.contains(port)) {
                    XdmNode doc = null;
                    for (String uri : cmd.getInputs(port)) {
                        if (uri.startsWith("xml:")) {
                            uri = uri.substring(4);

                            SAXSource source = null;
                            if ("-".equals(uri)) {
                                doc = runtime.parse(new InputSource(System.in));
                            } else {
                                doc = runtime.parse(new InputSource(uri));
                            }
                        } else if (uri.startsWith("data:")) {
                            uri = uri.substring(5);
                            ReadableData rd = new ReadableData(runtime, XProcConstants.c_data, uri, "text/plain");
                            doc = rd.read(stepContext);
                        } else {
                            throw new UnsupportedOperationException("Unexpected input type: " + uri);
                        }

                        pipeline.writeTo(port, doc);
                        // Innovimax: close pipe
                        pipeline.closeWrittenPipe(port);                          
                    }
                } else {
                    for (ReadablePipe pipe : config.inputs.get(port)) {
                        XdmNode doc = pipe.read(stepContext);
                        pipeline.writeTo(port, doc);
                        // Innovimax: close pipe
                        pipeline.closeWrittenPipe(port);                          
                    }
                }
            }

            String stdio = null;

            // Look for explicit binding to "-"
            for (String port : pipeline.getOutputs()) {
                String uri = null;

                if (cmd.outputs.containsKey(port)) {
                    uri = cmd.outputs.get(port);
                } else if (config.outputs.containsKey(port)) {
                    uri = config.outputs.get(port);
                }

                if ("-".equals(uri) && stdio == null) {
                    stdio = port;
                }
            }

            // Look for implicit binding to "-"
            for (String port : pipeline.getOutputs()) {
                String uri = null;

                if (cmd.outputs.containsKey(port)) {
                    uri = cmd.outputs.get(port);
                } else if (config.outputs.containsKey(port)) {
                    uri = config.outputs.get(port);
                }

                if (uri == null) {
                    if (stdio == null) {
                        stdio = port;
                    } else {
                        warning(logger, null, "You didn't specify any binding for the output port '" + port + "', its output will be discard.");
                    }
                }
            }

            for (QName optname : config.options.keySet()) {
                RuntimeValue value = new RuntimeValue(config.options.get(optname), null, null);
                pipeline.passOption(optname, value);
            }

            for (QName optname : cmd.getOptionNames()) {
                RuntimeValue value = new RuntimeValue(cmd.getOption(optname), null, null);
                pipeline.passOption(optname, value);
            }

            // Innovimax: start memory spy                   
            runtime.startSpying();                        

            // Innovimax: exec pipeline
            //pipeline.run();  
            pipeline.exec(); 
                        
            // Innovimax: stop memory spy                   
            runtime.stopSpying(); 

            for (String port : pipeline.getOutputs()) {
                String uri = null;
                if (cmd.outputs.containsKey(port)) {
                    uri = cmd.outputs.get(port);
                } else if (config.outputs.containsKey(port)) {
                    uri = config.outputs.get(port);
                }

                if (port.equals(stdio)) {
                    finest(logger, null, "Copy output from " + port + " to stdout");
                    uri = null;
                } else if (uri == null) {
                    // You didn't bind it, and it isn't going to stdout, so it's going into the bit bucket.
                    continue;
                } else {
                    finest(logger, null, "Copy output from " + port + " to " + uri);
                }

                Serialization serial = pipeline.getSerialization(port);

                if (serial == null) {
                    // Use the configuration options
                    // FIXME: should each of these be considered separately?
                    // FIXME: should there be command-line options to override these settings?
                    serial = new Serialization(runtime, pipeline.getNode()); // The node's a hack
                    for (String name : config.serializationOptions.keySet()) {
                        String value = config.serializationOptions.get(name);

                        if ("byte-order-mark".equals(name)) serial.setByteOrderMark("true".equals(value));
                        if ("escape-uri-attributes".equals(name)) serial.setEscapeURIAttributes("true".equals(value));
                        if ("include-content-type".equals(name)) serial.setIncludeContentType("true".equals(value));
                        if ("indent".equals(name)) serial.setIndent("true".equals(value));
                        if ("omit-xml-declaration".equals(name)) serial.setOmitXMLDeclaration("true".equals(value));
                        if ("undeclare-prefixes".equals(name)) serial.setUndeclarePrefixes("true".equals(value));
                        if ("method".equals(name)) serial.setMethod(new QName("", value));

                        // FIXME: if ("cdata-section-elements".equals(name)) serial.setCdataSectionElements();
                        if ("doctype-public".equals(name)) serial.setDoctypePublic(value);
                        if ("doctype-system".equals(name)) serial.setDoctypeSystem(value);
                        if ("encoding".equals(name)) serial.setEncoding(value);
                        if ("media-type".equals(name)) serial.setMediaType(value);
                        if ("normalization-form".equals(name)) serial.setNormalizationForm(value);
                        if ("standalone".equals(name)) serial.setStandalone(value);
                        if ("version".equals(name)) serial.setVersion(value);
                    }
                }

                // I wonder if there's a better way...
                WritableDocument wd = null;
                if (uri != null) {
                    URI furi = new URI(uri);
                    String filename = furi.getPath();
                    FileOutputStream outfile = new FileOutputStream(filename);
                    wd = new WritableDocument(runtime,filename,serial,outfile);
                } else {
                    wd = new WritableDocument(runtime,uri,serial);
                }

                ReadablePipe rpipe = pipeline.readFrom(port);
                while (rpipe.moreDocuments(stepContext)) {
                    wd.write(stepContext,rpipe.read(stepContext));
                }

                if (uri!=null) {
                   wd.close(stepContext);
                }
            }

            if (stdio != null) {
                // It's just sooo much nicer if there's a newline at the end.
                System.out.println();
            }
        } catch (ExitException err) {            
            // Innovimax: exit 
            throw err;                          
        } catch (XProcException err) {
            if (err.getErrorCode() != null) {
                error(logger, null, errorMessage(err.getErrorCode()), err.getErrorCode());
            } else {
                error(logger, null, err.toString(), null);
            }

            Throwable cause = err.getCause();
            while (cause != null && cause instanceof XProcException) {
                cause = cause.getCause();
            }

            if (cause != null) {
                error(logger, null, "Underlying exception: " + cause, null);
            }

            // Innovimax: debug trace
            if (debug || qconfig.isTraceAll()) {
                err.printStackTrace();
            }
            // Innovimax: exit 
            throw new ExitException(-1,err);             
        } catch (Exception err) {
            error(logger, null, "Pipeline failed: " + err.toString(), null);
            if (err.getCause() != null) {
                Throwable cause = err.getCause();
                error(logger, null, "Underlying exception: " + cause, null);
            }
            // Innovimax: debug trace
            if (debug || qconfig.isTraceAll()) {
                err.printStackTrace();
            }
            // Innovimax: exit 
            throw new ExitException(-1,err);               
        }
    }
        
    // extracted from old run
    private CompiledData compile(String[] args, QConfig qconfig) throws XProcException, ExitException, IOException, SaxonApiException {
      
        // Innovimax: instantiate data structure
        CompiledData data = new CompiledData(); 
        
        try {
            data.cmd.parse(args);
        } catch (XProcException xe) {
            System.err.println(xe.getMessage());
            usage();
        }

        if (data.cmd.saxonConfigFile != null) {
            if (data.cmd.schemaAware) {
                throw new XProcException("Specifying schema-aware processing is an error if you specify a Saxon configuration file.");
            }
            if (data.cmd.saxonProcessor != null) {
                throw new XProcException("Specifying a processor type is an error if you specify a Saxon configuration file.");
            }
        }

        // Innovimax: obsolete
        //XProcConfiguration config = null;

        // Blech
        try {
            String proc = data.cmd.saxonProcessor;
            if (data.cmd.schemaAware) {
                proc = "ee";
            }

            if (data.cmd.saxonConfigFile != null) {
                data.config = new XProcConfiguration(data.cmd.saxonConfigFile);
            } else if (proc != null) {
                data.config = new XProcConfiguration(proc, data.cmd.schemaAware);
            } else {
                data.config = new XProcConfiguration();
            }
        } catch (Exception e) {
            System.err.println("FATAL: Failed to parse configuration file.");
            System.err.println(e);
            // Innovimax: exit 
            //System.exit(2);
            throw new ExitException(2,e);  
        }
        // set Processor to be the same
        qconfig.getQuiXPath().setProcessor(data.config.getProcessor());

        if (data.cmd.configFile != null) {
            // Make this absolute because sometimes it fails from the command line otherwise. WTF?
            String cfgURI = URIUtils.cwdAsURI().resolve(data.cmd.configFile).toASCIIString();
            SAXSource source = new SAXSource(new InputSource(cfgURI));
            // No resolver, we don't have one yet
            DocumentBuilder builder = data.config.getProcessor().newDocumentBuilder();
            XdmNode doc = builder.build(source);
            data.config.parse(doc);
        }

        if (data.cmd.logStyle != null) {
            if (data.cmd.logStyle.equals("off")) {
                data.config.logOpt = LogOptions.OFF;
            } else if (data.cmd.logStyle.equals("plain")) {
                data.config.logOpt = LogOptions.PLAIN;
            } else if (data.cmd.logStyle.equals("directory")) {
                data.config.logOpt = LogOptions.DIRECTORY;
            } else {
                data.config.logOpt = LogOptions.WRAPPED;
            }
        }

        if (data.cmd.uriResolverClass != null) {
            data.config.uriResolver = data.cmd.uriResolverClass;
        }

        if (data.cmd.entityResolverClass != null) {
            data.config.entityResolver = data.cmd.entityResolverClass;
        }

        if (data.cmd.safeModeExplicit) {
            data.config.safeMode = data.cmd.safeMode;
        }
        
        if (data.cmd.debugExplicit) {
            data.config.debug = data.cmd.debug;
        }

        data.config.extensionValues |= data.cmd.extensionValues;
        data.config.xpointerOnText |= data.cmd.allowXPointerOnText;
        data.config.transparentJSON |= data.cmd.transparentJSON;
        if (data.cmd.jsonFlavor != null) {
            data.config.jsonFlavor = data.cmd.jsonFlavor;
        }
        data.config.useXslt10 |= data.cmd.useXslt10;

        debug = data.config.debug;

        runtime = new XProcRuntime(data.config);
        
        // Innovimax: set qconfig            
        runtime.setQConfig(qconfig);          

        if (data.cmd.showVersion) {
            showVersion();
        }

        if (data.cmd.pipelineURI != null) {
            data.pipeline = runtime.load(data.cmd.pipelineURI);
        } else if (data.cmd.impliedPipeline()) {
            XdmNode implicitPipeline = data.cmd.implicitPipeline(runtime);

            if (debug) {
                System.err.println("Implicit pipeline:");

                Serializer serializer = new Serializer();

                serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
                serializer.setOutputProperty(Serializer.Property.METHOD, "xml");

                serializer.setOutputStream(System.err);

                S9apiUtils.serialize(runtime, implicitPipeline, serializer);
            }

            data.pipeline=runtime.use(implicitPipeline);
            
        } else if (data.config.pipeline != null) {
            XdmNode doc = data.config.pipeline.read(data.stepContext);
            data.pipeline = runtime.use(doc);
        }
        
        if (errors || data.pipeline == null) {
            usage();
        }
        
        return data;                
    } 
    
    //*************************************************************************
    // INNOVIMAX DEPRECATION
    //*************************************************************************       
/*        
    public static void main(String[] args) throws SaxonApiException, IOException, URISyntaxException {
        Main main = new Main();
        main.run(args);
    }   
    
    public void run(String[] args) throws SaxonApiException, IOException, URISyntaxException {                
        //...
    }
*/
}
