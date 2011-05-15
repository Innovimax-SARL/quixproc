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

import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.model.RuntimeValue;
import com.xmlcalabash.runtime.XAtomicStep;
import com.xmlcalabash.util.ProcessMatch;
import com.xmlcalabash.util.ProcessMatchingNodes;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.tree.iter.NamespaceIterator;

import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;

public class Template extends DefaultStep implements ProcessMatchingNodes {
    private ReadablePipe source = null;
    private ReadablePipe template = null;
    private WritablePipe result = null;
    private Hashtable<QName,RuntimeValue> params = new Hashtable<QName, RuntimeValue> ();
    private ProcessMatch matcher = null;
    private XdmNode context = null;

    private static final int START = 0;
    private static final int XPATHMODE = 1;
    private static final int SQUOTEMODE = 2;
    private static final int DQUOTEMODE = 3;
    private static final int END = 4;

    /** Creates a new instance of LabelElements */
    public Template(XProcRuntime runtime, XAtomicStep step) {
        super(runtime,step);
    }

    public void setInput(String port, ReadablePipe pipe) {
        if ("source".equals(port)) {
            source = pipe;
        } else if ("template".equals(port)) {
            template = pipe;
        } else {
            throw new UnsupportedOperationException("WTF?");
        }
    }

    public void setOutput(String port, WritablePipe pipe) {
        result = pipe;
    }

    public void setParameter(QName name, RuntimeValue value) {
        params.put(name, value);
    }

    public void reset() {
        source.resetReader(stepContext);
        result.resetWriter(stepContext);
    }

    public void gorun() throws SaxonApiException {
        if (step.getNode().getNodeName().equals(XProcConstants.p_document_template)) {
            runtime.fine(this, step.getNode(), "The template step should be named p:template, the name p:document-template is deprecated.");
        }

        super.gorun();

        if (source.documentCount(stepContext) > 1) {
            throw XProcException.stepError(68);
        }

        context = source.read(stepContext);

        matcher = new ProcessMatch(runtime, this);
        matcher.match(template.read(stepContext), new RuntimeValue("node()", step.getNode()));

        result.write(stepContext, matcher.getResult());
    }

    public boolean processStartDocument(XdmNode node) throws SaxonApiException {
        matcher.startDocument(node.getBaseURI());
        return true;
    }

    public void processEndDocument(XdmNode node) throws SaxonApiException {
        matcher.endDocument();
    }

    public boolean processStartElement(XdmNode node) throws SaxonApiException {
        matcher.addStartElement(node);

        XdmSequenceIterator iter = node.axisIterator(Axis.ATTRIBUTE);
        while (iter.hasNext()) {
            XdmNode attr = (XdmNode) iter.next();
            String value = attr.getStringValue();
            if (value.contains("{") || value.contains("}")) {
                Vector<XdmItem> items = parse(attr, value);
                String newvalue = "";
                for (XdmItem item : items) {
                    newvalue += item.getStringValue();
                }
                matcher.addAttribute(attr, newvalue);
            } else {
                matcher.addAttribute(attr);
            }
        }

        return true;
    }

    public void processEndElement(XdmNode node) throws SaxonApiException {
        matcher.addEndElement();
    }

    public void processText(XdmNode node) throws SaxonApiException {
        String value = node.getStringValue();
        if (value.contains("{") || value.contains("}")) {
            Vector<XdmItem> items = parse(node, value);
            for (XdmItem item : items) {
                if (item.isAtomicValue()) {
                    matcher.addText(item.getStringValue());
                } else {
                    XdmNode nitem = (XdmNode) item;
                    switch (nitem.getNodeKind()) {
                        case ELEMENT:
                            matcher.addSubtree(nitem);
                            break;
                        case ATTRIBUTE:
                            matcher.addAttribute(nitem);
                            break;
                        case PROCESSING_INSTRUCTION:
                            matcher.addSubtree(nitem);
                            break;
                        case COMMENT:
                            matcher.addComment(nitem.getStringValue());
                            break;
                        default:
                            matcher.addText(nitem.getStringValue());
                    }
                }
            }
        } else {
            matcher.addText(value);
        }
    }

    private Vector<XdmItem> parse(XdmNode node, String value) {
        Vector<XdmItem> items = new Vector<XdmItem> ();
        int state = START;
        String ptext = "";
        String ch = "";

        Hashtable<String,String> nsbindings = new Hashtable<String,String> ();

        // FIXME: Surely there's a better way to do this?
        XdmNode parent = node.getParent();
        while (parent != null
                && parent.getNodeKind() != XdmNodeKind.ELEMENT
                && parent.getNodeKind() != XdmNodeKind.DOCUMENT) {
            parent = parent.getParent();
        }

        NodeInfo inode = parent.getUnderlyingNode();
        NamePool pool = inode.getNamePool();
        int inscopeNS[] = NamespaceIterator.getInScopeNamespaceCodes(inode);
        for (int nspos = 0; nspos < inscopeNS.length; nspos++) {
            int ns = inscopeNS[nspos];
            String nspfx = pool.getPrefixFromNamespaceCode(ns);
            String nsuri = pool.getURIFromNamespaceCode(ns);
            nsbindings.put(nspfx,nsuri);
        }

        String peek = "";
        int pos = 0;
        while (pos < value.length()) {
            ch = value.substring(pos,pos+1);

            switch (state) {
                case START:
                    if (pos+1 < value.length()) {
                        peek = value.substring(pos+1,pos+2);
                    } else {
                        peek = "";
                    }

                    if ("{".equals(ch)) {
                        if ("{".equals(peek)) {
                            ptext += "{";
                            pos++;
                        } else {
                            if (!"".equals(ptext)) {
                                items.add(new XdmAtomicValue(ptext));
                                ptext = "";
                            }
                            state = XPATHMODE;
                        }
                    } else if ("}".equals(ch)) {
                        if ("}".equals(peek)) {
                            ptext += "}";
                            pos++;
                        } else {
                            throw XProcException.stepError(67);
                        }
                    } else {
                        ptext += ch;
                    }
                    break;
                case XPATHMODE:
                    if ("{".equals(ch)) {
                        throw XProcException.stepError(67);
                    } else if ("'".equals(ch)) {
                        ptext += "'";
                        state = SQUOTEMODE;
                    } else if ("\"".equals(ch)) {
                        ptext += "\"";
                        state = DQUOTEMODE;
                    } else if ("}".equals(ch)) {                        
                        items.addAll(evaluateXPath(context, nsbindings, ptext, params));
                        ptext = "";
                        state = START;
                    } else {
                        ptext += ch;
                    }
                    break;
                case SQUOTEMODE:
                    if ("'".equals(ch)) {
                        ptext += "'";
                        state = XPATHMODE;
                    } else {
                        ptext += ch;
                    }
                    break;
                case DQUOTEMODE:
                    if (("\"").equals(ch)) {
                        ptext += "\"";
                        state = XPATHMODE;
                    } else {
                        ptext += ch;
                    }
                    break;
            }

            pos++;
        }

        if (state != START) {
            throw XProcException.stepError(67);
        }

        if (!"".equals(ptext)) {
            items.add(new XdmAtomicValue(ptext));
        }

        return items;
    }

    public void processComment(XdmNode node) throws SaxonApiException {
        String value = node.getStringValue();
        if (value.contains("{") || value.contains("}")) {
            Vector<XdmItem> items = parse(node, value);
            String newvalue = "";
            for (XdmItem item : items) {
                newvalue += item.getStringValue();
            }
            matcher.addComment(newvalue);
        } else {
            matcher.addComment(value);
        }
    }

    public void processPI(XdmNode node) throws SaxonApiException {
        String value = node.getStringValue();
        if (value.contains("{") || value.contains("}")) {
            Vector<XdmItem> items = parse(node, value);
            String newvalue = "";
            for (XdmItem item : items) {
                newvalue += item.getStringValue();
            }
            matcher.addPI(node.getNodeName().getLocalName(), newvalue);
        } else {
            matcher.addPI(node.getNodeName().getLocalName(), value);
        }
    }

    public void processAttribute(XdmNode node) throws SaxonApiException {
        throw new UnsupportedOperationException("This can't happen.");
    }

}

