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

package com.xmlcalabash.library;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Hashtable;
import java.util.Vector;
import javax.xml.transform.Result;
import javax.xml.transform.TransformerException;

import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.model.RuntimeValue;
import com.xmlcalabash.util.TreeWriter;
import com.xmlcalabash.util.CollectionResolver;
import com.xmlcalabash.util.S9apiUtils;
import net.sf.saxon.Configuration;
import net.sf.saxon.lib.CollectionURIResolver;
import net.sf.saxon.lib.OutputURIResolver;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.MessageListener;
import net.sf.saxon.s9api.ValidationMode;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.event.Receiver;
import com.xmlcalabash.runtime.XAtomicStep;

public class XSLT extends DefaultStep {
    private static final QName _initial_mode = new QName("", "initial-mode");
    private static final QName _template_name = new QName("", "template-name");
    private static final QName _output_base_uri = new QName("", "output-base-uri");
    private static final QName _version = new QName("", "version");
    // FIXME: doesn't support sequence input yet!
    private ReadablePipe sourcePipe = null;
    private ReadablePipe stylesheetPipe = null;
    private WritablePipe resultPipe = null;
    private WritablePipe secondaryPipe = null;
    private Hashtable<QName,RuntimeValue> params = new Hashtable<QName,RuntimeValue> ();
    private Hashtable<String, XdmDestination> secondaryResults = new Hashtable<String, XdmDestination> ();

    /**
     * Creates a new instance of XSLT
     */
    public XSLT(XProcRuntime runtime, XAtomicStep step) {
        super(runtime,step);
    }

    public void setInput(String port, ReadablePipe pipe) {
        if ("source".equals(port)) {
            sourcePipe = pipe;
        } else {
            stylesheetPipe = pipe;
        }
    }

    public void setOutput(String port, WritablePipe pipe) {
        if ("result".equals(port)) {
            resultPipe = pipe;
        } else {
            secondaryPipe = pipe;
        }
    }

    public void setParameter(QName name, RuntimeValue value) {
        params.put(name, value);
    }
    
    public void reset() {
        sourcePipe.resetReader(stepContext);
        stylesheetPipe.resetReader(stepContext);
        resultPipe.resetWriter(stepContext);
        secondaryPipe.resetWriter(stepContext);
    }

    public void gorun() throws SaxonApiException {
        super.gorun();

        XdmNode stylesheet = stylesheetPipe.read(stepContext);
        if (stylesheet == null) {
            throw XProcException.dynamicError(6, step.getNode(), "No stylesheet provided.");
        }

        Vector<XdmNode> defaultCollection = new Vector<XdmNode> ();

        while (sourcePipe.moreDocuments(stepContext)) {
            defaultCollection.add(sourcePipe.read(stepContext));
        }

        XdmNode document = null;
        if (defaultCollection.size() > 0) {
            document = defaultCollection.firstElement();
        }

        String version = null;
        if (getOption(_version) == null) {
            XdmNode ssroot = S9apiUtils.getDocumentElement(stylesheet);
            version = ssroot.getAttributeValue(new QName("","version"));
            if (version == null) {
                version = ssroot.getAttributeValue(new QName("http://www.w3.org/1999/XSL/Transform","version"));
            }
            if (version == null) {
                version = "2.0"; // WTF?
            }
        } else {
            version = getOption(_version).getString();
        }
        
        if (!"1.0".equals(version) && !"2.0".equals(version)) {
            throw XProcException.stepError(38, "XSLT version '" + version + "' is not supported.");
        }

        if ("1.0".equals(version) && defaultCollection.size() > 1) {
            throw XProcException.stepError(39);
        }

        QName initialMode = null;
        QName templateName = null;
        String outputBaseURI = null;

        RuntimeValue opt = getOption(_initial_mode);
        if (opt != null) {
            initialMode = opt.getQName();
        }

        opt = getOption(_template_name);
        if (opt != null) {
            templateName = opt.getQName();
        }

        opt = getOption(_output_base_uri);
        if (opt != null) {
            outputBaseURI = opt.getString();
        }

        Processor processor = runtime.getProcessor();
        Configuration config = processor.getUnderlyingConfiguration();

        OutputURIResolver uriResolver = config.getOutputURIResolver();
        CollectionURIResolver collectionResolver = config.getCollectionURIResolver();

        config.setOutputURIResolver(new OutputResolver());
        config.setCollectionURIResolver(new CollectionResolver(runtime, defaultCollection, collectionResolver));

        XsltCompiler compiler = runtime.getProcessor().newXsltCompiler();
        compiler.setSchemaAware(processor.isSchemaAware());
        XsltExecutable exec = compiler.compile(stylesheet.asSource());
        XsltTransformer transformer = exec.load();

        // NDW debugging, ignore this
        // transformer.getUnderlyingController().setBaseOutputURI("http://example.com/");

        for (QName name : params.keySet()) {
            RuntimeValue v = params.get(name);
            if (runtime.getAllowGeneralExpressions()) {
                transformer.setParameter(name, v.getValue());
            } else {
                transformer.setParameter(name, new XdmAtomicValue(v.getString()));
            }
        }

        if (document != null) {
            transformer.setInitialContextNode(document);
        }
        transformer.setMessageListener(new CatchMessages());
        XdmDestination result = new XdmDestination();
        transformer.setDestination(result);

        if (initialMode != null) {
            transformer.setInitialMode(initialMode);
        }

        if (templateName != null) {
            transformer.setInitialTemplate(templateName);
        }

        if (outputBaseURI != null) {
            transformer.setBaseOutputURI(outputBaseURI);
        }

        transformer.setSchemaValidationMode(ValidationMode.DEFAULT);
        transformer.transform();

        config.setOutputURIResolver(uriResolver);
        config.setCollectionURIResolver(collectionResolver);

        XdmNode xformed = result.getXdmNode();
        if (xformed != null) {
            // Can be null when nothing is written to the principle result tree...
            resultPipe.write(stepContext, xformed);
        }
    }

    class OutputResolver implements OutputURIResolver {
        public OutputResolver() {
        }

        public Result resolve(String href, String base) throws TransformerException {
            URI baseURI = null;
            try {
                baseURI = new URI(base);
                baseURI = baseURI.resolve(href);
            } catch (URISyntaxException use) {
                throw new XProcException(use);
            }

            finest(step.getNode(), "XSLT secondary result document: " + baseURI);

            try {
                XdmDestination xdmResult = new XdmDestination();
                secondaryResults.put(baseURI.toASCIIString(), xdmResult);
                Receiver receiver = xdmResult.getReceiver(runtime.getProcessor().getUnderlyingConfiguration());
                receiver.setSystemId(baseURI.toASCIIString());
                return receiver;
            } catch (SaxonApiException sae) {
                throw new XProcException(sae);
            }
        }

        public void close(Result result) throws TransformerException {
            String href = result.getSystemId();
            XdmDestination xdmResult = secondaryResults.get(href);
            XdmNode doc = xdmResult.getXdmNode();
            secondaryPipe.write(stepContext, doc);
        }
    }

    class CatchMessages implements MessageListener {
        public CatchMessages() {
        }

        public void message(XdmNode content, boolean terminate, javax.xml.transform.SourceLocator locator) {
            TreeWriter treeWriter = new TreeWriter(runtime);
            treeWriter.startDocument(content.getBaseURI());
            treeWriter.addStartElement(XProcConstants.c_error);
            treeWriter.startContent();

            treeWriter.addSubtree(content);

            treeWriter.addEndElement();
            treeWriter.endDocument();

            step.reportError(treeWriter.getResult());

            System.err.println(content);
            //finest(step.getNode(), "xsl:messsage (terminate=" + terminate + "): " + content);
        }
    }
}
