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

package com.xmlcalabash.io;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmNode;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.model.Serialization;
import com.xmlcalabash.model.Step;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Map;  // Innovimax: new import
import java.util.HashMap;  // Innovimax: new import


import innovimax.quixproc.codex.util.PipedDocument;
import innovimax.quixproc.codex.util.StepContext;
import innovimax.quixproc.datamodel.QuixEvent;

public class WritableDocument implements WritablePipe {
    private Serializer serializer = null;
    private int writeCount = 0;
    private String uri = null;
    private URI journal = null;
    private XProcRuntime runtime = null;
    private Serialization serial = null;
    private boolean writeSeqOk = false;
    private Step writer = null;
    private OutputStream ostream = null;

    /** Creates a new instance of ReadableDocument */
    public WritableDocument(XProcRuntime xproc, String uri, Serialization serial) {
        this.runtime = xproc;
        this.uri = uri;

        if (serial == null) {
            this.serial = new Serialization(xproc, null);
            this.serial.setIndent(xproc.getDebug()); // indent stdio by default when debugging
        } else {
            this.serial = serial;
        }
    }

    public WritableDocument(XProcRuntime xproc, String uri, Serialization serial, OutputStream out) {
        this.runtime = xproc;
        this.uri = uri;
        this.ostream = out;

        if (serial == null) {
            this.serial = new Serialization(xproc, null);
            this.serial.setIndent(xproc.getDebug()); // indent stdio by default when debugging
        } else {
            this.serial = serial;
        }

    }

    public void canWriteSequence(boolean sequence) {
        writeSeqOk = sequence;
    }
    
    public int documentsWritten() {
        return writeCount;
    }

    public int documentsRead() {
        return 1;
    }
    
    public void write(XdmNode doc) {
        try {            
            Processor qtproc = runtime.getProcessor();
            DocumentBuilder builder = qtproc.newDocumentBuilder();
            builder.setBaseURI(new URI("http://example.com/"));
            XQueryCompiler xqcomp = qtproc.newXQueryCompiler();
            XQueryExecutable xqexec = xqcomp.compile(".");
            XQueryEvaluator xqeval = xqexec.load();
            xqeval.setContextItem(doc);

            serializer = new Serializer();            
            serializer.setOutputProperty(Serializer.Property.BYTE_ORDER_MARK, serial.getByteOrderMark() ? "yes" : "no");
            // FIXME: support CDATA_SECTION_ELEMENTS
            if (serial.getDoctypePublic() != null) {
                serializer.setOutputProperty(Serializer.Property.DOCTYPE_PUBLIC, serial.getDoctypePublic());
            }
            if (serial.getDoctypeSystem() != null) {
                serializer.setOutputProperty(Serializer.Property.DOCTYPE_SYSTEM, serial.getDoctypeSystem());
            }
            if (serial.getEncoding() != null) {
                serializer.setOutputProperty(Serializer.Property.ENCODING, serial.getEncoding());
            }
            serializer.setOutputProperty(Serializer.Property.ESCAPE_URI_ATTRIBUTES, serial.getEscapeURIAttributes() ? "yes" : "no");
            serializer.setOutputProperty(Serializer.Property.INCLUDE_CONTENT_TYPE, serial.getIncludeContentType() ? "yes" : "no");
            serializer.setOutputProperty(Serializer.Property.INDENT, serial.getIndent() ? "yes" : "no");
            if (serial.getMediaType() != null) {
                serializer.setOutputProperty(Serializer.Property.MEDIA_TYPE, serial.getMediaType());
            }
            if (serial.getMethod() != null) {
                serializer.setOutputProperty(Serializer.Property.METHOD, serial.getMethod().getLocalName());
            }
            if (serial.getNormalizationForm() != null) {
                serializer.setOutputProperty(Serializer.Property.NORMALIZATION_FORM, serial.getNormalizationForm());
            }
            serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, serial.getOmitXMLDeclaration() ? "yes" : "no");
            if (serial.getStandalone() != null) {
                String standalone = serial.getStandalone();
                if ("true".equals(standalone)) {
                    serializer.setOutputProperty(Serializer.Property.STANDALONE, "yes");
                } else if ("false".equals(standalone)) {
                    serializer.setOutputProperty(Serializer.Property.STANDALONE, "no");
                }
                // What about omit?
            }

            serializer.setOutputProperty(Serializer.Property.UNDECLARE_PREFIXES, serial.getUndeclarePrefixes() ? "yes" : "no");
            if (serial.getVersion() != null) {
                serializer.setOutputProperty(Serializer.Property.VERSION, serial.getVersion());
            }
            
            if (ostream != null) {                
                serializer.setOutputStream(ostream);
            } else if (uri == null) {                
                serializer.setOutputStream(System.out);
            } else {                
                try {                    
                    // Attempt to handle both the case where we're writing to a URI scheme that
                    // supports writing (like FTP?) and the case where we're writing to a file
                    // (which apparently *isn't* a scheme we can write to, WTF?)
                    URI ouri = new URI(uri);

                    if ("file".equals(ouri.getScheme())) {                        
                        runtime.finest(null, null, "Attempt to write file: " + uri);                        
                        File file = new File(ouri.toURL().getFile());
                        serializer.setOutputFile(file);                        
                    } else {                        
                        runtime.finest(null, null, "Attempt to write: " + uri);
                        URL url = new URL(uri);
                        final URLConnection conn = url.openConnection();                        
                        conn.setDoOutput(true);
                        ostream = conn.getOutputStream();                        
                        serializer.setOutputStream(ostream);                        
                    }
                } catch (IOException ex) {                    
                    runtime.error(ex);
                } catch (URISyntaxException use) {                    
                    runtime.error(use);
                }
            }            
            xqeval.setDestination(serializer);
            xqeval.run();

            if (ostream != null) {
                try {
                    ostream.close();
                } catch (IOException ex) {
                    throw new XProcException(ex);
                }
            }
            
            if (uri == null && runtime.getDebug()) {
                System.out.println("\n--<document boundary>--------------------------------------------------------------------------");
            }
        } catch (URISyntaxException use) {
            use.printStackTrace();
            throw new XProcException(use);
        } catch (SaxonApiException sae) {
            sae.printStackTrace();
            throw new XProcException(sae);
        }

        if (writer != null) {
            runtime.finest(null, writer.getNode(), writer.getName() + " wrote '" + (doc == null ? "null" : doc.getBaseURI()));
        }
    }    
    
    //*************************************************************************
    //*************************************************************************        
    //*************************************************************************
    // INNOVIMAX IMPLEMENTATION
    //*************************************************************************
    //*************************************************************************
    //************************************************************************* 

    private Map<String, Step> writers = new HashMap<String, Step>();  // Innovimax: new property       
    
    // Innovimax: new function
    public void resetWriter(StepContext stepContext) {
        throw new UnsupportedOperationException("You can't resetWriter a WritableDocument");
    }       

    // Innovimax: new function
    public void close(StepContext stepContext) {
        // nop;
    }
    
    // Innovimax: new function
    public Step getWriter(StepContext stepContext) {
        return writers.get(Integer.toString(stepContext.curChannel));
    }        
    
    // Innovimax: new function
    public void setWriter(StepContext stepContext, Step step) {
        writers.put(Integer.toString(stepContext.curChannel),step);
    }      

    // Innovimax: new function
    public void write(StepContext stepContext, XdmNode doc) {                 
        runtime.getTracer().debug(null,stepContext,-1,this,null,"    DOCU > WRITE ");        
        write(doc);
    }  
    
    // Innovimax: new function
    public void addChannel(int channel) {      
        // nop
    }

    @Override
    public PipedDocument newPipedDocument(int channel) {
      PipedDocument document = new PipedDocument(runtime, 1);
      runtime.getTracer().debug(null,null,channel,this,document,"    DOCU > WRITE ");
      return document;
    }    
    
    //*************************************************************************
    //*************************************************************************        
    //*************************************************************************
    // INNOVIMAX DEPRECATION
    //*************************************************************************
    //*************************************************************************
    //*************************************************************************
/*
    public void resetWriter() {
        throw new UnsupportedOperationException("You can't resetWriter a WritableDocument");
    }

    public void close() {
        // nop;
    }

    public void setWriter(Step step) {
        writer = step;
    }    
*/
}
