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

delimiter //

-- QUERY START:
create procedure TigUpgradeMuc()
begin
    if not exists (select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_muc_room_affiliations' and column_name = 'persistent') then
        alter table tig_muc_room_affiliations add persistent int not null default 0;
    end if;

    if not exists (select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_muc_room_affiliations' and column_name = 'nickname') then
        alter table tig_muc_room_affiliations add nickname varchar(1024);
    end if;
end //
-- QUERY END:

delimiter ;

-- QUERY START:
call TigUpgradeMuc();
-- QUERY END:

-- QUERY START:
drop procedure if exists TigUpgradeMuc;
-- QUERY END:

-- ---------------------
-- Stored procedures
-- ---------------------

-- QUERY START:
drop procedure if exists Tig_MUC_GetRoomAffiliations;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_SetRoomAffiliation;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure Tig_MUC_GetRoomAffiliations(_roomId bigint)
begin
    select jid, affiliation, persistent, nickname from tig_muc_room_affiliations where room_id = _roomId;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_SetRoomAffiliation(_roomId bigint, _jid varchar(2049), _affiliation varchar(20), _persistent int, _nickname  varchar(1024))
begin
	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
	END;

	START TRANSACTION;

    if exists( select 1 from tig_muc_room_affiliations where room_id = _roomId and jid_sha1 = SHA1( LOWER( _jid ) ) ) then
        if _affiliation <> 'none' then
            update tig_muc_room_affiliations set affiliation = _affiliation, persistent = _persistent, nicknane = _nickanme where room_id = _roomId and jid_sha1 = SHA1( LOWER( _jid ) );
        else
            delete from tig_muc_room_affiliations where room_id = _roomId and jid_sha1 = SHA1( LOWER( _jid ) );
        end if;
    else
        if _affiliation <> 'none' then
            insert into tig_muc_room_affiliations (room_id, jid, jid_sha1, affiliation, persistent, nickname)
                values (_roomId, _jid, SHA1( LOWER( _jid ) ), _affiliation, _persistent, _nickname);
        end if;
    end if;

	COMMIT;
end //
-- QUERY END:
