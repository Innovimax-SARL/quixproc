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

package com.xmlcalabash.library;

import innovimax.quixproc.codex.util.StepContext;
import innovimax.quixproc.codex.util.XPathUtils;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SaxonApiUncheckedException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;

import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.core.XProcStep;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.model.RuntimeValue;
import com.xmlcalabash.runtime.XAtomicStep;
import com.xmlcalabash.util.S9apiUtils;
// Innovimax: new import
// Innovimax: new import

/**
 * Created by IntelliJ IDEA.
 * User: ndw
 * Date: Oct 8, 2008
 * Time: 7:46:15 AM
 * To change this template use File | Settings | File Templates.
 */
public class DefaultStep implements XProcStep {
    public static final QName _byte_order_mark = new QName("", "byte-order-mark");
    public static final QName _cdata_section_elements = new QName("", "cdata-section-elements");
    public static final QName _doctype_public = new QName("", "doctype-public");
    public static final QName _doctype_system = new QName("", "doctype-system");
    public static final QName _encoding = new QName("", "encoding");
    public static final QName _escape_uri_attributes = new QName("", "escape-uri-attributes");
    public static final QName _include_content_type = new QName("", "include-content-type");
    public static final QName _indent = new QName("", "indent");
    public static final QName _media_type = new QName("", "media-type");
    public static final QName _method = new QName("", "method");
    public static final QName _normalization_form = new QName("", "normalization-form");
    public static final QName _omit_xml_declaration = new QName("", "omit-xml-declaration");
    public static final QName _standalone = new QName("", "standalone");
    public static final QName _undeclare_prefixes = new QName("", "undeclare_prefixes");
    public static final QName _version = new QName("", "version");

    private Hashtable<QName,RuntimeValue> options = null;
    protected XProcRuntime runtime = null;
    protected XAtomicStep step = null;
    protected Logger logger = Logger.getLogger(this.getClass().getName());

    //private String logger = null;
    
    // Innovimax: modified constructor
    public DefaultStep(XProcRuntime runtime, XAtomicStep step) {      
        this.runtime = runtime;
        this.step = step;     
        // Innovimax: set step context           
        //options = new Hashtable<QName,RuntimeValue> ();        
        if (runtime.getConfiguration() != null) {
          this.stepContext = step.getContext();        
          options = new Hashtable<QName,RuntimeValue> ();        
        }        
    }    

    public XAtomicStep getStep() {
        return step;
    }

    public void setInput(String port, ReadablePipe pipe) {
        throw new XProcException("No inputs allowed.");
    }

    public void setOutput(String port, WritablePipe pipe) {
        throw new XProcException("No outputs allowed.");
    }

    public void setParameter(QName name, RuntimeValue value) {
        throw new XProcException("No parameters allowed.");
    }

    public void setParameter(String port, QName name, RuntimeValue value) {
        throw new XProcException("No parameters allowed on port '" + port + "'");
    }

    public void setOption(QName name, RuntimeValue value) {
        if (options == null) {
            options = new Hashtable<QName,RuntimeValue> ();
        }
        options.put(name,value);
    }

    public RuntimeValue getOption(QName name) {
        if (options != null && options.containsKey(name)) {
            return options.get(name);
        } else {
            return null;
        }
    }

    public String getOption(QName name, String defaultValue) {
        if (options == null || !options.containsKey(name)) {
            return defaultValue;
        }
        return options.get(name).getString();
    }

    public QName getOption(QName name, QName defaultValue) {
        if (options == null || !options.containsKey(name)) {
            return defaultValue;
        }
        return options.get(name).getQName();
    }

    public boolean getOption(QName name, boolean defaultValue) {
        if (options == null || !options.containsKey(name)) {
            return defaultValue;
        }
        return options.get(name).getBoolean();
    }

    public int getOption(QName name, int defaultValue) {
        if (options == null || !options.containsKey(name)) {
            return defaultValue;
        }
        return options.get(name).getInt();
    }

    public void reset() {
        throw new XProcException("XProcStep implementation must override reset().");
    }

    public void error(XdmNode node, String message, QName code) {
        runtime.error(this, node, message, code);
    }

    public void warning(XdmNode node, String message) {
        runtime.warning(this, node, message);
    }

    public void info(XdmNode node, String message) {
        runtime.info(this, node, message);
    }

    public void fine(XdmNode node, String message) {
        runtime.fine(this, node, message);
    }

    public void finer(XdmNode node, String message) {
        runtime.finer(this, node, message);
    }

    public void finest(XdmNode node, String message) {
        runtime.finest(this, node, message);
    }
   
    public void gorun() throws SaxonApiException {
        String type = null;
        if (XProcConstants.NS_XPROC.equals(step.getType().getNamespaceURI())) {
            type = step.getType().getLocalName();
        } else {
            type = step.getType().getClarkName();
        }
        fine(null, "Running " + type + " " + step.getName());
        runtime.reportStep(step);     
    }      

    public Serializer makeSerializer() {
        Serializer serializer = new Serializer();

        if (options == null) {
            return serializer;
        }

        if (options.containsKey(_byte_order_mark)) {
            serializer.setOutputProperty(Serializer.Property.BYTE_ORDER_MARK, getOption(_byte_order_mark, false) ? "yes" : "no");
        }

        if (options.containsKey(_cdata_section_elements)) {
            String list = getOption(_cdata_section_elements).getString();

            // FIXME: Why is list="" sometimes?
            if (!"".equals(list)) {
                String[] names = list.split("\\s+");
                list = "";
                for (String name : names) {
                    QName q = new QName(name, step.getNode());
                    list += q.getClarkName() + " ";
                }

                serializer.setOutputProperty(Serializer.Property.CDATA_SECTION_ELEMENTS, list);
            }
        }

        if (options.containsKey(_doctype_public)) {
            serializer.setOutputProperty(Serializer.Property.DOCTYPE_PUBLIC, getOption(_doctype_public).getString());
        }

        if (options.containsKey(_doctype_system)) {
            serializer.setOutputProperty(Serializer.Property.DOCTYPE_SYSTEM, getOption(_doctype_system).getString());
        }

        if (options.containsKey(_encoding)) {
            serializer.setOutputProperty(Serializer.Property.ENCODING, getOption(_encoding).getString());
        }

        if (options.containsKey(_escape_uri_attributes)) {
            serializer.setOutputProperty(Serializer.Property.ESCAPE_URI_ATTRIBUTES, getOption(_escape_uri_attributes, true) ? "yes" : "no");
        }

        if (options.containsKey(_include_content_type)) {
            serializer.setOutputProperty(Serializer.Property.INCLUDE_CONTENT_TYPE, getOption(_include_content_type, true) ? "yes" : "no");
        }

        if (options.containsKey(_indent)) {
            serializer.setOutputProperty(Serializer.Property.INDENT, getOption(_indent, true) ? "yes" : "no");
        }

        if (options.containsKey(_media_type)) {
            serializer.setOutputProperty(Serializer.Property.MEDIA_TYPE, getOption(_media_type).getString());
        }

        if (options.containsKey(_method)) {
            serializer.setOutputProperty(Serializer.Property.METHOD, getOption(_method).getString());
        }

        if (options.containsKey(_normalization_form)) {
            serializer.setOutputProperty(Serializer.Property.NORMALIZATION_FORM, getOption(_normalization_form).getString());
        }

        if (options.containsKey(_omit_xml_declaration)) {
           serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, getOption(_omit_xml_declaration, true) ? "yes" : "no");
        }

        if (options.containsKey(_standalone)) {
            String standalone = getOption(_standalone).getString();
            if ("true".equals(standalone)) {
                serializer.setOutputProperty(Serializer.Property.STANDALONE, "yes");
            } else if ("false".equals(standalone)) {
                serializer.setOutputProperty(Serializer.Property.STANDALONE, "no");
            }
            // What about omit?
        }

        if (options.containsKey(_undeclare_prefixes)) {
            serializer.setOutputProperty(Serializer.Property.UNDECLARE_PREFIXES, getOption(_undeclare_prefixes, true) ? "yes" : "no");
        }

        if (options.containsKey(_version)) {
            serializer.setOutputProperty(Serializer.Property.VERSION, getOption(_version).getString());
        }

        return serializer;
    }

    // Innovimax: modified function
    public Vector<XdmItem> evaluateXPath(XdmNode doc, Hashtable<String,String> nsBindings, String xpath, Hashtable<QName,RuntimeValue> globals) {
        Vector<XdmItem> results = new Vector<XdmItem> ();
        
        // Innovimax: select xpath functions
        xpath = XPathUtils.checkFunctions(step, xpath);        

        Configuration config = runtime.getProcessor().getUnderlyingConfiguration();

        try {
            XPathCompiler xcomp = runtime.getProcessor().newXPathCompiler();
            xcomp.setBaseURI(step.getNode().getBaseURI());
            // Extension functions are not available here...

            for (QName varname : globals.keySet()) {
                xcomp.declareVariable(varname);
            }

            for (String prefix : nsBindings.keySet()) {
                xcomp.declareNamespace(prefix, nsBindings.get(prefix));
            }
            XPathExecutable xexec = null;
            try {
                xexec = xcomp.compile(xpath);
            } catch (SaxonApiException sae) {
                Throwable t = sae.getCause();
                if (t instanceof XPathException) {
                    XPathException xe = (XPathException) t;
                    if (xe.getMessage().contains("Undeclared variable")) {
                        throw XProcException.dynamicError(26, xe.getMessage());
                    }
                }
                throw sae;
            }

            XPathSelector selector = xexec.load();

            for (QName varname : globals.keySet()) {
                XdmAtomicValue avalue = globals.get(varname).getUntypedAtomic(runtime);
                selector.setVariable(varname,avalue);
            }

            if (doc != null) {
                selector.setContextItem(doc);
            }

            try {
                Iterator<XdmItem> values = selector.iterator();
                while (values.hasNext()) {
                    results.add(values.next());
                }
            } catch (SaxonApiUncheckedException saue) {
                Throwable sae = saue.getCause();
                if (sae instanceof XPathException) {
                    XPathException xe = (XPathException) sae;
                    if ("http://www.w3.org/2005/xqt-errors".equals(xe.getErrorCodeNamespace()) && "XPDY0002".equals(xe.getErrorCodeLocalPart())) {
                        throw XProcException.dynamicError(26,"Expression refers to context when none is available: " + xpath);
                    } else {
                        throw saue;
                    }

                } else {
                    throw saue;
                }
            }
        } catch (SaxonApiException sae) {
            if (S9apiUtils.xpathSyntaxError(sae)) {
                throw XProcException.dynamicError(23, "Invalid XPath expression: '" + xpath + "'.");
            } else {
                throw new XProcException(sae);
            }
        }

        return results;
    }
    
    //*************************************************************************
    //*************************************************************************        
    //*************************************************************************
    // INNOVIMAX IMPLEMENTATION
    //*************************************************************************
    //*************************************************************************
    //*************************************************************************                 
    
    protected boolean running = false; // Innovimax: new property
    protected boolean streamed = false; // Innovimax: new property
    protected boolean streamAll = false; // Innovimax: new property
    protected StepContext stepContext = null; // Innovimax: new property    
    
    // Innovimax: new function (not used : to agree XProcRunnable)
    public void run() {}        
        
    // Innovimax: new function
    public boolean isRunning() {
      return running;
    }     
           
    // Innovimax: new function
    public void setStreamed(boolean streamed) {
      this.streamed = streamed;
    }       
    
    // Innovimax: new function
    public void setStreamAll(boolean streamAll) {
      this.streamAll = streamAll;
    }       
}
