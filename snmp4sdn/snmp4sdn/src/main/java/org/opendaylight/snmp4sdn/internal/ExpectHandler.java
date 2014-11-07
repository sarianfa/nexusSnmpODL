/*
 * Copyright (c) 2013 Industrial Technology Research Institute of Taiwan and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.snmp4sdn.internal;//s4s

//Unit tests with expect4j enabled are successful, but currently loading expect4j in OSGi has problem,
//so here use a dummy (ExpectDummy, at bottom) to replace expec4j.
//The lines marked with "//e4j" and "//e4j-dummy" are for switching to use expect4j or the dummy
/*
import org.apache.commons.net.telnet.TelnetClient;
import org.apache.commons.net.telnet.TelnetNotificationHandler;
import org.apache.commons.net.telnet.SimpleOptionHandler;
import org.apache.commons.net.telnet.EchoOptionHandler;
import org.apache.commons.net.telnet.TerminalTypeOptionHandler;
import org.apache.commons.net.telnet.SuppressGAOptionHandler;
import org.apache.commons.net.telnet.InvalidTelnetOptionException;
*/
//import org.apache.commons.net.telnet.*;
import org.apache.commons.net.io.*;
import org.apache.commons.net.telnet.*;
//import telnet.*;
import org.expect4j.*;//e4j
//import expect4j.*;
//import expect4j.Expect4j;
//import jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.Socket;
import java.net.InetAddress;

public class ExpectHandler{

    private static Logger logger = LoggerFactory.getLogger(ExpectHandler.class);
    Socket socket;
    static TelnetClient client = null;
    StringBuffer buffer = new StringBuffer("");
    Expect4j expect;//e4j
    //ExpectDummy expect;//e4j-dummy
    long expectTimeout = 2 * 1000; //set Expect4j's default timeout as 2 seconds
    InputStream in = null;
    PrintStream out = null;
    String sw_ipAddr, username_prompt, password_prompt, username, password, prompt = "#";
    Reader reader = null;
    Writer writer = null;
//    int showvlan = 1;  
    boolean isDummy = false;//for testing without real Ethernet switch: when isDummy is true, ExpectHandler always answer 'sucessful' for any request
    //boolean firstOrder = false;
    boolean cmdExecuted = false;
    public ExpectHandler(String sw_ipAddr, String username_prompt, String password_prompt, 
                                            String username, String password) throws Exception{
        if(isDummy)return;
        this.sw_ipAddr = new String(sw_ipAddr);
        this.username_prompt = new String(username_prompt);
        this.password_prompt = new String(password_prompt);
        this.username = new String(username);
        this.password = new String(password);
        System.out.println("NexusCLIHandler() sw_ipaddr:" + sw_ipAddr);
        client = new TelnetClient();
        //client.connect(sw_ipAddr);
        //read();
        //write(username.getBytes());
        //client.disconnect();
        TerminalTypeOptionHandler ttopt = new TerminalTypeOptionHandler("VT100", false, false, true, true);
        EchoOptionHandler echoopt = new EchoOptionHandler(true, false, true, false);
        SuppressGAOptionHandler gaopt = new SuppressGAOptionHandler(false, false, false, false);
        client.addOptionHandler( ttopt );
        client.addOptionHandler( echoopt );
        client.addOptionHandler( gaopt );

        client.connect(sw_ipAddr, 23);
        //System.out.println("after login"); 
        //writer = new OutputStreamWriter(os); 
         out = new PrintStream(client.getOutputStream());
         telnetLogin();
         InputStream is =  new FromNetASCIIInputStream( client.getInputStream() ); // null until client connected
        //OutputStream os = new ToNetASCIIOutputStream( client.getOutputStream() );
        reader = new InputStreamReader(is);
        
         //executeNexus("show vlan"); 
         //sendCommand("show vlan");
         //disconnect();
    }
    
    public String executeNexus(String cmd){
        int length;
        boolean foundEOF = false;
        cmdExecuted = false;
        char cs[] = new char[256];     
        loginwrite(cmd);  
        String result = null;
        while (!foundEOF && !cmdExecuted) {
            try {
                //loginwrite(cmd);
                length = reader.read(cs); 
            } catch (IOException ioe) {
                
                 System.out.println("BlockingConsumer Exited from reader");
               foundEOF = true;
               break;
                                                                                             }
             if (length == -1) { 
                foundEOF = true;
                break;
            }
            synchronized(this) {
            String print = new String(cs, 0, length);
                    print = print.replaceAll("\n", "\\\\n");
                    print = print.replaceAll("\r", "\\\\r");
                    logger.debug("Adding to buffer: " + print);

                    StringBuffer sb = new StringBuffer();
                    for (int i = 0; i < length; i++)
                        sb.append("," + ((int) cs[i]) );
                    logger.trace("Codes: " + sb.toString());

                buffer.append(cs, 0, length); 
                result = mywakeup(buffer,cmd);
            } // end synchronized(this)
        } // end while loop 
       return result;
   }
    public String pause() {
        // TODO mark offset, so that it can be trimmed by resume coming in later
                 String currentBuffer;
                        currentBuffer = buffer.toString();
                                 return currentBuffer;
                                     }
    public void resume(int offset) {
        if (offset < 0)
            return;

        synchronized(this) {
            logger.trace("BlockingConsumer " + this + " moving buffer up by " + offset);
            StringBuffer smaller = buffer.delete(0, offset); // + 1
        }
    }
   public String mywakeup(StringBuffer buf, String cmd) {
        // TODO mark offset, so that it can be trimmed by resume coming in later        
        pause();
        String username = "admin";
        String password = "vam.1234";
        String ret = "";
        int len = buf.length(); 
        //System.out.println("READ NEW STUFF");
        //System.out.println(buf.toString());
        if(buf.toString().endsWith("login:") 
          || buf.toString().endsWith("login: ")){
               //System.out.println("in login part");
               //writer.write(username);
              // writer.flush();
              loginwrite(username);
              resume(len - 1); 
        }else
        if(buf.toString().endsWith("password:")
          || buf.toString().endsWith(" Password: ")){
               //System.out.println("in login password part");
               //writer.write(username);
               //              // writer.flush();
              loginwrite(password);
              resume(len - 1); 
             }
        else if(buf.toString().endsWith(prompt + " ")){
                //System.out.println("Going to analysis in case of "+ prompt + " ");
                //System.out.println(buf.toString());
                 /*
                 if(!firstOrder)
                   {
                    System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&");
                    loginwrite(cmd);
                    firstOrder = true;
                   } 
                   */
                cmdExecuted = true;
                ret= buf.toString();
                resume(len - 1); 
        }
        else if(buf.toString().endsWith("--More-- ") ||
          buf.toString().endsWith(" ") ||
          buf.toString().endsWith("--More--")) {
          //System.out.println("MMMMMMMMMMMMMMMMM");
          //System.out.println(buf.toString());
          loginwrite(" ");
          resume(-1);
        }
        else {
          //loginwrite(" ");
          
          resume(-1); 
        } 
       //resume(len - 1);
       return ret;
       }
   public void analysis(String patterns){
          //System.out.println("%%%%%%%%%%%%%%%%%%%%%%%");
          System.out.println(patterns);
          System.out.println("%%%%%%%%%%%%%%%%%%%%%%%");
   }
   public void telnetLogin() {
        //InputStream in = null;
        //PrintStream out = null;
        String username = "admin";
        String password = "vam.1234"; 
      try {
              System.out.println("in telnet login");
    		// Connect to the specified server
    		//     		telnet.connect(server, 23);
    		//
    		//     		    		// Get input and output stream references
    	      in = client.getInputStream();	
             // out = new PrintStream(client.getOutputStream());
    		//
    		//     		    		    		    		    		// Log the user on
     	       readUntil("login: ");
    		     		    		    		    		    	       loginwrite(username);
    	       readUntil(" Password: ");
    		     		    		    		    		     		loginwrite(password);
    		//
    		//     		    		    		    		    		    		    		    		    		    		// Advance to a prompt
         	readUntil(prompt + " ");
                System.out.println("After successful login");
    		     		    		    		    		    		    		    		    		    		    		    		    	} catch (Exception e) {    		    		    		    		    		    		    		    	 		e.printStackTrace();
    			    		    	    		    	} 
   }
 public String sendCommand(String command) {
       System.out.println("in send command");
    	try {
    		loginwrite(command);
    		return readUntil(prompt + " ");
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	return null;
    }

    public void disconnect() {
    	try {
    		client.disconnect();
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }

public String readUntil(String pattern) {
    	try {
    		char lastChar = pattern.charAt(pattern.length() - 1);
    		StringBuffer sb = new StringBuffer();
    		boolean found = false;
    		char ch = (char) in.read();
    		while (true) {
    			//System.out.print(ch);
    			sb.append(ch);
    			if (ch == lastChar) {
    				if (sb.toString().endsWith(pattern)) {
                                        System.out.println(sb.toString());
    					return sb.toString();
    				}
                               /* else if (sb.toString().endsWith(" ")){
                                     //System.out.println(sb.toString());
                                     System.out.println("last charachter not what we expect");
                                     loginwrite(" ");
                                     //return sb.toString();
                                }*/
    			}
                  ch = (char) in.read();
    		}
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	return null;
    }

    public void loginwrite(String value) {
    	try {
    		out.println(value);
    		out.flush();
    	//	System.out.println(value);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    private static void write(byte[] data) throws IOException {
    client.getOutputStream().write(data);
    client.getOutputStream().flush();
}

    private static void read() throws IOException {
    System.out.println("Read");
    byte[] buff = new byte[1024];
    String tmp = null;
    String username= "admin";
    String password= "vam.1234";  
    int readCount;
    if((readCount = client.getInputStream().read(buff)) > 0) {
        tmp = new String(buff, 0, readCount);
        
        if(tmp.endsWith("login: "))
          {
           System.out.println("@@@@@in if login@@@@");
           System.out.println(tmp);
           System.out.println("#############################"); 
           write(username.getBytes());
           read();
          }else if(tmp.endsWith("password: "))
          {
           System.out.println("@@@@@in if password@@@@");
           System.out.println(tmp);
           System.out.println("#############################");
           write(password.getBytes());
           read();
          }
         else{ 
           System.out.println("@@@@in else@@@@@");
           System.out.println(tmp);
           System.out.println("#############################");
           } 
          
    }
    System.out.println("read="+readCount);
}

    private boolean loginCLI() throws Exception{
        System.out.println("in loginCLI");
        if(socket.isClosed()){
            socket = new Socket(sw_ipAddr, 23);
            if(socket.isClosed()){
                logger.trace("(telnet to switch " + sw_ipAddr + " fail)");
                return false;
            }
            else
                logger.trace("(telnet to switch " + sw_ipAddr + " successfully)");
        }
         System.out.println("Assigning socket to expect");
         //expect= ExpectUtils.telnet(sw_ipAddr, 23);
        expect = new Expect4j(socket);//e4j
        //expect = new ExpectDummy(socket);//e4j-dummy
        //if(expect.getClass().getName().equals(
        //            "org.opendaylight.snmp4sdn.internal.util.ExpectHandler$ExpectDummy"))
         //   isDummy = true;//e4j-dummy
        System.out.println("assigned socket to expect");
        expect.setDefaultTimeout(expectTimeout);
        System.out.println("expecting--" + username_prompt);
        logger.trace("expecting--" + username_prompt);
        expect.expect(username_prompt);
        logger.trace("1:" + expect.getLastState().getBuffer());
        System.out.println("1:" + expect.getLastState().getBuffer());  
        expect.send(username + "\r\n");
        logger.trace("expecting--" + password_prompt);
        System.out.println("expecting--" + password_prompt);
        expect.expect(password_prompt);//d-link:PassWord  Accton:"Password "
        logger.trace("2:" + expect.getLastState().getBuffer());
        System.out.println("2:" + expect.getLastState().getBuffer());
        expect.send(password + "\r\n");
        logger.trace("login().expecting--#");
        expect.expect("#");
        if(expect.getLastState().getBuffer().endsWith("#")){
            logger.trace("(login successfully to " + sw_ipAddr +")");
            System.out.println("(login successfully to " + sw_ipAddr +")");
            return true;//login successfully
        }
        return false;//login fail
    }

    //for executing CLI command
    //  sendStr: the command string
    //  expStr: expect string (usually the CLI console prompt)
    //  findStr: check if findStr is in the content shown after sendStr is executed
    public boolean execute(String sendStr, String expStr, String findStr) throws Exception{
        if(isDummy)return true;

        if(sendAndExpect(sendStr, expStr) < 0)
            return false;//TODO: retry if fail?
        return isStringinResult(findStr);
    }

    //for executing CLI command that needs 2 steps
    public boolean execute_2step_end(String sendStr, String expStr, String sendStr2) throws Exception{
        if(isDummy)return true;

        if(sendAndExpect(sendStr, expStr) < 0)
            return false;
        else
            return sendAndEnd(sendStr2);
    }

    public int sendAndExpect(String sendStr) throws Exception{
        if(isDummy)return 1;
                
        return sendAndExpect(sendStr, prompt);
    }

    public int sendAndExpect(String sendStr, String expStr) throws Exception{
        if(isDummy)return 1;
        
        int index = -1;
        if(socket.isClosed()){
            logger.trace("not logged in, try login again");
            loginCLI();
        }
        expect.send(sendStr + "\r\n");
        index = expect.expect(expStr);
        return index;
    }

    public boolean sendAndEnd(String sendStr) throws Exception{
        if(isDummy)return true;
        
        if(socket.isClosed()){
            logger.trace("not logged in, try login again");
            loginCLI();
        }
        expect.send(sendStr + "\r\n");
        return true;
    }

    public boolean isStringinResult(String str) throws Exception{
        if(isDummy)return true;

        if(expect.getLastState().getBuffer().indexOf(str) >= 0)
            return true;
        else
            return false;
    }

    public String getBuffer() throws Exception{
        if(isDummy)return null;
        
        return expect.getLastState().getBuffer();
    }

    private class ExpectDummy{
        public ExpectDummy(Socket socket){}
        public boolean setDefaultTimeout(Long time){return true;}
        public int expect(String str){return 0;}
        public void send(String str){}
        public ExpectDummy getLastState(){return new ExpectDummy(null);}
        public String getBuffer(){return "";}
    }
}
