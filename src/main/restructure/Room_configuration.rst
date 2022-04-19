Room configuration options
===========================

In addition to the default Room configuration options defined in the MUC specification Tigase offers following as well:

**Tigase MUC Options**
   -  tigase#presence_delivery_logic - allows configuring logic determining which presence should be used by occupant in the room while using multiple-resource connections under one nickname, following options are available:

      -  PREFERE_PRIORITY

      -  PREFERE_LAST

   -  tigase#presence_filtering - (boolean) when enabled broadcasts presence only to selected affiliation groups

   -  tigase#presence_filtered_affiliations - when enabled tigase#presence_filtering is enabled one can select affiliation which should receive presences, following are possible to select from:

      -  owner

      -  admin

      -  member

      -  none

      -  outcast

   -  muc#roomconfig_maxusers - Allows configuring of maximum users of room.

**Configuring default room configuration in init.properties**
   For more informations look into `??? <#Configuring default room configuration>`__

**Configuration per-room**
   Per room configuration is done using IQ stanzas defined in the specification, for example:

.. code:: xml

   <iq type="set" to="roomname@muc.domain" id="config1">
       <query xmlns="http://jabber.org/protocol/muc#owner">
           <x xmlns="jabber:x:data" type="submit">
               <field type="boolean" var="tigase#presence_filtering">
                   <value>1</value>
               </field>
               <field type="list-multi" var="tigase#presence_filtered_affiliations">
                   <value>owner</value>
               </field>
           </x>
       </query>
   </iq>
