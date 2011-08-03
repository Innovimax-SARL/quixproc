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
package innovimax.quixproc.codex.util.open;

import innovimax.quixproc.codex.util.PipedDocument;
import innovimax.quixproc.codex.util.Waiting;

import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.runtime.XStep;

public class Waiter implements Waiting { 
  
    public Waiter() {}  
   
    public Waiting newInstance(XStep xstep, int channel, ReadablePipe pipe, PipedDocument doc, String message) {  
        Waiting waiter = new Waiter();
        return waiter;
    }           
    
    public void setRuntime(XProcRuntime runtime) { /* NOP */ }        
    public void setExitTimeout(int time) { /* NOP */ }
    public void setWaitTimeout(int time) { /* NOP */ }
    public void check() { /* NOP */ }
    public void check(boolean exit) { /* NOP */ }
    public void check(String info) { /* NOP */ }
    public void check(String info, boolean exit) { /* NOP */ }
          
}
