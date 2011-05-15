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
package com.xmlcalabash.extensions;

import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.library.DefaultStep;
import com.xmlcalabash.runtime.XAtomicStep;
import com.xmlcalabash.util.RelevantNodes;
import com.xmlcalabash.util.S9apiUtils;
import com.xmlcalabash.util.TreeWriter;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import org.apache.commons.httpclient.Cookie;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SetCookies extends DefaultStep {
    private static final QName _cookies = new QName("","cookies");
    private static final QName _domain = new QName("","domain");
    private static final QName _name = new QName("","name");
    private static final QName _value = new QName("","value");
    private static final QName _path = new QName("","path");
    private static final QName _expires = new QName("","expires");
    private static final QName _version = new QName("", "version");
    private static final QName _secure = new QName("","secure");
    private static final QName c_cookies = new QName("c", XProcConstants.NS_XPROC_STEP, "cookies");
    private static final QName c_cookie = new QName("c", XProcConstants.NS_XPROC_STEP, "cookie");
    private static DateFormat iso8601tz = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    private static DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private ReadablePipe source = null;

    /**
     * Creates a new instance of Identity
     */
    public SetCookies(XProcRuntime runtime, XAtomicStep step) {
        super(runtime,step);
    }

    public void setInput(String port, ReadablePipe pipe) {
        source = pipe;
    }

    public void reset() {
        source.resetReader(stepContext);
    }

    public void gorun() throws SaxonApiException {
        super.gorun();

        String cookieKey = getOption(_cookies).getString();

        XdmNode doc = source.read(stepContext);
        XdmNode root = S9apiUtils.getDocumentElement(doc);
        if (!c_cookies.equals(root.getNodeName())) {
            throw new XProcException(step.getNode(), "The input to cx:set-cookies must be a c:cookies document.");
        }
        
        for (XdmNode node : new RelevantNodes(null, root, Axis.CHILD)) {
            if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
                if (!c_cookie.equals(node.getNodeName())) {
                    throw new XProcException(step.getNode(), "A c:cookies document must contain only c:cookie elements.");
                }

                String domain = node.getAttributeValue(_domain);
                String name = node.getAttributeValue(_name);
                String value = node.getAttributeValue(_value);
                String path = node.getAttributeValue(_path);
                String expires = node.getAttributeValue(_expires);

                if (name == null || value == null) {
                    throw new XProcException(step.getNode(), "Invalid cookie: " + node);
                }

                Cookie cookie = new Cookie();
                cookie.setName(name);
                cookie.setValue(value);

                if (domain != null) { cookie.setDomain(domain); }
                if (path != null) { cookie.setPath(path); }

                if (expires != null) {
                    Date date = null;
                    try {
                        if (expires.length() > 21) {
                            // expires = yyyy-MM-dd'T'HH:mm:ss+00:00"
                            expires = expires.substring(0,22) + expires.substring(23);
                            date = iso8601tz.parse(expires);
                        } else {
                            // expires = yyyy-MM-dd'T'HH:mm:ss"
                            date = iso8601.parse(expires);
                        }

                        cookie.setExpiryDate(date);
                    } catch (ParseException pe) {
                        throw new XProcException(pe);
                    }
                }

                runtime.addCookie(cookieKey, cookie);

            } else if (node.getNodeKind() == XdmNodeKind.TEXT) {
                if ("".equals(node.getStringValue().trim())) {
                    // nop
                } else {
                    throw new XProcException(step.getNode(), "A c:cookies document must not contain non-whitespace text nodes.");
                }
            }
        }
    }
}