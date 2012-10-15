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
package com.xmlcalabash.model;

import java.util.HashSet;
import java.util.Hashtable;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;

/**
 * Created by IntelliJ IDEA.
 * User: ndw
 * Date: Jul 21, 2008
 * Time: 8:45:01 PM
 * To change this template use File | Settings | File Templates.
 */
public class NamespaceBinding {
    private XdmNode node = null;
    private XProcRuntime runtime = null;
    private String binding = null;
    private String expr = null;
    private HashSet<String> except = new HashSet<String> (); // Default is nothing excluded
    private Hashtable<String,String> nsBindings = new Hashtable<String,String> ();

    public NamespaceBinding(XProcRuntime xproc, XdmNode node) {
        runtime = xproc;
        this.node = node;

        XdmSequenceIterator nsIter = node.axisIterator(Axis.NAMESPACE);
        while (nsIter.hasNext()) {
            XdmNode ns = (XdmNode) nsIter.next();
            nsBindings.put((ns.getNodeName()==null ? "" : ns.getNodeName().getLocalName()),ns.getStringValue());
        }
    }

    public XdmNode getNode() {
        return node;
    }

    public void setBinding(String binding) {
        nsBindings = null;
        this.binding = binding;
        if (binding != null && expr != null) {
            throw XProcException.staticError(41);
        }
    }

    public String getBinding() {
        return binding;
    }

    public void setXPath(String expr) {
        nsBindings = null;
        this.expr = expr;
        if (binding != null && expr != null) {
            throw XProcException.staticError(41);
        }
    }

    public String getXPath() {
        return expr;
    }

    public Hashtable<String,String> getNamespaceBindings() {
        return nsBindings;
    }

    public void addExcludedNamespace(String exclude) {
        except.add(exclude);
    }

    public HashSet<String> getExcludedNamespaces() {
        return except;
    }
}
