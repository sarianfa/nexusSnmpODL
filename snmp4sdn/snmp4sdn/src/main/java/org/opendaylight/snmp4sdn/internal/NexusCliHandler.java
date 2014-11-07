/*
 * Copyright (c) 2013 Industrial Technology Research Institute of Taiwan and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.snmp4sdn.internal;

import org.snmpj.*;

import org.opendaylight.controller.sal.action.Action;
import org.opendaylight.controller.sal.action.ActionType;
import org.opendaylight.controller.sal.action.Output;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchField;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.core.ConstructionException;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.reader.FlowOnNode;
import org.opendaylight.controller.sal.utils.NodeConnectorCreator;
import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.sal.utils.StatusCode;

import org.opendaylight.snmp4sdn.protocol.util.HexString;
import org.opendaylight.snmp4sdn.protocol.SNMPFlowMod;
import org.opendaylight.snmp4sdn.VLANTable;
import org.opendaylight.snmp4sdn.internal.util.CmethUtil;
import org.opendaylight.snmp4sdn.internal.NexusCliCommunicationInterface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.io.*;

public class NexusCliHandler{
    private static final Logger logger = LoggerFactory
            .getLogger(NexusCliHandler.class);

    String portGetOID = "1.3.6.1.2.1.17.7.1.2.2.1.2";//s4s: MAC_PORT_GET's OID
    String typeGetOID = "1.3.6.1.2.1.17.7.1.2.2.1.3";//s4s: MAC_TYPE_GET's OID
    String portSetOID = "1.3.6.1.2.1.17.7.1.3.1.1.3";//s4s: MAC_PORT_SET's OID
    String typeSetOID = "1.3.6.1.2.1.17.7.1.3.1.1.4";//s4s: MAC_TYPE_SET's OID

    String lldpRemoteChassisIdOID = "1.0.8802.1.1.2.1.4.1.1.5";//s4s
    String lldpLocalChassisIdOID = "1.0.8802.1.1.2.1.3.2.0";//s4s
    String lldpLocalPortIdOID = "1.0.8802.1.1.2.1.3.7.1.3";//s4s
    String lldpRemotePortIdOID = "1.0.8802.1.1.2.1.4.1.1.7";//s4s

    String vlanNameOID = "1.3.6.1.2.1.17.7.1.4.3.1.1";
    //String vlanEgressPortsOID = "1.3.6.1.2.1.17.7.1.4.3.1.2";
    //this is the cisco specific mib id
    String vlanEgressPortsOID = "1.3.6.1.4.1.9.9.68.1.2.2.1.2";//first oen would be port, second one would be vlan
    String vlanForbiddenEgressPortsOID = "1.3.6.1.2.1.17.7.1.4.3.1.3";
    String vlanUntaggedPortsOID = "1.3.6.1.2.1.17.7.1.4.3.1.4";
    String vlanRowStatusOID = "1.3.6.1.2.1.17.7.1.4.3.1.5";
    

    //String requestTypeOIDs = {"1.1.1.1.1"};//(when all kinds of OID more and more, may consider to use requestTypeOIDs[typeID] instead of rasing one by one like above

    CmethUtil cmethUtil = null;

    public NexusCliHandler(CmethUtil cmethUtil){
        this.cmethUtil = cmethUtil;
    }

    private NexusCliCommunicationInterface createNexusCliCommInterface( InetAddress hostAddress){
        try{

            // create a communications interface to a remote SNMP-capable device;
            // need to provide the remote host's InetAddress and the community
            // name for the device; in addition, need to  supply the version number
            // for the SNMP messages to be sent (the value 0 corresponding to SNMP
            // version 1)
            /*InetAddress hostAddress = InetAddress.getByName("10.0.1.1");
            String community = "public";
            int version = 0;    // SNMPv1*/

          NexusCliCommunicationInterface comInterface = new NexusCliCommunicationInterface( hostAddress.toString(), "admin","vam.1234");
          return comInterface;

        }
        catch(Exception e)
        {
            logger.error("In createNexusCliCommInterface(), Exception during SNMP createNexusCliCommInterface to switch {}: {}", hostAddress, e);
        }

        return null;
    }

    //s4s
    /*
        for example:
        0x00400000 ( port 10 )
        |00|40|00|00|
        | 00000000 | 01000000 | 00000000 | 00000000 |
    */

    //s4s
    String convertToEthSwitchPortString(int port){
        //String ans = "0x";//for linux snmpset command parameter form
        String ans = "";
        String sep = ":";//see what seperate character is assigned, eg. ":" or " " or ""  (i.e.  colon or blank or connected)
        int pow;
        String str = "";

        if(port <= 8){
            pow = 8 - port;
            str = new Integer((int)Math.pow(2, pow)).toString();
            ans = ans + str + "0"  + sep + "00";
        }
        else if(port <= 16){
            pow = 16 - port;
            str = new Integer((int)Math.pow(2, pow)).toString();
            ans = ans + "0" + str + sep + "00";
        }
        else if(port <= 24){
            pow = 24 - port;
            str = new Integer((int)Math.pow(2, pow)).toString();
            ans = ans + "00" + sep + str + "0";
        }
        else if(port <= 32){
            pow = 32 - port;
            str = new Integer((int)Math.pow(2, pow)).toString();
            ans = ans + "00" + sep + "0" + str;
        }
        else{
            logger.error("convertToEthSwitchPortString() is given port > 32!");
            return null;
        }

        return ans;
    }

    //s4s
    String convertToEthSwitchPortString_4bits_as_a_character(int port){
        //String ans = "0x";//for linux snmpset command parameter form
        String ans = "";
        String sep = ":";//see what seperate character is assigned, eg. ":" or " " or ""  (i.e.  colon or blank or connected)
        int pow;
        String str = "";

        if(port <= 4){
            pow = 4 - port;
            str = new Integer((int)Math.pow(2, pow)).toString();
            ans = ans + str + "0" + sep + "00" + sep + "00" + sep + "00";
        }
        else if(port <= 8){
            pow = 8 - port;
            str = new Integer((int)Math.pow(2, pow)).toString();
            ans = ans + "0" + str + "00" + sep + "00" + sep + "00";
        }
        else if(port <= 12){
            pow = 12 - port;
            str = new Integer((int)Math.pow(2, pow)).toString();
            ans = ans + "00" + str + "0" + sep + "00" + sep + "00";
        }
        else if(port <= 16){
            pow = 16 - port;
            str = new Integer((int)Math.pow(2, pow)).toString();
            ans = ans + "000" + str + "00" + sep + "00";
        }
        else if(port <= 20){
            pow = 20 - port;
            str = new Integer((int)Math.pow(2, pow)).toString();
            ans = ans + "0000" + str + "0" + sep + "00";
        }
        else if(port <= 24){
            pow = 24 - port;
            str = new Integer((int)Math.pow(2, pow)).toString();
            ans = ans + "00" + sep + "00" + sep + "0" + str + "00";
        }
        else if(port <= 28){
            pow = 28 - port;
            str = new Integer((int)Math.pow(2, pow)).toString();
            ans = ans + "00" + sep + "00" + sep + "00" + str + "0";
        }
        else if(port <= 32){
            pow = 32 - port;
            str = new Integer((int)Math.pow(2, pow)).toString();
            ans = ans + "00" + sep + "00" + sep + "00" + sep + "0" + str;
        }
        else{
            logger.error("convertToEthSwitchPortString() is given port > 32!");
            return null;
        }

        return ans;
    }

    //s4s
    private String macAddrToOID(String macAddr){
        String macOID = "";
        for(int i = 0; i < 17; ){
            macOID += ".";
            macOID  += new Integer(Integer.parseInt(macAddr.substring(i, i + 2), 16)).toString();
            i += 3;
        }
        return macOID.substring(1, macOID.length());
    }

    //s4s
    private byte[] OIDToMacAddrBytes(String oid){
        byte[] result = new byte[6];
        int loc1 = 0, loc2 = 0;
        int count = 0;
        while(loc1 < oid.length()){
            if(count > 5){
                logger.error("fwd table has mac addr longer than 6 bytes");
                return null;
            }
            loc2 = oid.indexOf(".", loc1 + 1);
            if(loc2 < 0)
                loc2 = oid.length();
            String subaddr = oid.substring(loc1, loc2);
            byte subaddrByte = (byte)(Integer.parseInt(subaddr));
            result[count++] = subaddrByte;
            loc1 = loc2 + 1;
        }
        return result;
    }

    //s4s
    private boolean setFwdTableEntry(NexusCliCommunicationInterface comInterface, String destMac, short vlan, int port, int type){
        logger.debug("enter NexusCliHandler.setFwdTableEntry()...");
        try{
               // SNMPVarBindList newVars = comInterface.setMIBEntry(typeOid, typeInt);
                //SNMPVarBindList newVars = comInterface.setMIBEntry(oids, newValues); //comInterface.setMIBEntry() can either input array or variable, like here or below


            return true;
        }
        catch(Exception e)
        {
            logger.error("In setFwdTableEntry(), Exception during SNMP setMIBEntry: {}", e);
            return false;
        }
   }

    //s4s
    /*private String getIpAddr(Long macAddr){
        //look up table...
        return "10.216.0.31";
    }*///move to CmethUtil

    public Status sendBySNMP(Flow flow, int modType, Long sw_macAddr){
        logger.debug("enter NexusCliHandler.sendBySNMP()");

        logger.debug("retrieving the metrics in the Flow...");
        //retrieve from the flow: (1)src mac (2)dest mac (3)the port value, to write into fwd table
            //to retrieve (1)&(2)
        Match match = flow.getMatch();
        MatchField fieldDlDest= match.getField(MatchType.DL_DST);
        String destMac = HexString.toHexString((byte[])fieldDlDest.getValue());
        MatchField fieldVlan = match.getField(MatchType.DL_VLAN);
        short vlan = ((Short)(fieldVlan.getValue())).shortValue();
            //to retrieve (3)
        Action action = flow.getActions().get(0);
        if(flow.getActions().size() > 1) {
            logger.error("flow.getActions() > 1");
            return new Status(StatusCode.NOTALLOWED, null);
        }
        if(action.getType() != ActionType.OUTPUT){
            logger.error("flow's action is not to set OUTPUT port!");
            return new Status(StatusCode.NOTALLOWED, null);
        }
        NodeConnector oport = ((Output)action).getPort();


        //Use snmp to write to switch fwd table...

        try{
            //1. open snmp communication interface
            String switchIP = cmethUtil.getIpAddr(sw_macAddr);
            InetAddress sw_ipAddr = InetAddress.getByName(switchIP);
            NexusCliCommunicationInterface comInterface = createNexusCliCommInterface(sw_ipAddr);

            //logger.debug("snmp connection created...swtich IP addr=" + sw_ipAddr.toString() + ", community=" + community);

            //2. now can set fwd table entry
           // logger.debug("going to set fwd table entry...");
            Short portShort = (Short)(oport.getID());
            short portID = portShort.shortValue();
            int portInt = (int)portID;
            boolean result = setFwdTableEntry(comInterface, destMac, vlan, portInt, modType);//oport.getID() is the port.  modType as 3 means "learned". modType as 2 means "invalid"
            return (result == true)?(new Status(StatusCode.SUCCESS, null)):
                    (new Status(StatusCode.INTERNALERROR,
                    errorString("program", "snmp to set fwd table", "Vendor Extension Internal Error")));
        }
        catch (UnknownHostException e) {
            logger.error("sw_macAddr {} into InetAddress.getByName() error: {}", sw_macAddr, e);
            return new Status(StatusCode.INTERNALERROR, null);
        }
    }

    private int readFwdTableEntry(NexusCliCommunicationInterface comInterface, short vlan, String destMac){
        logger.debug("enter readFwdTableEntry()...");
        try{

            //SNMPObjectIdentifier snmpOID = (SNMPObjectIdentifier)pair.getSNMPObjectAt(0);


            return 0;

        }
        catch(Exception e)
        {
            logger.error("In readFwdTableEntry(), Exception during SNMP getMIBEntry: {}", e);
            return -1;//meaning fail
        }
    }

    private Map<String, Integer> readAllFwdTableEntry(NexusCliCommunicationInterface comInterface){
        logger.debug("enter readAllFwdTableEntry()...");
        Map<String, Integer> table =  new HashMap<String, Integer>();

        try{
            logger.debug("to retieve oid {}'s value...", portGetOID);

            //SNMPVarBindList tableVars = comInterface.retrieveMIBTable(portGetOID);
            return table;

           }
           catch(Exception e)
           {
               logger.error("In readAllFwdTableEntry(), Exception during SNMP getMIBEntry: {}", e);
               return null;
           }
      }

    public FlowOnNode readFlowRequest(Flow flow, Node node){
        logger.debug("enter NexusCliHandler.readFlowRequest()");

        logger.debug("retrieving the metrics in the Flow...");
        //retrieve dest mac from the flow
        Match match = flow.getMatch();
        MatchField fieldVlan = match.getField(MatchType.DL_VLAN);
        short vlan = ((Short)(fieldVlan.getValue())).shortValue();
        MatchField fieldDlDest= match.getField(MatchType.DL_DST);
        String destMac = HexString.toHexString((byte[])fieldDlDest.getValue());

        //Use snmp to read switch fwd table...

        Long sw_macAddr = (Long) node.getID();
        InetAddress sw_ipAddr = null;

        //1. open snmp communication interface
        try{
            String switchIP = cmethUtil.getIpAddr(sw_macAddr);
            sw_ipAddr = InetAddress.getByName(switchIP);
        }
        catch (UnknownHostException e) {
            logger.error("sw_macAddr {} into InetAddress.getByName() error!: {}", sw_macAddr, e);
            return null;
        }
        if(sw_ipAddr == null) return null;

        NexusCliCommunicationInterface comInterface = createNexusCliCommInterface( sw_ipAddr);
        //logger.debug("snmp connection created...swtich IP addr=" + sw_ipAddr.toString() + ", community=" + community);

        //2. read fwd table entry
        //logger.debug("going to read fwd table entry...");
        int port = readFwdTableEntry(comInterface, vlan, destMac);
        if(port < 0) return null;

        //3. convert the retrieved entry to FlowOnNode
        NodeConnector oport = NodeConnectorCreator.createNodeConnector("SNMP", (short)port, node);
        List<Action> actions = new ArrayList<Action>();
        actions.add(new Output(oport));
        Flow flown = new Flow(flow.getMatch(), actions);
        return new FlowOnNode(flown);
    }

    //return value: 1. null -- switch not found  2. an empty List<FlowOnNode> -- switch found and has no entries
    public List<FlowOnNode>  readAllFlowRequest(Node node){
        logger.debug("enter NexusCliHandler.readAllFlowRequest()");

        //Use snmp to read switch fwd table...

        Long sw_macAddr = (Long) node.getID();
        InetAddress sw_ipAddr = null;

        //1. open snmp communication interface
        try{
            String switchIP = cmethUtil.getIpAddr(sw_macAddr);
            sw_ipAddr = InetAddress.getByName(switchIP);
        }
        catch (UnknownHostException e) {
            logger.error("sw_macAddr {} into InetAddress.getByName() error!: {}", sw_macAddr, e);
            return null;
        }
        if(sw_ipAddr == null) return null;

        
        NexusCliCommunicationInterface comInterface = createNexusCliCommInterface( sw_ipAddr);
        //logger.debug("snmp connection created...swtich IP addr=" + sw_ipAddr.toString() + ", community=" + community);

        //2. now can set fwd table entry
        //logger.debug("going to read fwd table entry...");
        Map<String, Integer> entries = readAllFwdTableEntry(comInterface);
        if(entries == null) return null;
        return forwardingTableEntriesToFlows(entries, node);
    }

    private List<FlowOnNode> forwardingTableEntriesToFlows(Map<String, Integer> entries, Node node){
        List<FlowOnNode> list = new ArrayList<FlowOnNode>();
        for(Map.Entry<String, Integer> entry : entries.entrySet()){
            Match match = new Match();
            String str = entry.getKey();
            short vlan = Short.parseShort(str.substring(0, str.indexOf(".")));
            byte[] macAddrBytes = OIDToMacAddrBytes(str.substring(str.indexOf(".") + 1));
            if(macAddrBytes == null)
                return null;

            match.setField(MatchType.DL_VLAN, vlan);
            match.setField(MatchType.DL_DST, macAddrBytes);
            List<Action> actions = new ArrayList<Action>();
            NodeConnector oport = NodeConnectorCreator.createNodeConnector("SNMP", Short.parseShort(entry.getValue().toString()), node);
            actions.add(new Output(oport));

            Flow flow = new Flow(match, actions);
            list.add(new FlowOnNode(flow));
        }
        return list;
    }

    private int retrievePortNumFromChassisOID(String oidstr){
        //e.g. oidstr as "iso.0.8802.1.1.2.1.4.1.1.5.0.49.1", then return "49"
        int tail = oidstr.lastIndexOf(".");
        int head = oidstr.substring(0, tail).lastIndexOf(".") + 1;
        //logger.debug(oidstr + " ==> head=" + head + ",tail=" + tail);
        String ansStr= oidstr.substring(head, tail);
        int ans = Integer.parseInt(ansStr);
        //logger.debug("oidstr (" + oidstr +") to retrieve port number:" + ans);
        return ans;
    }



    public Status addVLAN(Node node, Long vlanID){
        logger.debug("enter NexusCliHandler.addVLAN()...");
        return addVLAN(node, vlanID, "v" + vlanID);
    }
    public Status addVLAN(Node node, Long vlanID, String vlanName){
        Long sw_macAddr = (Long)(node.getID());
        try{
            //1. open snmp communication interface
            String switchIP = cmethUtil.getIpAddr(sw_macAddr);
            InetAddress sw_ipAddr = InetAddress.getByName(switchIP);
            NexusCliCommunicationInterface comInterface = createNexusCliCommInterface(sw_ipAddr);

            //logger.debug("snmp connection created...swtich IP addr=" + sw_ipAddr.toString() + ", community=" + community);

            //2. now can add vlan
           // logger.debug("going to add vlan...");
            boolean result = addVLANtoSwitch(comInterface, vlanID, vlanName);
            return (result == true)?(new Status(StatusCode.SUCCESS, null)):
                    (new Status(StatusCode.INTERNALERROR,
                    errorString("program", "snmp to add vlan", "Vendor Extension Internal Error")));
        }
        catch (UnknownHostException e) {
            logger.error("sw_macAddr {} into InetAddress.getByName() error: {}", sw_macAddr, e);
            return new Status(StatusCode.INTERNALERROR, null);
        }
    }

    private boolean addVLANtoSwitch(NexusCliCommunicationInterface comInterface, Long vlanID, String vlanName){
        logger.debug("enter NexusCliHandler.addVLANtoSwitch()...");
        try{
            //SNMPVarBindList newVars = comInterface.setMIBEntry(oids, values);
            comInterface.addVlan(vlanID, vlanName); 
          }catch(Exception e){
            logger.error("addVLANtoSwitch, error: " + e);
            return false;
        }
        return true;
    }

    public Status setVLANPorts (Node node, Long vlanID, String portList){
        logger.debug("enter NexusCliHandler.setVLANPorts()...");
        Long sw_macAddr = (Long)(node.getID());
        try{
            //1. open snmp communication interface
            String switchIP = cmethUtil.getIpAddr(sw_macAddr);
            InetAddress sw_ipAddr = InetAddress.getByName(switchIP);
            NexusCliCommunicationInterface comInterface = createNexusCliCommInterface(sw_ipAddr);
             String switchType = cmethUtil.getSwitchType(sw_macAddr);
             System.out.println("switchType: " + switchType); 
            //logger.debug("snmp connection created...swtich IP addr=" + sw_ipAddr.toString() + ", community=" + community);

            //2. now can add vlan
            // logger.debug("going to set vlan ports...");
            //byte[] nodeConnsBytes = convertPortListToBytes(nodeConns);
            String[] ports = portList.split(",");
            //boolean result = setVLANPortstoSwitch(comInterface, vlanID, nodeConnsBytes);
            boolean result = setVLANPortstoSwitch(comInterface, vlanID, ports);
             return (result == true)?(new Status(StatusCode.SUCCESS, null)):
                    (new Status(StatusCode.INTERNALERROR,
                    errorString("program", "snmp to set vlan ports", "Vendor Extension Internal Error")));
        }
        catch (UnknownHostException e) {
            logger.error("sw_macAddr {} into InetAddress.getByName() error: {}", sw_macAddr, e);
            return new Status(StatusCode.INTERNALERROR, null);
        }
    }

    //ToDo: howmany bytes for the port
    private byte[] convertPortListToBytes(List<NodeConnector> nodeConns){
        NodeConnector nc = null;
        int portNum = -1;
        int portList[] = new int[48];
        byte[] answer = new byte[6];
        int index = 0;
        for(int i = 0; i < nodeConns.size(); i++){
            nc = (NodeConnector)(nodeConns.get(i));
            portNum = ((Integer)(nc.getID())).intValue() - 1;
            portList[portNum] = 1;
        }
        String listStr = "port list: ";
        for(int k = 0; k < 48; k++)
            listStr = listStr + portList[k];
        logger.info(listStr);
        for(int j = 0; j < 48 - 7; j += 8){
            int seg = portList[j] * 128 + portList[j + 1] * 64 + portList[j + 2] * 32 + portList[j + 3] * 16
                          + portList[j + 4] * 8 + portList[j + 5] * 4 + portList[j + 6] * 2 + portList[j + 7];
            /*int seg = portList[j] << 7 + portList[j + 1] << 6 + portList[j + 2] << 5 + portList[j + 3] << 4
                          + portList[j + 4] << 3 + portList[j + 5] << 2 + portList[j + 6] << 1 + portList[j + 7];*/
            logger.info("port list [" + j + "~" + (j+7) + "] = " + seg);    
            answer[index++] = (new Integer(seg)).byteValue();
        }
        logger.info("port list as hex string=" + HexString.toHexString(answer));
        return answer;
    }

    private boolean setVLANPortstoSwitch(NexusCliCommunicationInterface comInterface, Long vlanID, String[] ports){
        logger.debug("enter NexusCliHandler.setVLANPortstoSwitch()...");
        try{
            //String oid = vlanEgressPortsOID + "." + vlanID;
            //byte[] value = new byte[42];
            //System.arraycopy(nodeConnsBytes, 0, value, 0, 6);
            //SNMPOctetString octetValue =  new SNMPOctetString(value);
            //comInterface.setMIBEntry(oid, octetValue);
           comInterface.setVLANPortstoSwitch(vlanID, ports); 

        }
        catch(Exception e){
            logger.error("In setVLANPortstoSwitch(), Exception during SNMP setMIBEntry: {}", e);
            return false;
        }
        return true;
    }

    public Status deleteVLAN(Node node, Long vlanID){//return-- true:success, false:fail
        Long sw_macAddr = (Long)(node.getID());
        try{
            //1. open snmp communication interface
            String switchIP = cmethUtil.getIpAddr(sw_macAddr);
            InetAddress sw_ipAddr = InetAddress.getByName(switchIP);
            NexusCliCommunicationInterface comInterface = createNexusCliCommInterface(sw_ipAddr);
            boolean result = deleteVLANFromSwitch(comInterface, vlanID);
            return (result == true)?(new Status(StatusCode.SUCCESS, null)):
                    (new Status(StatusCode.INTERNALERROR,
                    errorString("program", "snmp to delete vlan", "Vendor Extension Internal Error")));
        }
        catch (UnknownHostException e) {
            logger.error("switchIP {} into InetAddress.getByName() error: {}", cmethUtil.getIpAddr(sw_macAddr), e);
            return new Status(StatusCode.INTERNALERROR, null);
        }
    }

    private boolean deleteVLANFromSwitch(NexusCliCommunicationInterface comInterface, Long vlanID){
        ///System.out.println("enter NexusCliHandler.addVLANtoSwitch()...");
        String vlanIDStr = "." + vlanID.toString();
        try{
            comInterface.deleteVLANFromSwitch(vlanID);  
        }catch(Exception e){
            logger.info("deleteVLANFromSwitch, error: " + e);
            logger.info("(maybe because this vlan already exists)");
            return false;
        }
        return true;
    }

    public List<NodeConnector> getVLANPorts(Node node, Long vlanID){//return: the ports of the vlan on the switch
       Long sw_macAddr = (Long)(node.getID());
        try{
            //1. open snmp communication interface
            String switchIP = cmethUtil.getIpAddr(sw_macAddr);
            InetAddress sw_ipAddr = InetAddress.getByName(switchIP);
            NexusCliCommunicationInterface comInterface = createNexusCliCommInterface(sw_ipAddr);

            byte[] valueBytes = new byte[6];//(byte[])value.getValue();
            byte[] portBytes = new byte[6];
            System.arraycopy(valueBytes, 0, portBytes, 0, 6);

            int ports[] = convertPortBytesToList(portBytes);

            String portsStr = HexString.toHexString(portBytes);

            //convert ports (ex. 1010011001000... to {1,3,6,7,10})
            int ansTmp[] = new int[48];
            int index = 0;
            for(int i = 0; i < 48; i++){
                if(ports[i] !=0){
                    ansTmp[index] = i + 1;
                    index += 1;
                }
            }
            int answer[] = new int[index];
            System.arraycopy(ansTmp, 0, answer, 0, index);
            return portsToNcList(answer, node);
        }
        catch(Exception e)
        {
            logger.error("Exception during SNMP getVLANPorts: " + e);
            return null;//meaning fail
        }
    }

    private int[] convertPortBytesToList(byte portBytes[]){
        if(portBytes.length != 6){
            logger.error("convertPortBytesToList(), input portBytes's length != 6!");
            System.exit(0);
        }
        
        int ports[] = new int[48];
        int index = 0;
        for(int i = 0; i < portBytes.length; i++){
            int seg = portBytes[i] & 0xff;
            for(int j = 8; j > 0; j--){
                ports[index + j - 1] = seg % 2;
                seg = seg /2;
            }
            index = index + 8;
        }

        return ports;
    }

    private List<NodeConnector> portsToNcList(int ports[], Node node){
        List<NodeConnector> nodeConns = new ArrayList<NodeConnector>();
        for(int i = 0; i < ports.length; i++){
            nodeConns.add(createNodeConnector(new Short((short)ports[i]), node));
        }
        return nodeConns;
    }

    private static Node createSNMPNode(Long switchId) {
        try {
            return new Node("SNMP", switchId);
        } catch (ConstructionException e1) {
            logger.error("", e1);
            return null;
        }
    }

    private static NodeConnector createNodeConnector(Short portId, Node node) {
        if (node.getType().equals("SNMP")) {
            try {
                 NodeConnector test = new NodeConnector("SNMP", portId, node);

                return test;
            } catch (ConstructionException e1) {
                System.out.println("creartion of new connector failed");

                logger.error("",e1);
                return null;
            }
        }
        return null;
    }

private SNMPv1CommunicationInterface createSNMPv1CommInterface(int version, InetAddress hostAddress, String community){

        try{
       SNMPv1CommunicationInterface comInterface = new SNMPv1CommunicationInterface(version, hostAddress, community);
          return comInterface;
        }
        catch(Exception e)
        {}
       return null;
}

    public VLANTable getVLANTable(Node node){//return: all VLANs on the switch, and also the ports of each VLAN
        VLANTable table = new VLANTable();
        //List<NodeConnector>  ports;

        Map< Long,List<NodeConnector> > map = new HashMap< Long,List<NodeConnector> >() ; 
        Long sw_macAddr = (Long)(node.getID());
        try{
            //1. open snmp communication interface
            String switchIP = cmethUtil.getIpAddr(sw_macAddr);
            InetAddress sw_ipAddr = InetAddress.getByName(switchIP);
	    if(sw_ipAddr == null) 
            {
              System.out.println("the IP address for the switch is null"); 
              return null; 
            } 
           // NexusCliCommunicationInterface comInterface = createNexusCliCommInterface(sw_ipAddr);

           // ArrayList<ArrayList<String>> result = comInterface.getVlanandPorts();
             //  ArrayList<String> portNumbers = result.get(0);
              // Set<String> uniqueVlans = new HashSet<String>(result.get(1)); 
            //table.addEntry(vlanID, ports);
            //System.out.println("In NexusgetVLANTable for OID ");
            //System.out.println(vlanEgressPortsOID);
             
            //SNMPVarBindList tableVars = comInterface.retrieveMIBTable(vlanEgressPortsOID);
            String community = cmethUtil.getSnmpCommunity(sw_macAddr);
            SNMPv1CommunicationInterface comInterface = createSNMPv1CommInterface(0, sw_ipAddr, community);
            SNMPVarBindList tableVars = comInterface.retrieveMIBTable(vlanEgressPortsOID);
            //logger.debug("Number of table entries: " + tableVars.size());^M
            System.out.println("In getVLANtable for: ");
            //System.out.println(vlanEgressPortsOID);

            System.out.println(tableVars.size());
           
            //Set<int> uniqueVlans = new HashSet<int>();
            for(int i = 0; i < tableVars.size(); i++){
                SNMPSequence pair = (SNMPSequence)(tableVars.getSNMPObjectAt(i));

                SNMPObjectIdentifier snmpOID = (SNMPObjectIdentifier)pair.getSNMPObjectAt(0);
                String vlan = pair.getSNMPObjectAt(1).toString();
                //uniqueVlans.add(vlan); 
                String snmpOIDstr = snmpOID.toString();
                int head = snmpOIDstr.lastIndexOf(".")+1;
                int tail = snmpOIDstr.length();
        //logger.debug(oidstr + " ==> head=" + head + ",tail=" + tail);^M
                String portStr= snmpOIDstr.substring(head, tail);
              //          int ans = Integer.parseInt(ansStr);^M
        //
                int portId = Integer.parseInt(portStr);
                Long vlanID = Long.parseLong(vlan);
              //  uniqueVlans.add(vlanID);
               //System.out.println("vlanIDstr " + vlan); 
                NodeConnector nc =createNodeConnector(new Short((short)portId), node); //NodeConnectorCreator.createNodeConnector("SNMP", Integer.parseInt(portStr), node);
               //System.out.println("vlanIDstr " + vlan);
                List<NodeConnector>  ports = new ArrayList<NodeConnector>();
                //List<NodeConnector>  ports = map.get(vlanID);
                //System.out.println("portNumber " + portStr);
                if (map.get(vlanID) != null){
                    ports = map.get(vlanID);  
                    map.remove(vlanID);
                }
                //System.out.println("1portNumber " + portStr);
                ports.add(nc);
                //System.out.println("2portNumber " + portStr);
                map.put(vlanID, ports);
                //ports = getVLANPorts(createSNMPNode(sw_macAddr), vlanID);

                //table.addEntry(vlanID, ports);
                //logger.debug("Retrieved OID: " + snmpOID + " (so port num=" + portNum + "), value: " + valueStr);^M
                }
                //
for (Map.Entry< Long ,List<NodeConnector> > entry : map.entrySet()) {
          table.addEntry(entry.getKey(), entry.getValue());
}
          return table;

        }
        catch(Exception e)
        {
            logger.error("In readLLDPLocalPortIDEntries(), Exception during SNMP getMIBEntry:  " + e + "\n");
            return null;
        }
    }
    
    private String errorString(String phase, String action, String cause) {
        return "Failed to "
                + ((phase != null) ? phase + " the " + action
                        + " flow message: " : action + " the flow: ") + cause;
    }

}
