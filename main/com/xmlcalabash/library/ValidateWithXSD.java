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

import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.util.S9apiUtils;
import net.sf.saxon.Configuration;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.SchemaManager;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SchemaValidator;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.Controller;
import net.sf.saxon.om.NodeInfo;

import com.xmlcalabash.runtime.XAtomicStep;

import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.Source;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import javax.xml.XMLConstants;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Vector;
import java.io.IOException;

public class ValidateWithXSD extends DefaultStep {
    private static final QName _assert_valid = new QName("", "assert-valid");
    private static final QName _mode = new QName("", "mode");
    private static final QName _use_location_hints = new QName("", "use-location-hints");
    private static final QName _try_namespaces = new QName("", "try-namespaces");
    private static final Class [] paramTypes = new Class [] {};
    private ReadablePipe source = null;
    private ReadablePipe schemas = null;
    private WritablePipe result = null;

    /** Creates a new instance of ValidateWithXSD */
    public ValidateWithXSD(XProcRuntime runtime, XAtomicStep step) {
        super(runtime,step);
    }

    public void setInput(String port, ReadablePipe pipe) {
        if ("source".equals(port)) {
            source = pipe;
        } else if ("schema".equals(port)) {
            schemas = pipe;
        }
    }

    public void setOutput(String port, WritablePipe pipe) {
        result = pipe;
    }

    public void reset() {
        source.resetReader(stepContext);
        result.resetWriter(stepContext);
    }

    public void gorun() throws SaxonApiException {
        super.gorun();

        Processor proc = runtime.getProcessor();
        SchemaManager manager = proc.getSchemaManager();

        if (manager == null) {
            validateWithXerces();
        } else {
            validateWithSaxonSA(manager);
        }
    }

    public void validateWithSaxonSA(SchemaManager manager) throws SaxonApiException {
        info(step.getNode(), "Validating with Saxon");

        Configuration config = runtime.getProcessor().getUnderlyingConfiguration();

        // Saxon 9.2.0.4j introduces a clearSchemaCache method on Configuration.
        // Call it if it's available.
        try {
            Method clearSchemaCache = config.getClass().getMethod("clearSchemaCache", paramTypes);
            clearSchemaCache.invoke(config);
            finer(step.getNode(), "Cleared schema cache.");
        } catch (NoSuchMethodException nsme) {
            // nop; oh, well
            finer(step.getNode(), "Cannot reset schema cache.");
        } catch (IllegalAccessException nsme) {
            // nop; oh, well
            finer(step.getNode(), "Cannot reset schema cache.");
        } catch (InvocationTargetException nsme) {
            // nop; oh, well
            finer(step.getNode(), "Cannot reset schema cache.");
        }

        XdmNode doc = source.read(stepContext);
        String namespace = S9apiUtils.getDocumentElement(doc).getNodeName().getNamespaceURI();
        boolean tryNamespaces = getOption(_try_namespaces, false) && !"".equals(namespace);

        // Populate the URI cache so that URI references in schema documents will find
        // the schemas provided preferentially
        Vector<XdmNode> schemaDocuments = new Vector<XdmNode> ();
        while (schemas.moreDocuments(stepContext)) {
            XdmNode schemaNode = schemas.read(stepContext);
            String targetNS = schemaNode.getBaseURI().toASCIIString();
            fine(step.getNode(), "Caching input schema: " + targetNS);
            if (targetNS.equals(namespace)) {
                tryNamespaces = false;
            }
            schemaDocuments.add(schemaNode);
            runtime.getResolver().cache(schemaNode, schemaNode.getBaseURI());
        }

        if (tryNamespaces) {
            // Need to load one more schema
            try {
                XdmNode nsSchemaDoc = runtime.parse(namespace, doc.getBaseURI().toASCIIString(), false);
                schemaDocuments.add(nsSchemaDoc);
                runtime.getResolver().cache(nsSchemaDoc, nsSchemaDoc.getBaseURI());
            } catch (Exception e) {
                // nevermind
            }
        }


        // FIXME: HACK! Do this the right way
        for (XdmNode schemaNode : schemaDocuments) {
            InputSource schemaSource = S9apiUtils.xdmToInputSource(runtime, schemaNode);
            schemaSource.setSystemId(schemaNode.getBaseURI().toASCIIString());
            SAXSource source = new SAXSource(schemaSource);
            manager.load(source);
        }

        XdmDestination destination = new XdmDestination();
        Controller controller = new Controller(config);
        Receiver receiver = destination.getReceiver(controller.getConfiguration());
        PipelineConfiguration pipe = controller.makePipelineConfiguration();
        pipe.setRecoverFromValidationErrors(!getOption(_assert_valid,false));
        receiver.setPipelineConfiguration(pipe);

        SchemaValidator validator = manager.newSchemaValidator();
        validator.setDestination(destination);

        String mode = getOption(_mode, "strict");
        validator.setLax("lax".equals(mode));

        boolean useHints = getOption(_use_location_hints, false);
        validator.setUseXsiSchemaLocation(useHints);
        
        try {
            fine(step.getNode(), "Validating: " + doc.getBaseURI().toASCIIString());
            validator.validate(new SAXSource(S9apiUtils.xdmToInputSource(runtime, doc)));
        } catch (SaxonApiException sae) {
            if (getOption(_assert_valid,false)) {
                throw new XProcException(XProcConstants.stepError(53), sae);
            }
        }
        
        XdmNode valid = destination.getXdmNode();
        result.write(stepContext, valid);
    }

    private void validateWithXerces() throws SaxonApiException {
        info(step.getNode(), "Validating with Xerces");

        Vector<XdmNode> schemaDocuments = new Vector<XdmNode> ();
        while (schemas.moreDocuments(stepContext)) {
            XdmNode schemaNode = schemas.read(stepContext);
            schemaDocuments.add(schemaNode);
            runtime.getResolver().cache(schemaNode, schemaNode.getBaseURI());
        }

        XdmNode doc = source.read(stepContext);

        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

            XdmNode schemaNode = schemaDocuments.get(0);
            InputSource is = S9apiUtils.xdmToInputSource(runtime, schemaNode);
            is.setSystemId(schemaNode.getBaseURI().toASCIIString());
            Schema schema = factory.newSchema(new SAXSource(is));
            Validator validator = schema.newValidator();

            InputSource docSource = S9apiUtils.xdmToInputSource(runtime, doc);
            docSource.setSystemId(doc.getBaseURI().toASCIIString());

            try {
                validator.validate(new SAXSource(docSource));
            } catch (SAXParseException spe) {
                if (getOption(_assert_valid, false)) {
                    throw new XProcException(XProcConstants.stepError(53), spe);
                }
            }
        } catch (SAXException se) {
            throw new XProcException(se);
        } catch (IOException ioe) {
            throw new XProcException(ioe);
        }

        result.write(stepContext, doc);
    }

}

