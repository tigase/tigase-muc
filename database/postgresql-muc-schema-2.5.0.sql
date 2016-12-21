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
	room_id bigserial,
	jid varchar(2049) not null,
	name varchar(1024),
	config text,
	creator varchar(2049) not null,
	creation_date timestamp not null,
    subject text,
    subject_creator_nick varchar(1024),
    subject_date timestamp,

	primary key ( room_id )
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_muc_rooms_jid')) is null) then
    create unique index tig_muc_rooms_jid on tig_muc_rooms ( lower(jid) );
end if;
end$$;
-- QUERY END:

-- QUERY START:
create table if not exists tig_muc_room_affiliations (
	room_id bigint not null,
    jid varchar(2049) not null,
    affiliation varchar(20) not null,

    foreign key (room_id) references tig_muc_rooms (room_id)
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_muc_room_affiliations_room_id_jid')) is null) then
    create unique index tig_muc_room_affiliations_room_id_jid on tig_muc_room_affiliations ( room_id, lower(jid) );
end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_muc_room_affiliations_room_id')) is null) then
    create index tig_muc_room_affiliations_room_id on tig_muc_room_affiliations ( room_id );
end if;
end$$;
-- QUERY END:

-- QUERY START:
create table if not exists tig_muc_room_history (
	room_jid varchar(2049) not null,
    event_type int,
    ts timestamp not null,
    sender_jid varchar(3074),
    sender_nickname varchar(1024),
	body text,
	public_event boolean,
	msg text
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_muc_room_history_room_jid')) is null) then
    create index tig_muc_room_history_room_jid on tig_muc_room_history ( lower(room_jid) );
end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_muc_room_history_room_jid_ts')) is null) then
    create index tig_muc_room_history_room_jid_ts on tig_muc_room_history ( lower(room_jid), ts );
end if;
end$$;
-- QUERY END:

-- ---------------------
-- Stored procedures
-- ---------------------

-- QUERY START:
create or replace function Tig_MUC_CreateRoom(_roomJid varchar(2049), _creatorJid varchar(2049), _creationDate timestamp, _roomName varchar(1024), _roomConfig text) returns bigint as $$
declare
    _roomId bigint;
begin
	with inserted as (
		insert into tig_muc_rooms (jid, name, config, creator, creation_date)
		    values (_roomJid, _roomName, _roomConfig, _creatorJid, _creationDate)
		returning room_id
	)
	select room_id into _roomId from inserted;
	return _roomId;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_DestroyRoom(_roomJid varchar(2049)) returns void as $$
declare
    _roomId bigint;
begin
    select room_id into _roomId from tig_muc_rooms where lower(jid) = lower(_roomJid);
    if _roomId is not null then
        delete from tig_muc_room_affiliations where room_id = _roomId;
        delete from tig_muc_rooms where room_id = _roomId;
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_GetRoomAffiliations(bigint) returns table (jid varchar(2049), affiliation varchar(20)) as $$
    select jid, affiliation from tig_muc_room_affiliations where room_id = $1
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_GetRoom(varchar(2049)) returns table (room_id bigint, creation_date timestamp, creator varchar(2049), config text, subject text, subject_creator_nick varchar(1024), subject_change_date timestamp) as $$
    select room_id, creation_date, creator, config, subject, subject_creator_nick, subject_date from tig_muc_rooms where lower(jid) = lower($1)
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_GetRoomsJids() returns table (jid varchar(2049)) as $$
    select jid from tig_muc_rooms
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_SetRoomAffiliation(_roomId bigint, _jid varchar(2049), _affiliation varchar(20)) returns void as $$
begin
    if exists( select 1 from tig_muc_room_affiliations where room_id = _roomId and lower(jid) = lower(_jid) ) then
        if _affiliation <> 'none' then
            update tig_muc_room_affiliations set affiliation = _affiliation where room_id = _roomId and lower(jid) = lower(_jid);
        else
            delete from tig_muc_room_affiliations where room_id = _roomId and lower(jid) = lower(_jid);
        end if;
    else
        if _affiliation <> 'none' then
            insert into tig_muc_room_affiliations (room_id, jid, affiliation)
                values (_roomId, _jid, _affiliation);
        end if;
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_SetRoomSubject(_roomId bigint, _subject text, _creator varchar(1024), _changeDate timestamp) returns void as $$
begin
    update tig_muc_rooms set subject = _subject, subject_creator_nick = _creator, subject_date = _changeDate where room_id = _roomId;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_SetRoomConfig(_roomJid varchar(2049), _name varchar(1024), _config text) returns void as $$
begin
    update tig_muc_rooms set name = _name, config = _config where lower(jid) = lower(_roomJid);
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_AddMessage(_roomJid varchar(2049), _ts timestamp, _senderJid varchar(3074), _senderNick varchar(1024), _body text, _publicEvent boolean, _msg text) returns void as $$
begin
    insert into tig_muc_room_history (room_jid, event_type, ts, sender_jid, sender_nickname, body, public_event, msg)
        values (_roomJid, 1, _ts, _senderJid, _senderNick, _body, _publicEvent, _msg);
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_DeleteMessages(_roomJid varchar(2049)) returns void as $$
begin
    delete from tig_muc_room_history where lower(room_jid) = lower(_roomJid);
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_GetMessages(_roomJid varchar(2049), _maxMessages int, _since timestamp) returns table(
    "sender_nickname" varchar(1024), "ts" timestamp, "sender_jid" varchar(3074), "body" text, "msg" text
) as $$
begin
    return query select t.sender_nickname, t.ts, t.sender_jid, t.body, t.msg from (
        select h.sender_nickname, h.ts, h.sender_jid, h.body, h.msg
            from tig_muc_room_history h
            where lower(h.room_jid) = lower(_roomJid)
                and (_since is null or h.ts >= _since)
            order by h.ts desc limit _maxMessages
    ) AS t order by t.ts asc;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_MAM_GetMessages(_roomJid varchar(2049), _since timestamp, _to timestamp, _nickname varchar(1024), _limit int, _offset int) returns table(
    "sender_nickname" varchar(1024), "ts" timestamp, "sender_jid" varchar(3074), "body" text, "msg" text
) as $$
begin
    return query select h.sender_nickname, h.ts, h.sender_jid, h.body, h.msg
        from tig_muc_room_history h
        where lower(h.room_jid) = lower(_roomJid)
            and (_since is null or h.ts >= _since)
            and (_to is null or h.ts <= _to)
            and (_nickname is null or h.sender_nickname = _nickname)
        order by h.ts asc
        limit _limit offset _offset;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

create or replace function Tig_MUC_MAM_GetMessagePosition(_roomJid varchar(2049), _since timestamp, _to timestamp, _nickname varchar(1024), _id_ts timestamp) returns table(
    "position" bigint
) as $$
begin
    return query select count(1) from tig_muc_room_history h
        where lower(h.room_jid) = lower(_roomJid)
            and (_since is null or h.ts >= _since)
            and (_to is null or h.ts <= _to)
            and (_nickname is null or h.sender_nickname = _nickname)
            and t.ts < _id_ts;
end;
$$ LANGUAGE 'plpgsql';

-- QUERY START:
create or replace function Tig_MUC_MAM_GetMessagesCount(_roomJid varchar(2049), _since timestamp, _to timestamp, _nickname varchar(1024)) returns table(
	"count" bigint
) as $$
begin
    return query select count(1)
        from tig_muc_room_history h
        where lower(h.room_jid) = lower(_roomJid)
            and (_since is null or h.ts >= _since)
            and (_to is null or h.ts <= _to)
            and (_nickname is null or h.sender_nickname = _nickname);
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- ---------------------
-- Converting history to new format
-- ---------------------

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.muc_history')) is not null) then
    insert into tig_muc_room_history (room_jid, event_type, ts, sender_jid, sender_nickname, body, public_event, msg)
        select room_name, event_type, to_timestamp("timestamp"/1000), sender_jid, sender_nickname, body, public_event, msg
        from muc_history;

     alter table muc_history rename to muc_history_old;
end if;
end$$;
-- QUERY END:

