Offline users
===============

If user affiliation is marked as persistent (which can be done using admin ad-hoc commands), MUC delivers presence to occupants in name of offline user. MUC generates presence with ``extended away`` info:

.. code:: xml

   <presence from="…" to="…">
       <show>xa</show>
   </presence>

This presence is sent to occupants, when user goes offline and when persistent occupant is added to room (but he is offline). If persistent user if online in room, then MUC sens real presence of occupant.

Entering the room
---------------------

.. Important::

   When user is joining to room, he MUST use his BareJID as room nickname!

**Example of entering to room.**

.. code:: xml

   <presence
       from='hag66@shakespeare.lit/pda'
       id='n13mt3l'
       to='coven@chat.shakespeare.lit/hag66@shakespeare.lit'>
     <x xmlns='http://jabber.org/protocol/muc'/>
   </presence>

Messages
------------------

Room members marked as persistent are able to send message to room, when they not in room. Message will be treated as sent from online user, and delivered to all occupants.

All groupchat messages will be also sent to offline members if they are marked as persistent.

