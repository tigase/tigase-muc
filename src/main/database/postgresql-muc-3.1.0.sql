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
do $$
begin
    if not exists (select 1 from information_schema.columns where table_catalog = current_database() and table_schema = 'public' and table_name = 'tig_muc_room_affiliations' and column_name = 'persistent') then
        alter table tig_muc_room_affiliations
            add persistent int not null default 0;
    end if;

    if not exists (select 1 from information_schema.columns where table_catalog = current_database() and table_schema = 'public' and table_name = 'tig_muc_room_affiliations' and column_name = 'nickname') then
        alter table tig_muc_room_affiliations
            add nickname varchar(1024);
    end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = 'tig_muc_getroomaffiliations' and pg_get_function_result(oid) = 'TABLE(jid character varying, affiliation character varying)') then
    drop function Tig_MUC_GetRoomAffiliations(bigint);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_GetRoomAffiliations(bigint) returns table (jid varchar(2049), affiliation varchar(20), persistent int, _nickname varchar(1024)) as $$
    select jid, affiliation, persistent, nickname from tig_muc_room_affiliations where room_id = $1
$$ LANGUAGE SQL;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = 'tig_muc_setroomaffiliation' and pg_get_function_arguments(oid) = '_roomid bigint, _jid character varying, _affiliation character varying') then
    drop function Tig_MUC_SetRoomAffiliation(bigint,varchar,varchar);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MUC_SetRoomAffiliation(_roomId bigint, _jid varchar(2049), _affiliation varchar(20), _persistent int, _nickname varchar(1024)) returns void as $$
begin
    if exists( select 1 from tig_muc_room_affiliations where room_id = _roomId and lower(jid) = lower(_jid) ) then
        if _affiliation <> 'none' then
            update tig_muc_room_affiliations set affiliation = _affiliation, persistent = _persistent, nickname = _nickname where room_id = _roomId and lower(jid) = lower(_jid);
        else
            delete from tig_muc_room_affiliations where room_id = _roomId and lower(jid) = lower(_jid);
        end if;
    else
        if _affiliation <> 'none' then
            insert into tig_muc_room_affiliations (room_id, jid, affiliation, persistent, nickname)
                values (_roomId, _jid, _affiliation, _persistent, _nickname);
        end if;
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:
