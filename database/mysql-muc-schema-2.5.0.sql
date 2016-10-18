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
create table if not exists tig_muc_rooms (
	room_id bigint not null auto_increment,
	jid varchar(2049) not null,
	jid_sha1 char(40) not null,
	name varchar(1024),
	config text,
	creator varchar(2049) not null,
	creation_date timestamp not null,
    subject text,
    subject_creator_nick varchar(1024),
    subject_date timestamp,

	primary key ( room_id ),
	index using hash ( jid(255) ),
	unique index using hash ( jid_sha1(40) )
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- QUERY START:
create table if not exists tig_muc_room_affiliations (
	room_id bigint not null,
    jid varchar(2049) not null,
    jid_sha1 char(40) not null,
    affiliation varchar(20) not null,

	index using hash ( room_id ),
	unique index using hash ( room_id, jid_sha1(40) ),
	constraint
		foreign key ( room_id )
		references tig_muc_rooms ( room_id )
		match full
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- QUERY START:
create table if not exists tig_muc_room_history (
	room_jid varchar(2049) not null,
	room_jid_sha1 char(40) not null,
    event_type int,
    ts datetime not null,
    sender_jid varchar(3074),
    sender_nickname varchar(1024),
	body text,
	public_event boolean,
	msg text,

	index using hash ( room_jid_sha1 ),
	index using hash ( room_jid_sha1, ts ),
	index using hash ( room_jid(255) )
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- ---------------------
-- Stored procedures
-- ---------------------

-- QUERY START:
drop procedure if exists Tig_MUC_CreateRoom;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_DestroyRoom;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_GetRoomAffiliations;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_GetRoom;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_GetRoomsJids;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_SetRoomAffiliation;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_SetRoomSubject;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_SetRoomConfig;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_AddMessage;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_DeleteMessages;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_GetMessages;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigExecuteIf;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure Tig_MUC_CreateRoom(_roomJid varchar(2049), _creatorJid varchar(2049), _creationDate timestamp, _roomName varchar(1024), _roomConfig text)
begin
	declare _roomId bigint;
	declare _roomJidSha1 char(40);

	select SHA1( LOWER( _roomJid ) ) into _roomJidSha1;
	insert into tig_muc_rooms (jid, jid_sha1, name, config, creator, creation_date)
		values (_roomJid, _roomJidSha1, _roomName, _roomConfig, _creatorJid, _creationDate);
	select LAST_INSERT_ID() into _roomId;

	select _roomId as room_id;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_DestroyRoom(_roomJid varchar(2049))
begin
	declare _roomId bigint;

    select room_id into _roomId from tig_muc_rooms where jid_sha1 = SHA1( LOWER( _roomJid ) );
    if _roomId is not null then
        delete from tig_muc_room_affiliations where room_id = _roomId;
        delete from tig_muc_rooms where room_id = _roomId;
    end if;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_GetRoomAffiliations(_roomId bigint)
begin
    select jid, affiliation from tig_muc_room_affiliations where room_id = _roomId;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_GetRoom(_roomJid varchar(2049))
begin
    select room_id, creation_date, creator, config, subject, subject_creator_nick, subject_date
    from tig_muc_rooms
    where jid_sha1 = SHA1( LOWER( _roomJid ) );
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_GetRoomsJids()
begin
    select jid from tig_muc_rooms;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_SetRoomAffiliation(_roomId bigint, _jid varchar(2049), _affiliation varchar(20))
begin
    declare _jidSha1 char(40);

	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
	END;

	START TRANSACTION;

    select SHA1( LOWER( _jid ) ) into _jidSha1;
    if exists( select 1 from tig_muc_room_affiliations where room_id = _roomId and jid_sha1 = _jidSha1 ) then
        if _affiliation <> 'none' then
            update tig_muc_room_affiliations set affiliation = _affiliation where room_id = _roomId and jid_sha1 = _jidSha1;
        else
            delete from tig_muc_room_affiliations where room_id = _roomId and jid_sha1 = _jidSha1;
        end if;
    else
        if _affiliation <> 'none' then
            insert into tig_muc_room_affiliations (room_id, jid, jid_sha1, affiliation)
                values (_roomId, _jid, _jidSha1, _affiliation);
        end if;
    end if;

	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_SetRoomSubject(_roomId bigint, _subject text, _creator varchar(1024), _changeDate datetime)
begin
    update tig_muc_rooms set subject = _subject, subject_creator_nick = _creator, subject_date = _changeDate where room_id = _roomId;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_SetRoomConfig(_roomJid varchar(2049), _name varchar(1024), _config text)
begin
    update tig_muc_rooms set name = _name, config = _config where jid_sha1 = SHA1( LOWER( _roomJid ) );
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_AddMessage(_roomJid varchar(2049), _ts datetime, _senderJid varchar(3074), _senderNick varchar(1024), _body text, _publicEvent boolean, _msg text)
begin
	declare _roomJidSha1 char(40);

    select SHA1( LOWER( _roomJid ) ) into _roomJidSha1;
    insert into tig_muc_room_history (room_jid, room_jid_sha1, event_type, ts, sender_jid, sender_nickname, body, public_event, msg)
        values (_roomJid, _roomJidSha1, 1, _ts, _senderJid, _senderNick, _body, _publicEvent, _msg);
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_DeleteMessages(_roomJid varchar(2049))
begin
    delete from tig_muc_room_history where room_jid_sha1 = SHA1( LOWER( _roomJid ) );
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_GetMessages(_roomJid varchar(2049), _maxMessages int, _since datetime)
begin
    declare _roomJidSha1 char(40);

    select SHA1( LOWER( _roomJid ) ) into _roomJidSha1;

    select t.sender_nickname, t.ts, t.sender_jid, t.body, t.msg from (
        select h.sender_nickname, h.ts, h.sender_jid, h.body, h.msg
            from tig_muc_room_history h
            where h.room_jid_sha1 = _roomJidSha1
                and (_since is null or h.ts >= _since)
            order by h.ts desc limit _maxMessages
    ) AS t order by t.ts asc;
end //
-- QUERY END:

-- QUERY START:
create procedure TigExecuteIf(cond int, query text)
begin
set @s = (select if (
        cond < 1,
'select 1',
query
));

prepare stmt from @s;
execute stmt;
deallocate prepare stmt;
end //
-- QUERY END:

delimiter ;

-- ---------------------
-- Converting history to new format
-- ---------------------

-- QUERY START:
call TigExecuteIf((select count(1) from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'muc_history'), '
    insert into tig_muc_room_history (room_jid, room_jid_sha1, event_type, ts, sender_jid, sender_nickname, body, public_event, msg)
        select room_name, SHA1( LOWER(room_name) ), event_type, from_unixtime("timestamp"), sender_jid, sender_nickname, body, public_event, msg
        from muc_history;
    rename muc_history to muc_history_old;');
-- QUERY END:

