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

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import javax.xml.XMLConstants;

import net.sf.saxon.s9api.QName;

/**
 *
 * @author ndw
 */
public class XProcNamespaceContext {
    Hashtable<String,String> nshash = new Hashtable<String,String> ();
    
    /** Creates a new instance of XProcNamespaceContext */
    public XProcNamespaceContext(Hashtable<String,String> bindings) {
        for (String prefix : bindings.keySet()) {
            nshash.put(prefix,bindings.get(prefix));
        }
    }

    public String getNamespaceURI(String prefix) {
        if (nshash.containsKey(prefix)) {
            return nshash.get(prefix);
        } else if ("xml".equals(prefix)) {
            return XMLConstants.XML_NS_URI;
        } else if ("xmlns".equals(prefix)) {
            return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
        } else {
            return XMLConstants.NULL_NS_URI;
        }
    }

    public String getPrefix(String namespace) {
        for (String key : nshash.keySet()) {
            if (namespace.equals(nshash.get(key))) {
                return key;
            }
        }
        
        if (XMLConstants.XML_NS_URI.equals(namespace)) {
            return "xml";
        } else if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespace)) {
            return "xmlns";
        } else {
            return null;
        }
    }

    public Set<String> getAllPrefixes() {
        return nshash.keySet();
    }

    public Hashtable<String,String> bindings() {
        return nshash;
    }
    
    public Iterator getPrefixes(String namespace) {
        Vector<String> pfxs = new Vector<String> ();
        for (String key : nshash.keySet()) {
            if (namespace.equals(nshash.get(key))) {
                pfxs.add(key);
            }
        }
        return pfxs.iterator();
    }

    public Iterator getPrefixes() {
        return null;
    }

    public void addBinding(String prefix, String namespace) {
        nshash.put(prefix, namespace);
    }

    public void delBinding(String prefix) {
        nshash.remove(prefix);
    }

    public QName parseQName(String name) {
        QName newName = null;
            
        if (name.contains(":")) {
            int pos = name.indexOf(":");
            String prefix = name.substring(0, pos);
            String localName = name.substring(pos+1);
            String namespace = nshash.get(prefix);
            
            if ("xml".equals(prefix)) {
                namespace = XMLConstants.XML_NS_URI;
            }

            if (namespace == null) {
                throw new IllegalArgumentException("No binding for prefix in name: " + name);
            }
            
            newName = new QName(namespace, localName, prefix);
        } else {
            newName = new QName(getNamespaceURI(XMLConstants.DEFAULT_NS_PREFIX), name, "");
        }
        
        return newName;
    }
    
    public void dump() {
        System.err.println("Namespace context: " + this.hashCode());
        for (String prefix : nshash.keySet()) {
            System.err.println("\t" + prefix + "=" + nshash.get(prefix));
        }
    }
    
}
