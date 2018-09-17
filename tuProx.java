import java.net.*;
import java.io.*;

import com.barchart.udt.net.NetSocketUDT;


/*
  Java Transparent Proxy
  Copyright (C) 1999 by Didier Frick (http://www.dfr.ch/)
  This software is provided under the GNU general public license (http://www.gnu.org/copyleft/gpl.html).
*/

public class tuProx extends Thread {

    protected class StreamCopyThread extends Thread {
	private Socket inSock;
	private Socket outSock;
	private boolean done=false;
	private StreamCopyThread peer;

	public StreamCopyThread(Socket inSock, Socket outSock) {
	    this.inSock=inSock;
	    this.outSock=outSock;
	}

	
	
	
	public void run() {
	    byte[] buf=new byte[bufSize];
	    int count=-1;
	    try {
		InputStream in=inSock.getInputStream();
		OutputStream out=outSock.getOutputStream();
		try {
		    while(((count=in.read(buf))>0)&&!isInterrupted()) {
			out.write(buf,0,count);
		    }
		} catch(Exception xc) {
		    if(debug) {
			// FIXME
			// It's very difficult to sort out between "normal"
			// exceptions (occuring when one end closes the connection
			// normally), and "exceptional" exceptions (when something
			// really goes wrong)
			// Therefore we only log exceptions occuring here if the debug flag
			// is true, in order to avoid cluttering up the log.
			err.println(header+":"+xc);
			xc.printStackTrace();
		    }
		} finally {
		    // The input and output streams will be closed when the sockets themselves
		    // are closed.
		    out.flush();
		}
	    } catch(Exception xc) {
		err.println(header+":"+xc);
		xc.printStackTrace();
	    }
	    synchronized(lock) {
		done=true;
		try {
		    if((peer==null)||peer.isDone()) {
			// Cleanup if there is only one peer OR
			// if _both_ peers are done
			inSock.close();
			outSock.close();
		    }
		    else 
			// Signal the peer (if any) that we're done on this side of the connection
			peer.interrupt();
		} catch(Exception xc) {
		    err.println(header+":"+xc);
		    xc.printStackTrace();
		} finally {
		    connections.removeElement(this);
		}
	    }
	}
