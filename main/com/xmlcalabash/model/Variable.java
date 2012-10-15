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

import java.util.Vector;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;

import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcRuntime;

/**
 *
 * @author ndw
 */
public class Variable extends EndPoint implements ComputableValue {
    private QName name = null;
    private String select = null;
    private String type = null;
    private Vector<NamespaceBinding> nsBindings = new Vector<NamespaceBinding> ();

    /** Creates a new instance of Variable */
    public Variable(XProcRuntime xproc, XdmNode node) {
        super(xproc,node);
    }

    public void setName(QName name) {
        this.name = name;
    }

    public QName getName() {
        return name;
    }

    public String getType() {
        return null;
    }

    public QName getTypeAsQName() {
        return null;
    }

    public void setSelect(String select) {
        this.select = select;
    }

    public String getSelect() {
        return select;
    }

    public void addNamespaceBinding(NamespaceBinding binding) {
        nsBindings.add(binding);
    }

    public Vector<NamespaceBinding> getNamespaceBindings() {
        return nsBindings;
    }

    public boolean valid(Environment env) {
        boolean valid = true;

        if (bindings.size() > 1) {
            error("Variables can have at most one binding.", XProcConstants.dynamicError(8));
            valid = false;
        }

        if (select == null) {
            error("You must specify select on variable.", XProcConstants.staticError(16));
        }
        
        return valid;
    }

    public String toString() {
        return "variable " + name;
    }

    protected void dump(int depth) {
        String indent = "";
        for (int count = 0; count < depth; count++) {
            indent += " ";
        }

        System.err.println(indent + "variable " + getName());
        if (getBinding().size() == 0) {
            System.err.println(indent + "  no binding");
        }
        for (Binding binding : getBinding()) {
            binding.dump(depth+2);
        }
    }
}

