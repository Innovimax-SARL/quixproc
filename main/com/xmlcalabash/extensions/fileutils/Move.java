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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
public class Move extends DefaultStep {
    private static final QName _href = new QName("href");
    private static final QName _target = new QName("target");
    private static final int bufsize = 8192;

    private WritablePipe result = null;

    /**
     * Creates a new instance of UriInfo
     */
    public Move(XProcRuntime runtime, XAtomicStep step) {
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

        RuntimeValue href = getOption(_href);
        URI uri = href.getBaseURI().resolve(href.getString());
        File file;
        if (!"file".equals(uri.getScheme())) {
            throw new XProcException(step.getNode(), "Only file: scheme URIs are supported by the delete step.");
        } else {
            file = new File(uri.getPath());
        }

        if (!file.exists()) {
             throw new XProcException(step.getNode(), "Cannot move: file does not exist: " + file.getAbsolutePath());
        }

        if (file.isDirectory()) {
             throw new XProcException(step.getNode(), "Cannot move: file is a directory: " + file.getAbsolutePath());
        }

        href = getOption(_target);
        uri = href.getBaseURI().resolve(href.getString());
        File target;
        if (!"file".equals(uri.getScheme())) {
            throw new XProcException(step.getNode(), "Only file: scheme URIs are supported by the copy step.");
        } else {
            target = new File(uri.getPath());
        }

        if (target.isDirectory()) {
            target = new File(target, file.getName());
            if (target.isDirectory()) {
                throw new XProcException(step.getNode(), "Cannot move: target is a directory: " + target.getAbsolutePath());
            }
        }

        TreeWriter tree = new TreeWriter(runtime);
        tree.startDocument(step.getNode().getBaseURI());
        tree.addStartElement(XProcConstants.c_result);
        tree.startContent();

        tree.addText(target.toURI().toASCIIString());

        try {
            FileInputStream src = new FileInputStream(file);
            FileOutputStream dst = new FileOutputStream(target);
            byte[] buffer = new byte[bufsize];
            int read = src.read(buffer, 0, bufsize);
            while (read >= 0) {
                dst.write(buffer, 0, read);
                read = src.read(buffer, 0, bufsize);
            }
            src.close();
            dst.close();
        } catch (FileNotFoundException fnfe) {
            throw new XProcException(fnfe);
        } catch (IOException ioe) {
            throw new XProcException(ioe);
        }

        if (!file.delete()) {
            throw new XProcException(step.getNode(), "Move failed: could not delete file: " + file.getAbsolutePath());
        }

        tree.addEndElement();
        tree.endDocument();

        result.write(stepContext,tree.getResult());
    }
}