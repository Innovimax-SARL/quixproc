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
package com.xmlcalabash.util;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;

import com.xmlcalabash.config.XMLCalabashConfigurer;
import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.io.ReadableData;
import com.xmlcalabash.io.ReadableDocument;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.library.Load;
import com.xmlcalabash.model.DataBinding;
import com.xmlcalabash.model.DocumentBinding;
import com.xmlcalabash.model.RuntimeValue;

/**
 * Created by IntelliJ IDEA.
 * User: ndw
 * Date: 9/1/11
 * Time: 9:13 AM
 * To change this template use File | Settings | File Templates.
 */
public class DefaultXMLCalabashConfigurer implements XMLCalabashConfigurer {
    private static final QName _href = new QName("href");
    private static final QName _dtd_validate = new QName("dtd-validate");
    private final static QName cx_filemask = new QName("cx", XProcConstants.NS_CALABASH_EX,"filemask");
    protected XProcRuntime runtime = null;

    public DefaultXMLCalabashConfigurer(XProcRuntime runtime) {
        this.runtime = runtime;
    }

    public void configRuntime(XProcRuntime runtime) {
        // Do nothing
    }

    public XdmNode loadDocument(Load load) {
        boolean      validate = load.getOption(_dtd_validate, false);
        RuntimeValue href     = load.getOption(_href);
        String       base     = href.getBaseURI().toASCIIString();
        if (runtime.getSafeMode() && base.startsWith("file:")) {
            throw XProcException.dynamicError(21);
        }
        return runtime.parse(href.getString(), base, validate);
    }

    public ReadablePipe makeReadableData(XProcRuntime runtime, DataBinding binding) {
        return new ReadableData(runtime, binding.getWrapper(), binding.getHref(), binding.getContentType());
    }

    public ReadablePipe makeReadableDocument(XProcRuntime runtime, DocumentBinding binding) {
        String mask = binding.getExtensionAttribute(cx_filemask);
        String base = binding.getNode().getBaseURI().toASCIIString();
        return new ReadableDocument(runtime, binding.getNode(), binding.getHref(), base, mask);
    }
}
