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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.Vector;

import jp.co.antenna.XfoJavaCtl.MessageListener;
import jp.co.antenna.XfoJavaCtl.XfoException;
import jp.co.antenna.XfoJavaCtl.XfoFormatPageListener;
import jp.co.antenna.XfoJavaCtl.XfoObj;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;

import com.xmlcalabash.config.CssProcessor;
import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.runtime.XStep;

/**
 * Created by IntelliJ IDEA.
 * User: ndw
 * Date: 9/1/11
 * Time: 4:24 PM
 * To change this template use File | Settings | File Templates.
 */
public class CssAH implements CssProcessor {
    private static final QName _content_type = new QName("content-type");
    private static final QName _encoding = new QName("", "encoding");

    XProcRuntime runtime = null;
    Properties options = null;
    String primarySS = null;
    Vector<String> userSS = new Vector<String> ();

    XStep step = null;
    XfoObj ah = null;

    public void initialize(XProcRuntime runtime, XStep step, Properties options) {
        this.runtime = runtime;
        this.step = step;
        this.options = options;
        try {
            ah = new XfoObj();
            ah.setFormatterType(XfoObj.S_FORMATTERTYPE_XMLCSS);
            FoMessages msgs = new FoMessages();
            ah.setMessageListener(msgs);

            String s = getStringProp("OptionsFileURI");
            if (s != null) {
                ah.setOptionFileURI(s);
            }

            ah.setExitLevel(4);
            Integer i = getIntProp("ExitLevel");
            if (i != null) {
                ah.setExitLevel(i);
            }

            s = getStringProp("EmbedAllFontsEx");
            if (s != null) {
                if ("part".equals(s.toLowerCase())) {
                    ah.setPdfEmbedAllFontsEx(XfoObj.S_PDF_EMBALLFONT_PART);
                } else if ("base14".equals(s.toLowerCase())) {
                    ah.setPdfEmbedAllFontsEx(XfoObj.S_PDF_EMBALLFONT_BASE14);
                } else if ("all".equals(s.toLowerCase())) {
                    ah.setPdfEmbedAllFontsEx(XfoObj.S_PDF_EMBALLFONT_ALL);
                } else {
                    throw new XProcException("Unrecognized value for EmbedAllFontsEx");
                }
            }

            i = getIntProp("ImageCompression");
            if (i != null) {
                ah.setPdfImageCompression(i);
            }

            Boolean b = getBooleanProp("NoAccessibility");
            if (b != null) {
                ah.setPdfNoAccessibility(b);
            }

            b = getBooleanProp("NoAddingOrChangingComments");
            if (b != null) {
                ah.setPdfNoAddingOrChangingComments(b);
            }

            b = getBooleanProp("NoAssembleDoc");
            if (b != null) {
                ah.setPdfNoAssembleDoc(b);
            }

            b = getBooleanProp("NoChanging");
            if (b != null) {
                ah.setPdfNoChanging(b);
            }

            b = getBooleanProp("NoContentCopying");
            if (b != null) {
                ah.setPdfNoContentCopying(b);
            }

            b = getBooleanProp("NoFillForm");
            if (b != null) {
                ah.setPdfNoFillForm(b);
            }

            b = getBooleanProp("NoPrinting");
            if (b != null) {
                ah.setPdfNoPrinting(b);
            }

            s = getStringProp("OwnersPassword");
            if (s != null) {
                ah.setPdfOwnerPassword(s);
            }

            b = getBooleanProp("TwoPassFormatting");
            if (b != null) {
                ah.setTwoPassFormatting(b);
            }
        } catch (XfoException xfoe) {
            throw new XProcException(xfoe);
        }
    }

    public void addStylesheet(XdmNode doc) {
        doc = S9apiUtils.getDocumentElement(doc);

        String stylesheet = null;
        if ((XProcConstants.c_data.equals(doc.getNodeName())
             && "application/octet-stream".equals(doc.getAttributeValue(_content_type)))
            || "base64".equals(doc.getAttributeValue(_encoding))) {
            byte[] decoded = Base64.decode(doc.getStringValue());
            stylesheet = new String(decoded);
        } else {
            stylesheet = doc.getStringValue();
        }

        String prefix = "temp";
        String suffix = ".css";

        File temp;
        try {
            temp = File.createTempFile(prefix, suffix);
        } catch (IOException ioe) {
            throw new XProcException(step.getNode(), "Failed to create temporary file for CSS");
        }

        temp.deleteOnExit();

        try {
            PrintStream cssout = new PrintStream(temp);
            cssout.print(stylesheet);
            cssout.close();
        } catch (FileNotFoundException fnfe) {
            throw new XProcException(step.getNode(), "Failed to write to temporary CSS file");
        }

        if (primarySS == null) {
            primarySS = temp.toURI().toASCIIString();
        } else {
            userSS.add(temp.toURI().toASCIIString());
        }
    }

    public void format(XdmNode doc, OutputStream out, String contentType) {
        String outputFormat = null;
        if (contentType == null || "application/pdf".equals(contentType)) {
            outputFormat = "@PDF";
        } else if ("application/PostScript".equals(contentType)) {
            outputFormat = "@PS";
        } else if ("image/svg+xml".equals(contentType)) {
            outputFormat = "@SVG";
        } else if ("application/vnd.inx".equals(contentType)) {
            outputFormat = "@INX";
        } else if ("application/vnd.mif".equals(contentType)) {
            outputFormat = "@MIF";
        } else if ("text/plain".equals(contentType)) {
            outputFormat = "@TXT";
        } else {
            throw new XProcException(step.getNode(), "Unsupported content-type on p:xsl-formatter: " + contentType);
        }

        try {
            if (primarySS == null) {
                throw new XProcException("No CSS stylesheets provided");
            } else {
                ah.setStylesheetURI(primarySS);
            }

            for (String uri : userSS) {
                ah.addUserStylesheetURI(uri);
            }

            // Holy hack on a cracker!
            ByteArrayInputStream bis = new ByteArrayInputStream(doc.toString().getBytes("UTF-8"));
            ah.render(bis, out, outputFormat);
            ah.releaseObjectEx();
        } catch (XfoException e) {
            if (runtime.getDebug()) {
                System.out.println("ErrorLevel = " + e.getErrorLevel() + "\nErrorCode = " + e.getErrorCode() + "\n" + e.getErrorMessage());
                e.printStackTrace();
            }
            throw new XProcException(e);
        } catch (UnsupportedEncodingException e) {
            // won't happen
            throw new XProcException(e);
        }
    }

    private String getStringProp(String name) {
        return options.getProperty(name);
    }

    private Integer getIntProp(String name) {
        String s = getStringProp(name);
        if (s != null) {
            try {
                int i = Integer.parseInt(s);
                return new Integer(i);
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
        return null;
    }

    private Boolean getBooleanProp(String name) {
        String s = options.getProperty(name);
        if (s != null) {
            return "true".equals(s);
        }
        return null;
    }

    private class FoMessages implements MessageListener, XfoFormatPageListener {
	    public void onMessage(int errLevel, int errCode, String errMessage) {
            switch (errLevel) {
                case 1:
                    step.info(step.getNode(), errMessage);
                    return;
                case 2:
                    step.warning(step.getNode(), errMessage);
                    return;
                default:
                    step.error(step.getNode(), errMessage, XProcConstants.stepError(errCode));
                    return;
            }
	    }

        public void onFormatPage(int pageNo) {
            step.finest(step.getNode(), "Formatted PDF page " + pageNo);
        }
    }
}
