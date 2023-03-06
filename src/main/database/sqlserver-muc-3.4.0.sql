--
-- Tigase MUC - Multi User Chat component for Tigase
-- Copyright (C) 2007 Tigase, Inc. (office@tigase.com)
--
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published by
-- the Free Software Foundation, version 3 of the License.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Affero General Public License for more details.
--
-- You should have received a copy of the GNU Affero General Public License
-- along with this program. Look for COPYING file in the top folder.
-- If not, see http://www.gnu.org/licenses/.
--

-- QUERY START:
if not exists (select 1 from sys.columns where object_id = object_id('dbo.tig_muc_room_history') and name = 'stable_id')
begin
    alter table [tig_muc_room_history] add [stable_id] [uniqueidentifier];
end
-- QUERY END:
GO

-- QUERY START:
IF (SELECT count(1) FROM [tig_muc_room_history] WHERE [stable_id] IS NULL) > 0
	UPDATE [tig_muc_room_history] SET [stable_id] = NEWID() WHERE [stable_id] IS NULL;
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.columns WHERE name = 'stable_id' AND object_id = object_id('dbo.tig_muc_room_history') AND is_nullable = 1)
    ALTER TABLE [tig_muc_room_history] ALTER COLUMN [stable_id] [uniqueidentifier] NOT NULL;
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_muc_room_history') AND NAME ='IX_tig_muc_room_history_room_jid_sha1_stable_id')
CREATE UNIQUE INDEX IX_tig_muc_room_history_room_jid_sha1_stable_id ON [dbo].[tig_muc_room_history] ([room_jid_sha1], [stable_id]);
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
    @_stableId [nvarchar](36),
    @_ts [datetime],
    @_senderJid [nvarchar](3074),
    @_senderNick [nvarchar](1024),
    @_body [nvarchar](MAX),
    @_publicEvent [bit],
    @_msg [nvarchar](MAX)
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @_roomJidSha1 [varbinary](40);

    SET @_roomJidSha1 = HASHBYTES('SHA1', LOWER( @_roomJid ) );
    INSERT INTO tig_muc_room_history (room_jid, room_jid_sha1, stable_id, event_type, ts, sender_jid, sender_nickname, body, public_event, msg)
        VALUES (@_roomJid, @_roomJidSha1, CONVERT(uniqueidentifier, @_stableId), 1, @_ts, @_senderJid, @_senderNick, @_body, @_publicEvent, @_msg);
    SET NOCOUNT OFF;
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_MAM_UpdateMessage')
	DROP PROCEDURE Tig_MUC_MAM_UpdateMessage
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_MAM_UpdateMessage
    @_roomJid [nvarchar](2049),
    @_stableId [nvarchar](36),
    @_body [nvarchar](MAX),
    @_msg [nvarchar](MAX)
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @_roomJidSha1 [varbinary](40);

    SET @_roomJidSha1 = HASHBYTES('SHA1', LOWER( @_roomJid ) );
    UPDATE tig_muc_room_history
    SET body = @_body, msg = @_msg
    WHERE
        room_jid_sha1 = @_roomJidSha1
        and stable_id = CONVERT(uniqueidentifier, @_stableId);
    SET NOCOUNT OFF;
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_MAM_GetMessage')
	DROP PROCEDURE Tig_MUC_MAM_GetMessage
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_MAM_GetMessage
    @_roomJid [nvarchar](2049),
    @_stableId [nvarchar](36)
AS
BEGIN
    SELECT TOP 1 sender_nickname, CONVERT(nvarchar(36), stable_id) as stable_id, ts, sender_jid, body, msg
    FROM dbo.tig_muc_room_history
    WHERE room_jid_sha1 = HASHBYTES('SHA1', LOWER( @_roomJid ) )
        AND stable_id = CONVERT(uniqueidentifier, @_stableId)
    ORDER BY ts DESC
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
        SELECT TOP (@_maxMessages) sender_nickname, CONVERT(nvarchar(36), stable_id) as stable_id, ts, sender_jid, body, msg
        FROM dbo.tig_muc_room_history
        WHERE room_jid_sha1 = HASHBYTES('SHA1', LOWER( @_roomJid ) )
            AND (
                @_since IS NULL OR ts >= @_since
            )
        ORDER BY ts DESC
    )
    SELECT sender_nickname, stable_id, ts, sender_jid, body, msg
    FROM results_cte
    ORDER BY ts ASC;
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_MAM_GetMessages')
	DROP PROCEDURE Tig_MUC_MAM_GetMessages
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_MAM_GetMessages
    @_roomJid [nvarchar](2049),
    @_since [datetime],
    @_to [datetime],
    @_nickname [nvarchar](1024),
    @_limit [int],
    @_offset [int]
AS
BEGIN
    ;WITH results_cte AS (
        SELECT sender_nickname, CONVERT(nvarchar(36), stable_id) as stable_id, ts, sender_jid, body, msg,
            ROW_NUMBER() OVER (ORDER BY ts) AS row_num
        FROM dbo.tig_muc_room_history
        WHERE room_jid_sha1 = HASHBYTES('SHA1', LOWER( @_roomJid ) )
            AND ( @_since IS NULL OR ts >= @_since )
            AND ( @_to IS NULL OR ts <= @_to )
            AND ( @_nickname IS NULL OR sender_nickname = @_nickname )
    )
    SELECT sender_nickname, stable_id, ts, sender_jid, body, msg
    FROM results_cte
    WHERE row_num > @_offset
        AND row_num <= @_offset + @_limit
    ORDER BY ts ASC;
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_MAM_GetMessagePosition')
	DROP PROCEDURE Tig_MUC_MAM_GetMessagePosition
-- QUERY END:
GO


-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_MAM_GetMessagePosition
    @_roomJid [nvarchar](2049),
    @_since [datetime],
    @_to [datetime],
    @_nickname [nvarchar](1024),
    @_stableId [nvarchar](36)
AS
BEGIN
    SELECT count(1)
    FROM dbo.tig_muc_room_history
    WHERE room_jid_sha1 = HASHBYTES('SHA1', LOWER( @_roomJid ) )
        AND ( @_since IS NULL OR ts >= @_since )
        AND ( @_to IS NULL OR ts <= @_to )
        AND ( @_nickname IS NULL OR sender_nickname = @_nickname )
        AND ts < (
            SELECT h1.ts
            FROM tig_muc_room_history h1
            WHERE h1.room_jid_sha1 = HASHBYTES('SHA1', LOWER( @_roomJid ) )
            AND h1.stable_id = CONVERT(uniqueidentifier, @_stableId)
        )
END
-- QUERY END:
GO
