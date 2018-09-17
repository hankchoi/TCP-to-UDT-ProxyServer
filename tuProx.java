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

	    

	public boolean isDone() {
	    return done;
	}
    
	public void setPeer(StreamCopyThread peer) {
	    this.peer=peer;
	}

    }

    // Holds all the currently active StreamCopyThreads
    private java.util.Vector connections=new java.util.Vector();
    // Used to synchronize the connection-handling threads with this thread
    private Object lock=new Object();
    // The address to forward connections to
    private InetAddress dstAddr;
    // The port to forward connections to
    private int dstPort;
    // Backlog parameter used when creating the ServerSocket
    protected static final int backLog=100;
    // Timeout waiting for a StreamCopyThread to finish
    public static final int threadTimeout=2000; //ms
    // Linger time
    public static final int lingerTime=180; //seconds (?)
    // Size of receive buffer
    public static final int bufSize=2048;
    // Header to prepend to log messages
    private String header;
    // This proxy's server socket
    private ServerSocket srvSock;
    // Debug flag
    private boolean debug=false;

    // Log streams for output and error messages
    private PrintStream out;
    private PrintStream err;

    
    private static final String 
	argsMessage="Arguments: ( [source_address] source_port dest_address dest_port ) | config_file";
    private static final String 
	propertyPrefix="proxy";


    public tuProx(InetAddress srcAddr,int srcPort,
		       InetAddress dstAddr,int dstPort, PrintStream out, PrintStream err) 
	throws IOException {
	this.out=out;
	this.err=err;
	this.srvSock=(srcAddr==null) ? new ServerSocket(srcPort,backLog) :  
	    new ServerSocket(srcPort,backLog,srcAddr);
	this.dstAddr=dstAddr;
	this.dstPort=dstPort;
	this.header=(srcAddr==null ? "" : srcAddr.toString())+":"+srcPort+" <-> "+dstAddr+":"+dstPort;
	start();
    }

    public void run() {
	out.println(header+" : starting");
	try {
	    while(!isInterrupted()) {
		Socket serverSocket=srvSock.accept();
		try {
		    serverSocket.setSoLinger(true,lingerTime);
		    NetSocketUDT clientSocket=new NetSocketUDT();
		    clientSocket.connect(new InetSocketAddress(dstAddr, dstPort));
		    clientSocket.setSoLinger(true,lingerTime);
		    StreamCopyThread sToC=new StreamCopyThread(serverSocket,clientSocket);
		    StreamCopyThread cToS=new StreamCopyThread(clientSocket,serverSocket);
		    sToC.setPeer(cToS);
		    cToS.setPeer(sToC);
		    synchronized(lock) {
			connections.addElement(cToS);
			connections.addElement(sToC);
			sToC.start();
			cToS.start();
		    }
		} catch(Exception xc) {
		    err.println(header+":"+xc);
		    if(debug)
			xc.printStackTrace();
		}
	    }
	    srvSock.close();
	} catch(IOException xc) {
	    err.println(header+":"+xc);
	    if(debug)
		xc.printStackTrace();
	} finally {
	    cleanup();
	    out.println(header+" : stopped");
	}
    }

	    
	    
	    
