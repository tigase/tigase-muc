Database
==============

.. _Preparation_of_database:

Preparation of database
-----------------------------------

Before you will be able to use Tigase MUC Component you need to initialize this database. We provide few schemas for this component for MySQL, PostgreSQL, SQLServer and DerbyDB.

They are placed in ``database/`` directory of installation package and named in ``dbtype-mucversion.sql``, where ``dbname`` in name of database type which this schema supports and ``version`` is version of a MUC component for which this schema is designed.

You need to manually select schema for correct database and component and load this schema to database. For more information about loading database schema look into `Database Preparation <#Database Preparation>`__ section of `Tigase XMPP Server Administration Guide <#Tigase XMPP Server Administration Guide>`__

Upgrade of database schema
----------------------------

Database schema for our components may change between versions and if so it needs to be updated before new version may be started. To upgrade schema please follow instuctions from :ref:`Preparation of database<Preparation_of_database>` section.

.. Note::

   If you use SNAPSHOT builds then schema may change for same version as this are versions we are still working on.

Schema description
---------------------

Tigase MUC component uses few tables and stored procedures. To make it easier to find them on database level they are prefixed with ``tig_muc_``.

Table ``tig_muc_rooms``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This table stores list of rooms and configuration of rooms.

+----------------------+-------------------------------------+----------------------------------------------------+
| Field                | Description                         | Comments                                           |
+======================+=====================================+====================================================+
| room_id              | Database ID of a room               |                                                    |
+----------------------+-------------------------------------+----------------------------------------------------+
| jid                  | Room JID                            |                                                    |
+----------------------+-------------------------------------+----------------------------------------------------+
| jid_sha1             | SHA1 value of lowercased room JID   | Used for proper bare JID comparison during lookup. |
|                      |                                     |                                                    |
|                      |                                     | (Not exists in PostgreSQL schema)                  |
+----------------------+-------------------------------------+----------------------------------------------------+
| name                 | Room name                           |                                                    |
+----------------------+-------------------------------------+----------------------------------------------------+
| config               | Serialized room configuration       |                                                    |
+----------------------+-------------------------------------+----------------------------------------------------+
| creator              | Bare JID of room creator            |                                                    |
+----------------------+-------------------------------------+----------------------------------------------------+
| creation_date        | Room creation date                  |                                                    |
+----------------------+-------------------------------------+----------------------------------------------------+
| subject              | Room subject                        |                                                    |
+----------------------+-------------------------------------+----------------------------------------------------+
| subject_creator_nick | Nick of participant who set subject |                                                    |
+----------------------+-------------------------------------+----------------------------------------------------+
| subject_date         | Timestamp of subject                |                                                    |
+----------------------+-------------------------------------+----------------------------------------------------+


Table ``tig_muc_room_affiliations``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Table stores rooms affiliations.

+-------------+----------------------------------------+----------------------------------------------------+
| Field       | Description                            | Comments                                           |
+=============+========================================+====================================================+
| room_id     | ID of a room                           | References ``room_id`` from ``tig_muc_rooms``      |
+-------------+----------------------------------------+----------------------------------------------------+
| jid         | JID of affiliate                       |                                                    |
+-------------+----------------------------------------+----------------------------------------------------+
| jid_sha1    | SHA1 value of lowercased affiliate JID | Used for proper bare JID comparison during lookup. |
|             |                                        |                                                    |
|             |                                        | (Not exists in PostgreSQL schema)                  |
+-------------+----------------------------------------+----------------------------------------------------+
| affiliation | Affiliation between room and affiliate |                                                    |
+-------------+----------------------------------------+----------------------------------------------------+


Table ``tig_muc_room_history``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Table stores room messages history.

+-----------------+-----------------------------------+-------------------------------------------------------------+
| Field           | Description                       | Comments                                                    |
+=================+===================================+=============================================================+
| room_jid        | Room JID                          |                                                             |
+-----------------+-----------------------------------+-------------------------------------------------------------+
| room_jid_sha1   | SHA1 value of lowercased room JID | Used for proper bare JID comparison during lookup.          |
|                 |                                   |                                                             |
|                 |                                   | (Not exists in PostgreSQL schema)                           |
+-----------------+-----------------------------------+-------------------------------------------------------------+
| event_type      |                                   | For future use, if we decide to store other events as well. |
+-----------------+-----------------------------------+-------------------------------------------------------------+
| ts              | Timestamp of a message            |                                                             |
+-----------------+-----------------------------------+-------------------------------------------------------------+
| sender_jid      | JID of a sender                   |                                                             |
+-----------------+-----------------------------------+-------------------------------------------------------------+
| sender_nickname | Nickname of a message sender      |                                                             |
+-----------------+-----------------------------------+-------------------------------------------------------------+
| body            | Body of a message                 |                                                             |
+-----------------+-----------------------------------+-------------------------------------------------------------+
| public_event    | Mark public events                |                                                             |
+-----------------+-----------------------------------+-------------------------------------------------------------+
| msg             | Serialized message                |                                                             |
+-----------------+-----------------------------------+-------------------------------------------------------------+
