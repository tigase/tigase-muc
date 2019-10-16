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

--

-- QUERY START:
if not exists (select 1 from sys.columns where object_id = object_id('dbo.tig_muc_room_affiliations') and name = 'persistent')
begin
    alter table tig_muc_room_affiliations add persistent int not null default 0;
end
-- QUERY END:
GO

-- QUERY START:
if not exists (select 1 from sys.columns where object_id = object_id('dbo.tig_muc_room_affiliations') and name = 'nickname')
begin
    alter table tig_muc_room_affiliations add nickname nvarchar(1024);
end
-- QUERY END:
GO

-- QUERY START:
if not exists (select 1 from sys.columns where object_id = object_id('dbo.tig_muc_rooms') and name = 'avatar_hash')
begin
    alter table tig_muc_rooms
        add avatar_hash nvarchar(22);
    alter table tig_muc_rooms
        add avatar nvarchar(MAX);
end
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
    SELECT jid, affiliation, [persistent], nickname FROM dbo.tig_muc_room_affiliations WHERE room_id = @_roomId;
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
    @_affiliation [nvarchar](20),
    @_persistent [int],
    @_nickname [nvarchar](1024)
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @_jidSha1 [varbinary](40);

    SET @_jidSha1 = HASHBYTES('SHA1', LOWER( @_jid ) )

    IF EXISTS (SELECT 1 FROM dbo.tig_muc_room_affiliations WHERE room_id = @_roomId and jid_sha1 = @_jidSha1)
    BEGIN
        IF @_affiliation <> 'none'
            UPDATE dbo.tig_muc_room_affiliations SET affiliation = @_affiliation, [persistent] = @_persistent, nickname = @_nickname WHERE room_id = @_roomId and jid_sha1 = @_jidSha1;
        ELSE
            DELETE dbo.tig_muc_room_affiliations WHERE room_id = @_roomId and jid_sha1 = @_jidSha1;
    END
    ELSE
    BEGIN
        IF @_affiliation <> 'none'
            INSERT INTO dbo.tig_muc_room_affiliations (room_id, jid, jid_sha1, affiliation, [persistent], nickname)
                VALUES (@_roomId, @_jid, @_jidSha1, @_affiliation, @_persistent, @_nickname);
    END
    SET NOCOUNT OFF;
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

    SELECT room_id, creation_date, creator, config, subject, subject_creator_nick, subject_date, avatar_hash
    FROM tig_muc_rooms
    WHERE jid_sha1 = @_roomJidSha1;
END
-- QUERY END:
GO


-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_GetRoomAvatar')
    DROP PROCEDURE Tig_MUC_GetRoomAvatar
-- QUERY END:
GO

-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_GetRoomAvatar @_roomId [bigint]
AS
BEGIN
    SELECT avatar FROM tig_muc_rooms WHERE room_id = @_roomId;
END
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MUC_SetRoomAvatar')
    DROP PROCEDURE Tig_MUC_SetRoomAvatar
-- QUERY END:
GO


-- QUERY START:
CREATE PROCEDURE dbo.Tig_MUC_SetRoomAvatar
    @_roomId [bigint],
    @_encodedAvatar [nvarchar](MAX),
    @_avatarHash [nvarchar](22)
AS
BEGIN
    UPDATE tig_muc_rooms SET avatar = @_encodedAvatar, avatar_hash = @_avatarHash WHERE room_id = @_roomId;
END
-- QUERY END:
GO
