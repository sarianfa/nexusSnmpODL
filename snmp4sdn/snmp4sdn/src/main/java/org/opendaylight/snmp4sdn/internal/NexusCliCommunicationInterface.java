/*
 * TELNET Package
 *
 * Copyright (C) 2004, Jonathan Sevy <jsevy@mcs.drexel.edu>
 *
 * This is free software. Redistribution and use in source and binary forms, with
 * or without modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.opendaylight.snmp4sdn.internal;
import org.opendaylight.snmp4sdn.internal.ExpectHandler;//s4
import java.io.IOException;
import java.math.BigInteger;
import java.net.*;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Vector;
import java.util.StringTokenizer; 
import java.util.ArrayList;
public class NexusCliCommunicationInterface
{
    public static final int TELNET_PORT = 23;

    // port to which requests will be sent
    private int remotePort = TELNET_PORT;

    // largest size for datagram packet payload; based on
    // RFC 1157, need to handle messages of at least 484 bytes
    private int receiveBufferSize = 512;

   
    private InetAddress hostAddress;
 
    public Socket dSocket;

    public int requestID = 1;

    ExpectHandler expect; 
    String sw_ipAddr, username, password, prompt = "#";

    /**
    *    Construct a new communication object to communicate with the specified host using the
    *    given community name. The version setting should be either 0 (version 1) or 1 (version 2,
    *    a la RFC 1157).
    */

    public NexusCliCommunicationInterface(String sw_ipAddr, String username, String password)
       // throws SocketException
        throws Exception
    {
        //this(hostAddress, TELNET_PORT);
        this.sw_ipAddr = new String(sw_ipAddr);
        this.username = new String(username);
        this.password = new String(password);
        try{
            //it does not really work because expect4j does not work
            expect = new ExpectHandler("10.82.105.60", "login:", "password:", username, password);//d-link:UserName,PassWord  Accton:Username, Passwrod
        }catch(Exception e){
            System.out.println("NexusCLIHandler() err:" + e);
        }

    }



    /**
     *    Construct a new communication object to communicate with the specified host using the
     *    given community name, and sending requests to the specified port. The version setting
     *    should be either 0 (version 1) or 1 (version 2, a la RFC 1157).
     */

     public NexusCliCommunicationInterface( InetAddress hostAddress, int remotePort)
        // throws SocketException
        throws Exception
     {
         this.remotePort = remotePort;
         
         this.hostAddress = hostAddress;
         
          
        dSocket = new Socket(hostAddress, remotePort);
         dSocket.setSoTimeout(15000);    //15 seconds
         
     }




    /**
    *    Permits setting timeout value for underlying datagram socket (in milliseconds).
    */

    public void setSocketTimeout(int socketTimeout)
       // throws SocketException
       throws Exception
    {
        dSocket.setSoTimeout(socketTimeout);
    }
    public void setVLANPortstoSwitch(Long vlanID, String[] ports){
         //interface ethernet1/5
         //switchport
         //switchport mode access
         //switchport access vlan 5
         //no shutdown
          expect.executeNexus("configure terminal");
          for (String port : ports){
          
          expect.executeNexus("interface " + port);
          expect.executeNexus("switchport");
          expect.executeNexus("switchport mode access");
          expect.executeNexus("switchport access vlan "+ Long.toString(vlanID)); 
          expect.executeNexus("no shutdown");
          }
          expect.executeNexus("show vlan");
          expect.executeNexus("exit"); 
    }
    public void addVlan(Long vlanID, String vlanName){
         //sendCommand("show vlan");
         
    try{
          expect.executeNexus("configure terminal");
          expect.executeNexus("vlan " + vlanID);
          expect.executeNexus("name "+ vlanName);
          expect.executeNexus("state active");
          expect.executeNexus("no shutdown");
          expect.executeNexus("exit");
          expect.disconnect();
        }
          catch(Exception e){
            System.out.println("NexusCliCommunicationInterface.addvlan err: {}"+ e);
        }
    }  
    public ArrayList<String> getPorts()
          throws Exception
        {
           ArrayList<String> arrLis = null;
           System.out.println("In getPorts in NexusCLIcomminterface "); 
          try{
           expect.executeNexus("configure terminal");
           String portResults =   expect.executeNexus("show interface brief");
           expect.executeNexus("exit");
           expect.disconnect(); 
           arrLis = extractInfo(portResults,7,0);
}
            
catch(Exception e){
System.out.println("NexusCliCommunicationInterface.addvlan err: {}"+ e);

}
          return arrLis;
          }

    public ArrayList<ArrayList<String>> getVlanandPorts()
          throws Exception
        {
           ArrayList<ArrayList<String>> ret = null;
           ArrayList<String> arrLis1 = null;
           ArrayList<String> arrLis2 = null;
           System.out.println("In getPorts in NexusCLIcomminterface ");
          try{
           expect.executeNexus("configure terminal");
           String portResults =   expect.executeNexus("show interface brief");
           expect.executeNexus("exit");
           expect.disconnect();
            //untill line 7 is useless info
           //index 0 of this outcome should be port, index 1 should be vlan
           arrLis1 = extractInfo(portResults, 7, 0);
           arrLis2 = extractInfo(portResults, 7, 1); 
           //returning vlan is left
           ret.add(arrLis1);
           ret.add(arrLis2);

}

catch(Exception e){
System.out.println("NexusCliCommunicationInterface.addvlan err: {}"+ e);

}
          return ret;
          }
   private ArrayList<String> extractInfo(String portResults, int start, int index) 
         throws Exception
 {
       
           StringTokenizer strTkn = new StringTokenizer(portResults, "\n");
           ArrayList<String> arrLis = new ArrayList<String>(strTkn.countTokens()- 7);
          
       try{  
       int tokenCount = 0;
   while(strTkn.hasMoreTokens()){
     if(tokenCount < start){
       strTkn.nextToken();
       tokenCount++;
     }else{
      String line = strTkn.nextToken();
      String delims = "[ ]+";
      String[] strTknT = line.split(delims); 
      //StringTokenizer strTknT = new StringTokenizer(line, " "); 
      if(line.contains("------") || line.contains("#") ){
      }else{
          arrLis.add(strTknT[index]);
          }
       }
   }
   
           System.out.println("###################################### ");
           //System.out.println(strTkn.nextToken());
           //StringTokenizer strTknT = new StringTokenizer(strTknT, "\t");
           for(int i =0 ; i < arrLis.size();i++){
               System.out.println(arrLis.get(i));
           }
           System.out.println("######################################"); 
           }
          catch(Exception e){
            System.out.println("NexusCLICommunicationInterface.getPorts err: {}"+ e);
        }
        return arrLis;
        }
                               
    public void deleteVLANFromSwitch(Long vlanID){
         try{
          expect.executeNexus("configure terminal");
          expect.executeNexus("no vlan " + vlanID);
          expect.executeNexus("exit");
          expect.disconnect();  
          }
          catch(Exception e){
            System.out.println("NexusCLICommunicationInterface.deleteVLANFromSwitch err: {}"+ e);
        } 
    }
    /**
    *    Close the "connection" with the device.
    */

    public void closeConnection()
       // throws SocketException
       throws Exception
    {
        dSocket.close();
    }




}




