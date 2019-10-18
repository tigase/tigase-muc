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
CREATE procedure Tig_MUC_UPGRADE()
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.muc.repository.derby.StoredProcedures.migrateFromOldSchema';
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_GetRoomAvatar(id BIGINT)
    PARAMETER STYLE JAVA
    LANGUAGE JAVA
    READS SQL DATA
    DYNAMIC RESULT SETS 1
    EXTERNAL NAME 'tigase.muc.repository.derby.StoredProcedures.tigMucGetAvatar';
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_SetRoomAvatar(roomId bigint, avatar varchar(32672), avatarHash varchar(22))
    PARAMETER STYLE JAVA
    LANGUAGE JAVA
    MODIFIES SQL DATA
    EXTERNAL NAME 'tigase.muc.repository.derby.StoredProcedures.tigMucSetAvatar';
-- QUERY END:

-- QUERY START:
call Tig_MUC_UPGRADE();
-- QUERY END:

-- QUERY START:
drop procedure Tig_MUC_UPGRADE;
-- QUERY END:
