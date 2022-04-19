Announcement
==============

Major changes
----------------

Tigase MUC component has undergone a few major changes to our code and structure. To continue to use Tigase MUC component, a few changes may be needed to be made to your systems. Please see them below:

Database schema changes
^^^^^^^^^^^^^^^^^^^^^^^^^^

We decided to improve performance of MUC repository storage and to do so we needed to change database schema of MUC component. Additionally we decided to no longer use *in-code* database upgrade to update database schema of MUC component and rather provide separate schema files for every supported database.

To continue usage of new versions of MUC component it is required to manually load new component database schema, see `??? <#Preparation of database>`__ section for informations about that.

Moreover we no longer store rooms list and configurations inside ``UserRepository`` of default Tigase XMPP Server database. Instead we use separate tables which are part of new schema. Due to that it is required to execute converter which will move room configurations from ``UserRepository`` to new tables. It needs to be executed **AFTER** new database schema is loaded to database.

.. Note::

   If you used separate database to store messages history we strongly suggest to use same database for new schema and storage of rooms configurations as well. In other case message history will not be moved to new schema.

In ``database`` directory of installation package there is a ``muc-db-migrate`` utility which takes 2 parameters:

-in 'jdbc_uri_to_user_repository'
   To set JDBC URI of UserRepository

-out 'jdbc_uri_to_muc_database'
   To set JDBC URI of database with loaded database schema.

.. Tip::

   Both JDBC uri’s may be the same.

.. Warning::

    During this opeartion it removes room configurations from old storage.

Examples
~~~~~~~~~

UNIX / Linux / OSX

::

   database/muc-db-migrate.sh -in 'jdbc:mysql://localhost/database1' -out 'jdbc:mysql://localhost/database2'

Windows

::

   database/muc-db-migrate.cmd -in 'jdbc:mysql://localhost/database1' -out 'jdbc:mysql://localhost/database2'

Support for MAM
^^^^^^^^^^^^^^^^^^

In this version we added support for `XEP-0313: Message Archive Management <http://xmpp.org/extensions/xep-0313.html:>`__ protocol which allows any MAM compatible XMPP client with MUC support to retrieve room chat history using MAM and more advanced queries than retrieval of last X messages or messages since particular date supported by MUC

Disabled support for XEP-0091: Legacy Delayed Delivery
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

In this version we disabled by default support for `XEP-0091: Legacy Delayed Delivery <https://xmpp.org/extensions/xep-0091.html:>`__. This decision was made due to the fact that usage of XEP-0091 is not recommended any more and should be used only for backward compatibility. Moreover, it added overhead to each transmitted message sent from MUC room history, while the same information was already available in `XEP-0203: Delayed Delivery <https://xmpp.org/extensions/xep-0203.html:>`__ format. For more information see `Enabling support for XEP-0091: Legacy Delayed Delivery <#legacyDelayedDeliveryEnabled>`__