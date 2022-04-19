Configuration
================

To enable Tigase MUC Component you need to add following block to ``etc/init.properties`` file:

::

   muc () {
   }

It will enable component and configure it under name ``muc``. By default it will also use database configured as ``default`` data source to store data - including room configuration, affiliations and chat history.

Using separate storage
---------------------------------

As mentioned above, by default Tigase MUC component uses ``default`` data source configured for Tigase XMPP Server. It is possible to use separate store by MUC component. To do so you need to configure new ``DataSource`` in ``dataSource`` section. Here we will use ``muc-store`` as name of newly configured data source. Additionally you need to pass name of newly configured data source to ``dataSourceName`` property of ``default`` DAO of MUC component.

::

   dataSource {
       muc-store () {
           uri = 'jdbc:postgresql://server/muc-database'
       }
   }

   muc () {
       muc-dao {
           default () {
               dataSourceName = 'muc-store'
           }
       }
   }

It is also possible to configure separate store for particular domain, ie. ``muc.example.com``. Here we will configure data source with name ``muc.example.com`` and use it to store data for MUC rooms hosted at ``muc.example.com``:

::

   dataSource {
       'muc.example.com' () {
           uri = 'jdbc:postgresql://server/example-database'
       }
   }

   muc () {
       muc-dao {
           'muc.example.com' () {
             # we may not set dataSourceName as it matches name of domain
           }
       }
   }

.. Note::

   With this configuration room data for other domains than example.com will be stored in default data source.

Configuring default room configuration
------------------------------------------

It is possible to define value for every room option by setting it’s value to ``defaultRoomConfig`` as a property:

::

   muc () {
       defaultRoomConfig {
           <option> = <value>
       }
   }

for example:

::

   muc () {
       defaultRoomConfig {
           'tigase#presence_delivery_logic' = 'PREFERE_LAST'
       }
   }

Enabling and configuring MUC room logging
------------------------------------------

MUC component supports logging inforamtions about

-  joining room

-  leaving room

-  broadcasting message by room

-  setting room chat subject

to HTML, XML or plain text files.

To enable this functionality you need to modify ``etc/init.properties`` file to enable ``muc-logger`` in MUC component, like this:

::

   muc () {
       muc-logger () {
       }
   }

By default files are stored in ``logs`` subdirectory of Tigase XMPP Server installation directory. You may change it by setting ``room-log-directory`` property of MUC component to path where you want to store room logs.

::

   muc () {
       'muc-logger' () {
       }
       'room-log-directory' = '/var/log/muc/'
   }

We provide default logger for room events, but if you want, you may set your own custom logger. Here we set ``com.example.CustomLogger`` as logger for MUC rooms:

::

   muc () {
       'muc-logger' (class: com.example.CustomLogger) {
       }
   }


Disable message filtering
---------------------------

MUC component by default filters messages and allows only ``<body/>`` element to be delivered to participants. To disable this filtering it is required to set ``message-filter-enabled`` property of MUC component to ``false``.

::

   muc () {
       'message-filter-enabled' = false
   }

11.5.5. Disable presence filtering
-----------------------------------

To disable filter and allow MUC transfer all subelements in <presence/>, ``presence-filter-enabled`` property of MUC component needs to be set to ``false``

::

   muc () {
       'presence-filter-enabled' = false
   }

Configuring discovering of disconnected participants
-------------------------------------------------------

MUC component automatically discovers disconnected participants by checking if user is still connected every 5 minutes.

It is possible to increase checking frequency by setting ``search-ghosts-every-minute`` property of MUC component to ``true``

::

   muc () {
       'search-ghosts-every-minute' = trues
   }

It is also possible to disable this discovery by setting ``ghostbuster-enabled`` property of MUC component to ``false``

::

   muc () {
       'ghostbuster-enabled' = false
   }

Allow chat states in rooms
---------------------------

To allow transfer of chat-states in MUC messages set ``muc-allow-chat-states`` property of MUC component to ``true``

::

   muc () {
       'muc-allow-chat-states' = true
   }

Disable locking of new rooms
--------------------------------

To turn off default locking newly created rooms set ``muc-lock-new-room`` property of MUC component to \`false’ by default new room will be locked until owner submits a new room configuration.

::

   muc () {
       'muc-lock-new-room' = false
   }

Disable joining with multiple resources under same nickname
--------------------------------------------------------------

To disable joining from multiple resources under single nickname set ``muc-multi-item-allowed`` property of MUC component to ``false``

::

   muc () {
       'muc-multi-item-allowed' = false
   }

Enabling support for XEP-0091: Legacy Delayed Delivery
------------------------------------------------------------

To enable support for XEP-0091 you need to set ``legacy-delayed-delivery-enabled`` property of MUC component to ``true``

::

   muc () {
       'legacy-delayed-delivery-enabled' = true
   }