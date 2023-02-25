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
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- QUERY END:

-- QUERY START:
do $$
begin
    if not exists (select 1 from information_schema.columns where table_catalog = current_database() and table_schema = 'public' and table_name = 'tig_muc_room_history' and column_name = 'stable_id') then
        alter table tig_muc_room_history add column stable_id uuid;

        update tig_muc_room_history set stable_id = uuid_generate_v4() where stable_id is null;

        alter table tig_muc_room_history alter column stable_id set not null;
    end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_muc_room_history_room_stable_id_index')) is null) then
    create index tig_muc_room_history_room_stable_id_index on tig_muc_room_history ( lower(room_jid), stable_id );
end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MUC_AddMessage') and pg_get_function_arguments(oid) = '_roomJid character varying, _ts timestamp with time zone, _senderJid character varying, _senderNick character varying, _body text, _publicEvent boolean, _msg text') then
    drop function Tig_MA_AddMessage(_roomJid character varying, _ts timestamp with time zone, _senderJid character varying, _senderNick character varying, _body text, _publicEvent boolean, _msg text);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_AddMessage(_roomJid varchar(2049), _stableId varchar(36), _ts timestamp with time zone, _senderJid varchar(3074), _senderNick varchar(1024), _body text, _publicEvent boolean, _msg text) returns void as $$
begin
    insert into tig_muc_room_history (room_jid, stable_id, event_type, ts, sender_jid, sender_nickname, body, public_event, msg)
        values (_roomJid, uuid(_stableId), 1, _ts, _senderJid, _senderNick, _body, _publicEvent, _msg);
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_MAM_UpdateMessage(_roomJid varchar(2049), _stableId varchar(36), _body text, _msg text) returns void as $$
begin
    update tig_muc_room_history
    set body = _body, msg = _msg
    where lower(room_jid) = lower( _roomJid )
      and stable_id = uuid(_stableId);
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_MAM_GetMessage(_roomJid varchar(2049), _stableId varchar(36)) returns table(
    "sender_nickname" varchar(1024), "stable_id" varchar(36), "ts" timestamp with time zone, "sender_jid" varchar(3074), "body" text, "msg" text
) as $$
begin
    return query select h.sender_nickname, cast(h.stable_id as varchar(36)) as stable_id, h.ts, h.sender_jid, h.body, h.msg
            from tig_muc_room_history h
            where lower(h.room_jid) = lower(_roomJid)
                and h.stable_id = uuid(_stableId)
            order by h.ts desc limit 1;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MUC_GetMessages') and pg_get_function_arguments(oid) = '_roomjid character varying, _maxmessages integer, _since timestamp with time zone') then
    drop function Tig_MUC_GetMessages(_roomjid character varying, _maxmessages integer, _since timestamp with time zone);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_GetMessages(_roomJid varchar(2049), _maxMessages int, _since timestamp with time zone) returns table(
    "sender_nickname" varchar(1024), "stable_id" varchar(36), "ts" timestamp with time zone, "sender_jid" varchar(3074), "body" text, "msg" text
) as $$
begin
    return query select t.sender_nickname, cast(t.stable_id as varchar(36)) as stable_id, t.ts, t.sender_jid, t.body, t.msg from (
        select h.sender_nickname, h.stable_id, h.ts, h.sender_jid, h.body, h.msg
            from tig_muc_room_history h
            where lower(h.room_jid) = lower(_roomJid)
                and (_since is null or h.ts >= _since)
            order by h.ts desc limit _maxMessages
    ) AS t order by t.ts asc;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MUC_MAM_GetMessages') and pg_get_function_arguments(oid) = '_roomjid character varying, _since timestamp with time zone, _to timestamp with time zone, _nickname character varying, _limit integer, _offset integer') then
    drop function Tig_MUC_MAM_GetMessages(_roomjid character varying, _since timestamp with time zone, _to timestamp with time zone, _nickname character varying, _limit integer, _offset integer);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_MAM_GetMessages(_roomJid varchar(2049), _since timestamp with time zone, _to timestamp with time zone, _nickname varchar(1024), _limit int, _offset int) returns table(
    "sender_nickname" varchar(1024), "stable_id" varchar(36), "ts" timestamp with time zone, "sender_jid" varchar(3074), "body" text, "msg" text
) as $$
begin
    return query select h.sender_nickname, cast(h.stable_id as varchar(36)) as stable_id, h.ts, h.sender_jid, h.body, h.msg
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

-- QUERY START:
create or replace function Tig_MUC_MAM_GetMessagePosition(_roomJid varchar(2049), _since timestamp with time zone, _to timestamp with time zone, _nickname varchar(1024), _stableId varchar(36)) returns table(
    "position" bigint
) as $$
begin
    return query select count(1) from tig_muc_room_history h
        where lower(h.room_jid) = lower(_roomJid)
            and (_since is null or h.ts >= _since)
            and (_to is null or h.ts <= _to)
            and (_nickname is null or h.sender_nickname = _nickname)
            and h.ts < (
                select ts
                from tig_muc_room_history h1
                where lower(h1.room_jid) = lower( _roomJid )
                    and h1.stable_id = uuid(_stableId)
            );
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END: