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
package com.xmlcalabash.util;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.Destination;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.TreeReceiver;
import net.sf.saxon.event.NamespaceReducer;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.Configuration;
import com.xmlcalabash.core.XProcRuntime;

import java.util.Iterator;
import java.util.Vector;
import java.util.HashSet;
import java.net.URI;
import java.io.StringWriter;
import java.io.StringReader;

import net.sf.saxon.tree.iter.NamespaceIterator;
import org.xml.sax.InputSource;

public class S9apiUtils {
    private static final QName vara = new QName("","vara");
    private static final QName varb = new QName("","varb");

    /**
     * Write an XdmValue to a given destination. The sequence represented by the XdmValue is "normalized"
     * as defined in the serialization specification (this is equivalent to constructing a document node
     * in XSLT or XQuery with this sequence as the content expression), and the resulting document is
     * then copied to the destination. If the destination is a serializer this has the effect of serializing
     * the sequence as described in the W3C specifications.
     * @param values the value to be written
     * @param destination the destination to which the value is to be written
     */

    public static void writeXdmValue(XProcRuntime runtime, Vector<XdmValue> values, Destination destination, URI baseURI) throws SaxonApiException {
        writeXdmValue(runtime.getProcessor(), values, destination, baseURI);
    }

    public static void writeXdmValue(Processor proc, Vector<XdmValue> values, Destination destination, URI baseURI) throws SaxonApiException {
        try {
            Configuration config = proc.getUnderlyingConfiguration();
            PipelineConfiguration pipeConfig = config.makePipelineConfiguration();

            Receiver out = destination.getReceiver(config);
            out = new NamespaceReducer(out);
            TreeReceiver tree = new TreeReceiver(out);
            tree.setPipelineConfiguration(pipeConfig);
            if (baseURI != null) {
                tree.setSystemId(baseURI.toASCIIString());
            }
            tree.open();
            tree.startDocument(0);
            for (XdmValue value : values) {
                for (XdmItem item : (Iterable<XdmItem>) value) {
                    tree.append((Item) item.getUnderlyingValue(), 0,
                            NodeInfo.ALL_NAMESPACES);
                }
            }
            tree.endDocument();
            tree.close();
        } catch (XPathException err) {
            throw new SaxonApiException(err);
        }
    }

    public static void writeXdmValue(XProcRuntime runtime, XdmItem node, Destination destination, URI baseURI) throws SaxonApiException {
        try {
            Processor proc = runtime.getProcessor();
            Configuration config = proc.getUnderlyingConfiguration();
            PipelineConfiguration pipeConfig = config.makePipelineConfiguration();

            Receiver out = destination.getReceiver(config);
            out = new NamespaceReducer(out);
            TreeReceiver tree = new TreeReceiver(out);
            tree.setPipelineConfiguration(pipeConfig);
            if (baseURI != null) {
                tree.setSystemId(baseURI.toASCIIString());
            }
            tree.open();
            tree.startDocument(0);
            tree.append((Item) node.getUnderlyingValue(), 0, NodeInfo.ALL_NAMESPACES);
            tree.endDocument();
            tree.close();
        } catch (XPathException err) {
            throw new SaxonApiException(err);
        }
    }

    public static XdmNode getDocumentElement(XdmNode doc) {
        if (doc.getNodeKind() == XdmNodeKind.DOCUMENT) {
            for (XdmNode node : new RelevantNodes(doc, Axis.CHILD,true)) {
                if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
                    return node; // There can be only one, this is an XML document
                }
            }
            return null;
        } else {
            return doc;
        }
    }

    public static void serialize(XProcRuntime xproc, XdmNode node, Serializer serializer) throws SaxonApiException {
        Processor qtproc = xproc.getProcessor();
        XQueryCompiler xqcomp = qtproc.newXQueryCompiler();
        XQueryExecutable xqexec = xqcomp.compile(".");
        XQueryEvaluator xqeval = xqexec.load();
        xqeval.setContextItem(node);
        xqeval.setDestination(serializer);
        xqeval.run();
    }

    public static void serialize(XProcRuntime xproc, Vector<XdmNode> nodes, Serializer serializer) throws SaxonApiException {
        Processor qtproc = xproc.getProcessor();
        XQueryCompiler xqcomp = qtproc.newXQueryCompiler();
        XQueryExecutable xqexec = xqcomp.compile(".");
        XQueryEvaluator xqeval = xqexec.load();
        xqeval.setDestination(serializer);
        for (XdmNode node : nodes) {
            xqeval.setContextItem(node);
            xqeval.run();
            // Even if we output an XML decl before the first node, we must not do it before any others!
            serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
        }
    }

    public static boolean xpathEqual(Processor proc, XdmItem a, XdmItem b) {
        try {
            XPathCompiler c = proc.newXPathCompiler();
            c.declareVariable(vara);
            c.declareVariable(varb);

            XPathExecutable xexec = c.compile("$vara = $varb");
            XPathSelector selector = xexec.load();

            selector.setVariable(vara,a);
            selector.setVariable(varb,b);

            Iterator<XdmItem> values = selector.iterator();
            XdmAtomicValue item = (XdmAtomicValue) values.next();
            boolean same = item.getBooleanValue();
            return same;
        } catch (SaxonApiException sae) {
            return false;
        }
    }

    // FIXME: THIS METHOD IS A GROTESQUE HACK!
    public static InputSource xdmToInputSource(XProcRuntime runtime, XdmNode node) throws SaxonApiException {
        StringWriter sw = new StringWriter();
        Serializer serializer = new Serializer();
        serializer.setOutputWriter(sw);
        serialize(runtime, node, serializer);

        String serxml = sw.toString();

        StringReader sr = new StringReader(serxml);
        InputSource isource = new InputSource(sr);
        isource.setSystemId(node.getBaseURI().toASCIIString());
        //SAXSource source = new SAXSource(isource);
        //return source;
        return isource;
    }

    public static XdmNode removeNamespaces(XProcRuntime runtime, XdmNode node, HashSet<String> excludeNS) {
        return removeNamespaces(runtime.getProcessor(), node, excludeNS);
    }

    public static XdmNode removeNamespaces(Processor proc, XdmNode node, HashSet<String> excludeNS) {
        TreeWriter tree = new TreeWriter(proc);
        tree.startDocument(node.getBaseURI());
        removeNamespacesWriter(tree, node, excludeNS);
        tree.endDocument();
        return tree.getResult();
    }

    private static void removeNamespacesWriter(TreeWriter tree, XdmNode node, HashSet<String> excludeNS) {
        if (node.getNodeKind() == XdmNodeKind.DOCUMENT) {
            XdmSequenceIterator iter = node.axisIterator(Axis.CHILD);
            while (iter.hasNext()) {
                XdmNode cnode = (XdmNode) iter.next();
                removeNamespacesWriter(tree, cnode, excludeNS);
            }
        } else if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
            boolean usesDefaultNS = ("".equals(node.getNodeName().getPrefix())
                                     && !"".equals(node.getNodeName().getNamespaceURI()));

            NodeInfo inode = node.getUnderlyingNode();
            NamePool pool = inode.getNamePool();
            int inscopeNS[] = NamespaceIterator.getInScopeNamespaceCodes(inode);

            boolean excludeDefault = false;
            boolean changed = false;
            int newNS[] = null;
            if (inscopeNS.length > 0) {
                newNS = new int[inscopeNS.length];
                int newpos = 0;
                for (int pos = 0; pos < inscopeNS.length; pos++) {
                    int ns = inscopeNS[pos];
                    String pfx = pool.getPrefixFromNamespaceCode(ns);
                    String uri = pool.getURIFromNamespaceCode(ns);

                    boolean delete = excludeNS.contains(uri);
                    excludeDefault = excludeDefault || ("".equals(pfx) && delete);

                    // You can't exclude the default namespace if it's in use
                    if ("".equals(pfx) && usesDefaultNS) {
                        delete = false;
                    }

                    changed = changed || delete;

                    if (!delete) {
                        newNS[newpos++] = ns;
                    }
                }
                int onlyNewNS[] = new int[newpos];
                for (int pos = 0; pos < newpos; pos++) {
                    onlyNewNS[pos] = newNS[pos];
                }
                newNS = onlyNewNS;
            }

            // Careful, we're messing with the namespace bindings
            // Make sure the nameCode is right...
            int nameCode = inode.getNameCode();
            int typeCode = inode.getTypeAnnotation() & NamePool.FP_MASK;
            String pfx = pool.getPrefix(nameCode);
            String uri = pool.getURI(nameCode);

            if (excludeDefault && "".equals(pfx) && !usesDefaultNS) {
                nameCode = pool.allocate("", "", pool.getLocalName(nameCode));
            }

            tree.addStartElement(nameCode, typeCode, newNS);
            tree.addAttributes(node);

            XdmSequenceIterator iter = node.axisIterator(Axis.CHILD);
            while (iter.hasNext()) {
                XdmNode cnode = (XdmNode) iter.next();
                removeNamespacesWriter(tree, cnode, excludeNS);
            }
            tree.addEndElement();
        } else {
            tree.addSubtree(node);
        }
    }

    public static void dumpTree(XdmNode tree, String message) {
        NodeInfo treeNode = tree.getUnderlyingNode();
        System.err.println(message);
        System.err.println("Dumping tree: " + treeNode.getSystemId() + ", " + tree.getBaseURI());
        XdmSequenceIterator iter = tree.axisIterator(Axis.CHILD);
        while (iter.hasNext()) {
            XdmNode child = (XdmNode) iter.next();
            dumpTreeNode(child, "  ");
        }
    }

    private static void dumpTreeNode(XdmNode node, String indent) {
        if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
            System.err.println(indent + node.getNodeName() + ": " + node.getBaseURI());
            XdmSequenceIterator iter = node.axisIterator(Axis.CHILD);
            while (iter.hasNext()) {
                XdmNode child = (XdmNode) iter.next();
                dumpTreeNode(child, indent + "  ");
            }
        } else if (node.getNodeKind() == XdmNodeKind.TEXT) {
            System.err.println(indent + "text: ...");
        }
    }

    public static boolean xpathSyntaxError(SaxonApiException sae) {
        Throwable cause = sae.getCause();
        // FIME: Is this right? Are all XPathExceptions syntax errors?
        return (cause != null && cause instanceof XPathException);
    }

    public static
    boolean isDocument(XdmNode doc) {
        boolean ok = true;

        if (doc.getNodeKind() == XdmNodeKind.DOCUMENT) {
            ok = S9apiUtils.isDocumentContent(doc.axisIterator(Axis.CHILD));
        } else if (doc.getNodeKind() == XdmNodeKind.ELEMENT) {
            // this is ok
        } else {
            ok = false;
        }

        return ok;
    }

    public static
    boolean isDocumentContent(XdmSequenceIterator iter) {
        boolean ok = true;

        int elemCount = 0;
        while (ok && iter.hasNext()) {
            XdmNode child = (XdmNode) iter.next();
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                elemCount++;
                ok = ok && elemCount == 1;
            } else if (child.getNodeKind() == XdmNodeKind.PROCESSING_INSTRUCTION
                    || child.getNodeKind() == XdmNodeKind.COMMENT) {
                // that's ok
            } else if (child.getNodeKind() == XdmNodeKind.TEXT) {
                ok = ok && "".equals(child.getStringValue().trim());
            } else {
                ok = false;
            }
        }

        return ok;
    }

}
