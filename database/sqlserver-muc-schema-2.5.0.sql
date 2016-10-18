--
--  Tigase MUC Component
--  Copyright (C) 2016 "Tigase, Inc." <office@tigase.com>
--
--  This program is free software: you can redistribute it and/or modify
--  it under the terms of the GNU Affero General Public License as published by
--  the Free Software Foundation, either version 3 of the License.
--
--  This program is distributed in the hope that it will be useful,
--  but WITHOUT ANY WARRANTY; without even the implied warranty of
--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
--  GNU Affero General Public License for more details.
--
--  You should have received a copy of the GNU Affero General Public License
--  along with this program. Look for COPYING file in the top folder.
--  If not, see http://www.gnu.org/licenses/.

-- QUERY START:
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='tig_muc_rooms' AND xtype='U')
    CREATE TABLE tig_muc_rooms (
	    [room_id] [bigint] IDENTITY(1,1) NOT NULL,
	    [jid] [nvarchar](2049) NOT NULL,
	    [jid_index] AS CAST( [jid] AS NVARCHAR(255)),
	    [jid_sha1] [varbinary](40) NOT NULL,
	    [name] [nvarchar](1024),
    	[config] [nvarchar](MAX),
	    [creator] [nvarchar](2049) NOT NULL,
	    [creation_date] [datetime] NOT NULL,
        [subject] [nvarchar](MAX),
        [subject_creator_nick] [nvarchar](1024),
        [subject_date] datetime

		PRIMARY KEY ( [room_id] ),
		UNIQUE (jid_sha1)
    );
-- QUERY END:
GO


-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_muc_rooms') AND NAME ='IX_tig_muc_rooms_jids_sha1')
	CREATE INDEX IX_tig_muc_rooms_jids_sha1 ON [dbo].[tig_muc_rooms](jid_sha1);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='tig_muc_room_affiliations' AND xtype='U')
    CREATE TABLE tig_muc_room_affiliations (
	    [room_id] [bigint] NOT NULL,
        [jid] [nvarchar](2049) NOT NULL,
        [jid_sha1] [varbinary](40) NOT NULL,
        [affiliation] [nvarchar](20) NOT NULL,

        UNIQUE (room_id, jid_sha1),
	    CONSTRAINT [FK_tig_muc_room_affiliations_room_id] FOREIGN KEY ([room_id])
			REFERENCES [dbo].[tig_muc_rooms]([room_id])
    );
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_muc_room_affiliations') AND NAME ='IX_tig_muc_room_affiliations_room_id')
	CREATE INDEX IX_tig_muc_room_affiliations_room_id ON [dbo].[tig_muc_room_affiliations](room_id);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_muc_room_affiliations') AND NAME ='IX_tig_muc_room_affiliations_room_id_jid_sha1')
	CREATE INDEX IX_tig_muc_room_affiliations_room_id_jid_sha1 ON [dbo].[tig_muc_room_affiliations](room_id, jid_sha1);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='tig_muc_room_history' AND xtype='U')
    CREATE TABLE tig_muc_room_history (
	    [room_jid] [nvarchar](2049) NOT NULL,
	    [room_jid_sha1] [varbinary](40) NOT NULL,
        [event_type] [int],
        [ts] [datetime] NOT NULL,
        [sender_jid] [nvarchar](3074),
        [sender_nickname] [nvarchar](1024),
	    [body] [nvarchar](MAX),
	    [public_event] [bit],
	    [msg] [nvarchar](MAX)
    );
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_muc_room_history') AND NAME ='IX_tig_muc_room_history_room_jid_sha1')
	CREATE INDEX IX_tig_muc_room_history_room_jid_sha1 ON [dbo].[tig_muc_room_history](room_jid_sha1);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_muc_room_history') AND NAME ='IX_tig_muc_room_history_room_jid_sha1_ts')
	CREATE INDEX IX_tig_muc_room_history_room_jid_sha1_ts ON [dbo].[tig_muc_room_history](room_jid_sha1, ts);
-- QUERY END:
GO

-- ---------------------
-- Stored procedures
-- ---------------------

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_CreateRoom')
	DROP PROCEDURE Tig_MUC_CreateRoom
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_CreateRoom
    @_roomJid [nvarchar](2049),
    @_creatorJid [nvarchar](2049),
    @_creationDate [datetime],
    @_roomName [nvarchar](1024),
    @_roomConfig [nvarchar](MAX)
AS
BEGIN
    DECLARE @_roomId [bigint];
    DECLARE @_roomJidSha1 [varbinary](40)

    SET @_roomJidSha1 = HASHBYTES('SHA1', LOWER( @_roomJid ) )
    INSERT INTO dbo.tig_muc_rooms (jid, jid_sha1, name, config, creator, creation_date)
		    VALUES (@_roomJid, @_roomJidSha1, @_roomName, @_roomConfig, @_creatorJid, @_creationDate);

    SELECT @@IDENTITY as room_id;
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_DestroyRoom')
	DROP PROCEDURE Tig_MUC_DestroyRoom
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_DestroyRoom
    @_roomJid [nvarchar](2049)
AS
BEGIN
    DECLARE @_roomId [bigint];
    DECLARE @_roomJidSha1 [varbinary](40)

    SET @_roomJidSha1 = HASHBYTES('SHA1', LOWER( @_roomJid ) )
    SELECT @_roomId = room_id FROM dbo.tig_muc_rooms WHERE jid_sha1 = @_roomJidSha1;

    IF @_roomId IS NOT NULL
    BEGIN
        DELETE FROM dbo.tig_muc_room_affiliations WHERE room_id = @_roomId;
        DELETE FROM dbo.tig_muc_rooms WHERE room_id = @_roomId;
    END
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_GetRoomAffiliations')
	DROP PROCEDURE Tig_MUC_GetRoomAffiliations
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_GetRoomAffiliations
    @_roomId [bigint]
AS
BEGIN
    SELECT jid, affiliation FROM dbo.tig_muc_room_affiliations WHERE room_id = @_roomId;
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_GetRoom')
	DROP PROCEDURE Tig_MUC_GetRoom
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_GetRoom
    @_roomJid [nvarchar](2049)
AS
BEGIN
    DECLARE @_roomJidSha1 [varbinary](40);

    SET @_roomJidSha1 = HASHBYTES('SHA1', LOWER( @_roomJid ) )

    SELECT room_id, creation_date, creator, config, subject, subject_creator_nick, subject_date
    FROM tig_muc_rooms
    WHERE jid_sha1 = @_roomJidSha1;
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_GetRoomsJids')
	DROP PROCEDURE Tig_MUC_GetRoomsJids
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_GetRoomsJids
AS
BEGIN
    SELECT jid
    FROM tig_muc_rooms
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_SetRoomAffiliation')
	DROP PROCEDURE Tig_MUC_SetRoomAffiliation
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_SetRoomAffiliation
    @_roomId [bigint],
    @_jid [nvarchar](2049),
    @_affiliation [nvarchar](20)
AS
BEGIN
    DECLARE @_jidSha1 [varbinary](40);

    SET @_jidSha1 = HASHBYTES('SHA1', LOWER( @_jid ) )

    IF EXISTS (SELECT 1 FROM dbo.tig_muc_room_affiliations WHERE room_id = @_roomId and jid_sha1 = @_jidSha1)
    BEGIN
        IF @_affiliation <> 'none'
            UPDATE dbo.tig_muc_room_affiliations SET affiliation = @_affiliation WHERE room_id = @_roomId and jid_sha1 = @_jidSha1;
        ELSE
            DELETE dbo.tig_muc_room_affiliations WHERE room_id = @_roomId and jid_sha1 = @_jidSha1;
    END
    ELSE
    BEGIN
        IF @_affiliation <> 'none'
            INSERT INTO dbo.tig_muc_room_affiliations (room_id, jid, jid_sha1, affiliation)
                VALUES (@_roomId, @_jid, @_jidSha1, @_affiliation);
    END
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_SetRoomSubject')
	DROP PROCEDURE Tig_MUC_SetRoomSubject
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_SetRoomSubject
    @_roomId [bigint],
    @_subject [nvarchar](MAX),
    @_creator [nvarchar](1024),
    @_changeDate [datetime]
AS
BEGIN
    UPDATE tig_muc_rooms SET subject = @_subject, subject_creator_nick = @_creator, subject_date = @_changeDate WHERE room_id = @_roomId;
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_SetRoomConfig')
	DROP PROCEDURE Tig_MUC_SetRoomConfig
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_SetRoomConfig
    @_roomJid [nvarchar](2049),
    @_name [nvarchar](1024),
    @_config [nvarchar](MAX)
AS
BEGIN
    UPDATE tig_muc_rooms SET name = @_name, config = @_config WHERE jid_sha1 = HASHBYTES('SHA1', LOWER( @_roomJid ) );
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_AddMessage')
	DROP PROCEDURE Tig_MUC_AddMessage
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_AddMessage
    @_roomJid [nvarchar](2049),
    @_ts [datetime],
    @_senderJid [nvarchar](3074),
    @_senderNick [nvarchar](1024),
    @_body [nvarchar](MAX),
    @_publicEvent [bit],
    @_msg [nvarchar](MAX)
AS
BEGIN
    DECLARE @_roomJidSha1 [varbinary](40);

    SET @_roomJidSha1 = HASHBYTES('SHA1', LOWER( @_roomJid ) );
    INSERT INTO tig_muc_room_history (room_jid, room_jid_sha1, event_type, ts, sender_jid, sender_nickname, body, public_event, msg)
        VALUES (@_roomJid, @_roomJidSha1, 1, @_ts, @_senderJid, @_senderNick, @_body, @_publicEvent, @_msg);
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_DeleteMessages')
	DROP PROCEDURE Tig_MUC_DeleteMessages
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_DeleteMessages
    @_roomJid [nvarchar](2049)
AS
BEGIN
    DELETE FROM tig_muc_room_history WHERE room_jid_sha1 = HASHBYTES('SHA1', LOWER( @_roomJid ) );
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_GetMessages')
	DROP PROCEDURE Tig_MUC_GetMessages
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_GetMessages
    @_roomJid [nvarchar](2049),
    @_maxMessages [int],
    @_since [datetime]
AS
BEGIN
    ;WITH results_cte AS (
        SELECT TOP (@_maxMessages) sender_nickname, ts, sender_jid, body, msg
        FROM dbo.tig_muc_room_history
        WHERE room_jid_sha1 = HASHBYTES('SHA1', LOWER( @_roomJid ) )
            AND (
                @_since IS NULL OR ts >= @_since
            )
        ORDER BY ts DESC
    )
    SELECT sender_nickname, ts, sender_jid, body, msg
    FROM results_cte
    ORDER BY ts ASC;
END
-- QUERY END:
GO

-- ---------------------
-- Converting history to new format
-- ---------------------

-- QUERY START:
IF EXISTS (SELECT * FROM sysobjects WHERE name='muc_history' AND xtype='U')
BEGIN
    INSERT INTO dbo.tig_muc_room_history (room_jid, room_jid_sha1, event_type, ts, sender_jid, sender_nickname, body, public_event, msg)
        SELECT room_name, HASHBYTES('SHA1', LOWER( room_name ) ), event_type,
        DATEADD(ms, timestamp % 1000, DATEADD(s, timestamp/1000, convert(datetime, '19700101', 112))),
            sender_jid, sender_nickname, body, public_event, msg
        FROM muc_history;

     EXEC sp_rename 'dbo.muc_history', 'muc_history_old';
END
-- QUERY END:
GO
