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
package com.xmlcalabash.functions;

import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.runtime.XCompoundStep;
import com.xmlcalabash.runtime.XStep;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.SequenceType;
import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcRuntime;

/**
 * Implementation of the XSLT system-property() function
 */

public class SystemProperty extends ExtensionFunctionDefinition {
    private static StructuredQName funcname = new StructuredQName("p", XProcConstants.NS_XPROC, "system-property");
    private XProcRuntime runtime = null;

     protected SystemProperty() {
         // you can't call this one
     }

     public SystemProperty(XProcRuntime runtime) {
         this.runtime = runtime;
     }

     public StructuredQName getFunctionQName() {
         return funcname;
     }

     public int getMinimumNumberOfArguments() {
         return 1;
     }

     public int getMaximumNumberOfArguments() {
         return 1;
     }

     public SequenceType[] getArgumentTypes() {
         return new SequenceType[]{SequenceType.SINGLE_STRING};
     }

     public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
         return SequenceType.SINGLE_STRING;
     }

     public ExtensionFunctionCall makeCallExpression() {
         return new SystemPropertyCall();
     }

     private class SystemPropertyCall extends ExtensionFunctionCall {
         private StaticContext staticContext = null;

         public void supplyStaticContext(StaticContext context, int locationId, Expression[] arguments) throws XPathException {
             staticContext = context;
         }

         public SequenceIterator call(SequenceIterator[] arguments, XPathContext context) throws XPathException {
             StructuredQName propertyName = null;

             XStep step = runtime.getXProcData().getStep();
             // FIXME: this can't be the best way to do this...
             // FIXME: And what, exactly, is this even supposed to be doing!?
             if (step != null && !(step instanceof XCompoundStep)) {
                 throw XProcException.dynamicError(23);
             }

             try {
                 SequenceIterator iter = arguments[0];
                 String lexicalQName = iter.next().getStringValue();
                 propertyName = StructuredQName.fromLexicalQName(
                      lexicalQName,
                      false,
                      context.getConfiguration().getNameChecker(),
                      staticContext.getNamespaceResolver());
             } catch (XPathException e) {
                 if (e.getErrorCodeLocalPart()==null || e.getErrorCodeLocalPart().equals("FOCA0002")
                         || e.getErrorCodeLocalPart().equals("FONS0004")) {
                     e.setErrorCode("XTDE1390");
                 }
                 throw e;
             }

             String uri = propertyName.getNamespaceURI();
             String local = propertyName.getLocalName();
             String value = "";

             if (uri.equals(XProcConstants.NS_XPROC)) {
                 if ("episode".equals(local)) {
                     value = runtime.getEpisode();
                 } else if ("language".equals(local)) {
                     value = runtime.getLanguage();
                 } else if ("product-name".equals(local)) {
                     value = runtime.getProductName();
                 } else if ("product-version".equals(local)) {
                     value = runtime.getProductVersion();
                 } else if ("vendor".equals(local)) {
                     value = runtime.getVendor();
                 } else if ("vendor-uri".equals(local)) {
                     value = runtime.getVendorURI();
                 } else if ("version".equals(local)) {
                     value = runtime.getXProcVersion();
                 } else if ("xpath-version".equals(local)) {
                     value = runtime.getXPathVersion();
                 } else if ("psvi-supported".equals(local)) {
                     value = runtime.getPSVISupported() ? "true" : "false";
                 }
             }

             return SingletonIterator.makeIterator(
                     new StringValue(value));
         }
     }
}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Michael H. Kay.
//
// Portions created by Norman Walsh are Copyright (C) Mark Logic Corporation. All Rights Reserved.
//
// Contributor(s): Norman Walsh.
//
