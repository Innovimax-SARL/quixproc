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
package com.xmlcalabash.extensions;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import com.xmlcalabash.config.CssProcessor;
import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.library.DefaultStep;
import com.xmlcalabash.model.RuntimeValue;
import com.xmlcalabash.runtime.XAtomicStep;
import com.xmlcalabash.util.TreeWriter;
import com.xmlcalabash.util.URIUtils;

/**
 * Created by IntelliJ IDEA.
 * User: ndw
 * Date: Oct 21, 2008
 * Time: 6:59:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class CssFormatter extends DefaultStep {
    private static final QName _href = new QName("","href");
    private static final QName _content_type = new QName("","content-type");
    private ReadablePipe source = null;
    private ReadablePipe css = null;
    private WritablePipe result = null;
    private Properties options = new Properties();

    /** Creates a new instance of Unzip */
    public CssFormatter(XProcRuntime runtime, XAtomicStep step) {
        super(runtime,step);
    }

    public void setInput(String port, ReadablePipe pipe) {
        if ("source".equals(port)) {
            source = pipe;
        } else {
            css = pipe;
        }
    }

    public void setOutput(String port, WritablePipe pipe) {
        result = pipe;
    }

    public void setParameter(QName name, RuntimeValue value) {
        if (!"".equals(name.getNamespaceURI())) {
            throw new XProcException(step.getNode(), "The cx:css-formatter parameters are in no namespace: " + name + " (" + name.getNamespaceURI() + ")");
        }
        options.setProperty(name.getLocalName(), value.getString());
    }

    public void reset() {
        result.resetWriter(stepContext);
    }

    public void gorun() throws SaxonApiException {
        super.gorun();

        String cssClass = runtime.getConfiguration().cssProcessor;
        if (cssClass == null) {
            throw new XProcException("No CSS processor class defined");
        }

        CssProcessor provider = null;
        try {
            provider = (CssProcessor) Class.forName(cssClass).newInstance();
            provider.initialize(runtime,step,options);
        } catch (NoClassDefFoundError ncdfe) {
            provider = null;
            if (runtime.getDebug()) {
                ncdfe.printStackTrace();
            }
        } catch (Exception e) {
            provider = null;
            if (runtime.getDebug()) {
                e.printStackTrace();
            }
        }

        if (provider == null) {
            throw new XProcException(step.getNode(), "Failed to instantiate CSS provider");
        }

        while (css.moreDocuments(stepContext)) {
            XdmNode style = css.read(stepContext);
            provider.addStylesheet(style);
        }

        String contentType = null;
        if (getOption(_content_type) != null) {
            contentType = getOption(_content_type).getString();
        }

        String href = getOption(_href).getBaseURI().resolve(getOption(_href).getString()).toASCIIString();
        String output = null;
        if (href.startsWith("file:/")) {
            output = URIUtils.getFile(href).getPath();
        } else {
            throw new XProcException(step.getNode(), "Don't know how to write cx:css-formatter output to " + href);
        }

        OutputStream out = null;
        try {
            try {
                out = new BufferedOutputStream(new FileOutputStream(output));
                provider.format(source.read(stepContext),out,contentType);
            } catch (XProcException e) {
                throw e;
            } catch (Exception e) {
                throw new XProcException(step.getNode(), "Failed to style with CSS document", e);
            } finally {
                out.close();
            }
        } catch (IOException e) {
            // Oh, nevermind if we couldn't close the file
        }

        TreeWriter tree = new TreeWriter(runtime);
        tree.startDocument(step.getNode().getBaseURI());
        tree.addStartElement(XProcConstants.c_result);
        tree.startContent();
        tree.addText(href);
        tree.addEndElement();
        tree.endDocument();
        result.write(stepContext,tree.getResult());
    }
}
