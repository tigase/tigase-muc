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
drop function if exists Tig_MUC_UuidToOrdered;
-- QUERY END:

-- QUERY START:
drop function if exists Tig_MUC_OrderedToUuid;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_Upgrade;
-- QUERY END:

delimiter //

-- QUERY START:
create function Tig_MUC_UuidToOrdered(_uuid varchar(36)) returns binary(16) deterministic
begin
    if _uuid is null then
        return null;
    end if;
    return unhex(concat(substr(_uuid, 15, 4), substr(_uuid, 10, 4), substr(_uuid, 1, 8), substr(_uuid, 20, 4), substr(_uuid, 25)));
end //
-- QUERY END:

-- QUERY START:
create function Tig_MUC_OrderedToUuid(_uuid binary(16)) returns varchar(36) deterministic
begin
    declare hexed varchar(36);
    if _uuid is null then
        return null;
    end if;

    select hex(_uuid) into hexed;

    return concat(substr(hexed, 9, 8), '-', substr(hexed, 5, 4), '-', substr(hexed, 1, 4), '-', substr(hexed, 17, 4), '-', substr(hexed, 21));
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_Upgrade()
begin
    if not exists (select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_muc_room_history' and column_name = 'stable_id') then
        alter table tig_muc_room_history add column stable_id binary(16);

        update tig_muc_room_history set stable_id = Tig_MUC_UuidToOrdered(UUID()) where stable_id is null;

        alter table tig_muc_room_history modify stable_id binary(16) not null;

        alter table tig_muc_room_history modify ts timestamp(6) default CURRENT_TIMESTAMP(6) not null;
    end if;
    if not exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_muc_room_history' and INDEX_NAME = 'tig_muc_room_history_room_stable_id_index') then
        create unique index tig_muc_room_history_room_stable_id_index on tig_muc_room_history (room_jid_sha1, stable_id);
    end if;
end //
-- QUERY END:

delimiter ;

-- QUERY START:
call Tig_MUC_Upgrade;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_Upgrade;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_AddMessage;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_MAM_UpdateMessage;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_MAM_GetMessage;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_GetMessages;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_MAM_GetMessages;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MUC_MAM_GetMessagePosition;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure Tig_MUC_AddMessage(_roomJid varchar(2049), _stableId varchar(36), _ts timestamp(6), _senderJid varchar(3074), _senderNick varchar(1024), _body text charset utf8mb4 collate utf8mb4_bin, _publicEvent boolean, _msg text charset utf8mb4 collate utf8mb4_bin)
begin
    insert into tig_muc_room_history (room_jid, room_jid_sha1, stable_id, event_type, ts, sender_jid, sender_nickname, body, public_event, msg)
    values (_roomJid, SHA1( LOWER( _roomJid ) ), Tig_MUC_UuidToOrdered(_stableId), 1, _ts, _senderJid, _senderNick, _body, _publicEvent, _msg);
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_MAM_UpdateMessage(_roomJid varchar(2049), _stableId varchar(36), _body text charset utf8mb4 collate utf8mb4_bin, _msg text charset utf8mb4 collate utf8mb4_bin)
begin
    update tig_muc_room_history
    set body = _body, msg = _msg
    where room_jid_sha1 = SHA1( LOWER( _roomJid ) )
      and stable_id = Tig_MUC_UuidToOrdered(_stableId);
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_MAM_GetMessage(_roomJid varchar(2049), _stableId varchar(36))
begin
    select h.sender_nickname, Tig_MUC_OrderedToUuid(h.stable_id) as stable_id, h.ts, h.sender_jid, h.body, h.msg
    from tig_muc_room_history h
    where h.room_jid_sha1 = SHA1( LOWER( _roomJid ) )
      and h.stable_id = Tig_MUC_UuidToOrdered(_stableId);
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_GetMessages(_roomJid varchar(2049), _maxMessages int, _since timestamp(6))
begin
    select t.sender_nickname, Tig_MUC_OrderedToUuid(t.stable_id) as stable_id, t.ts, t.sender_jid, t.body, t.msg from (
                                                                                                                          select h.sender_nickname, h.stable_id, h.ts, h.sender_jid, h.body, h.msg
                                                                                                                          from tig_muc_room_history h
                                                                                                                          where h.room_jid_sha1 = SHA1( LOWER( _roomJid ) )
                                                                                                                            and (_since is null or h.ts >= _since)
                                                                                                                          order by h.ts desc limit _maxMessages
                                                                                                                      ) AS t order by t.ts asc;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_MAM_GetMessages(_roomJid varchar(2049), _since timestamp(6), _to timestamp(6), _nickname varchar(1024), _limit int, _offset int)
begin
    select h.sender_nickname, Tig_MUC_OrderedToUuid(h.stable_id) as stable_id, h.ts, h.sender_jid, h.body, h.msg
    from tig_muc_room_history h
    where h.room_jid_sha1 = SHA1( LOWER( _roomJid ) )
      and (_since is null or h.ts >= _since)
      and (_to is null or h.ts <= _to)
      and (_nickname is null or h.sender_nickname = _nickname)
    order by h.ts asc limit _limit offset _offset;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_MAM_GetMessagePosition(_roomJid varchar(2049), _since timestamp(6), _to timestamp(6), _nickname varchar(1024), _stableId varchar(36))
begin
    select count(1) from (
                                 select h.sender_nickname, h.ts, h.sender_jid, h.body, h.msg
                                 from tig_muc_room_history h
                                 where h.room_jid_sha1 = SHA1( LOWER( _roomJid ) )
                                   and (_since is null or h.ts >= _since)
                                   and (_to is null or h.ts <= _to)
                                   and (_nickname is null or h.sender_nickname = _nickname)
                             ) as t
    where t.ts < (
        select ts
        from tig_muc_room_history h1
        where h1.room_jid_sha1 = SHA1( LOWER( _roomJid ) )
          and h1.stable_id = Tig_MUC_UuidToOrdered(_stableId)
    );
end //
-- QUERY END: