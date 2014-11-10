nexusSnmpODL
============
This is an updated version of the snmp4sdn pluging that already exists for ODL Heluim. The basic instructions to install the 
snmp4sdn plugin would generally also apply here (obviously instead of git clone https://git.opendaylight.org/gerrit/p/snmp4sdn.git 
you need to clone the one here).

The instructions could be found here:
https://wiki.opendaylight.org/view/SNMP4SDN:Installation_Guide#Build_the_code

This version of snmp4sdn supports some of the required hooks to communicate with the NExus 5K switch through SNMP (read) 
and CLI (write). The only supported feature is adding and showing VLANs.

Use case
============

The actual ODL controller (version Heluim) needs to be downloaded from the ODL webpage and extracted (currently there seem 
to be some bug in the system that evey time something changes in the snmp plugin the controller tar.gz file needs to be 
extracted again)

tar xvf distribution-karaf-0.2.0-Helium.tar.gz 
cd distribution-karaf-0.2.0-Helium/bin/

The following commands could give an example how the new plugin would work:

./karaf
feature:install odl-adsal-all
feature:install odl-adsal-northbound
topologymanager:printNodeEdges
feature:repo-remove mvn:org.opendaylight.snmp4sdn/features-snmp4sdn/0.1.3-Helium/xml/features
feature:repo-add mvn:org.opendaylight.snmp4sdn/features-snmp4sdn/0.1.3-SNAPSHOT/xml/features
feature:repo-list | grep snmp
feature:install odl-snmp4sdn-all
snmp4sdn:ReadDB ~/nexusSnmpODL/snmpSwitchDB.txt 
snmp4sdn:TopoDiscover
topologymanager:printNodeEdges
snmp4sdn:printvlantable 28:7f:77:c9:52:21
snmp4sdn:addvlansetports 28:7f:77:c9:52:21 100 vlan100 eth1/1,eth1/2

(28:7f:77:c9:52:21 is the mac address of the management port of our nexus switch)

