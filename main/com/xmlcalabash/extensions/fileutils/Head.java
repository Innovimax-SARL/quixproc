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
package com.xmlcalabash.extensions.fileutils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;

import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.library.DefaultStep;
import com.xmlcalabash.model.RuntimeValue;
import com.xmlcalabash.runtime.XAtomicStep;
import com.xmlcalabash.util.TreeWriter;

/**
 * Created by IntelliJ IDEA.
 * User: ndw
 * Date: May 24, 2009
 * Time: 3:17:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class Head extends DefaultStep {
    private static final QName _href = new QName("href");
    private static final QName _count = new QName("count");
    private static final QName _fail_on_error = new QName("fail-on-error");
    private static final QName c_line = new QName("c", XProcConstants.NS_XPROC_STEP, "line");

    private WritablePipe result = null;

    /**
     * Creates a new instance of UriInfo
     */
    public Head(XProcRuntime runtime, XAtomicStep step) {
        super(runtime,step);
    }

    public void setOutput(String port, WritablePipe pipe) {
        result = pipe;
    }

    public void reset() {
        result.resetWriter(stepContext);
    }

    public void gorun() throws SaxonApiException {
        super.gorun();

        if (runtime.getSafeMode()) {
            throw XProcException.dynamicError(21);
        }

        boolean failOnError = getOption(_fail_on_error, true);
        int maxCount = getOption(_count, 10);

        RuntimeValue href = getOption(_href);
        URI uri = href.getBaseURI().resolve(href.getString());
        File file;
        if (!"file".equals(uri.getScheme())) {
            throw new XProcException(step.getNode(), "Only file: scheme URIs are supported by the copy step.");
        } else {
            file = new File(uri.getPath());
        }

        if (!file.exists()) {
             throw new XProcException(step.getNode(), "Cannot read: file does not exist: " + file.getAbsolutePath());
        }

        if (file.isDirectory()) {
             throw new XProcException(step.getNode(), "Cannot read: file is a directory: " + file.getAbsolutePath());
        }

        TreeWriter tree = new TreeWriter(runtime);
        tree.startDocument(step.getNode().getBaseURI());
        tree.addStartElement(XProcConstants.c_result);
        tree.startContent();

        try {
            FileReader rdr = new FileReader(file);
            BufferedReader brdr = new BufferedReader(rdr);
            String line = null;
            int count = 0;

            if (maxCount >= 0) {
                line = brdr.readLine();
                while (line != null && count < maxCount) {
                    tree.addStartElement(c_line);
                    tree.startContent();
                    tree.addText(line);
                    tree.addEndElement();
                    tree.addText("\n");
                    count++;
                    line = brdr.readLine();
                }
            } else {
                maxCount = -maxCount;
                line = "not null";
                while (line != null && count < maxCount) {
                    count++;
                    line = brdr.readLine();
                }

                line = brdr.readLine();
                while (line != null) {
                    tree.addStartElement(c_line);
                    tree.startContent();
                    tree.addText(line);
                    tree.addEndElement();
                    tree.addText("\n");
                    line = brdr.readLine();
                }
            }
            
            brdr.close();
            rdr.close();
        } catch (FileNotFoundException fnfe) {
            throw new XProcException(fnfe);
        } catch (IOException ioe) {
            throw new XProcException(ioe);
        }

        tree.addEndElement();
        tree.endDocument();

        result.write(stepContext,tree.getResult());
    }
}