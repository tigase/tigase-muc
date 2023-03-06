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
create procedure Tig_MUC_MAM_GetMessage(roomJid varchar(2049), stableId varchar(36))
    PARAMETER STYLE JAVA
    LANGUAGE JAVA
    READS SQL DATA
    DYNAMIC RESULT SETS 1
    EXTERNAL NAME 'tigase.muc.repository.derby.StoredProcedures.tigMucGetMessage';
-- QUERY END:

-- QUERY START:
create procedure Tig_MUC_MAM_UpdateMessage(roomJid varchar(2049), stableId varchar(36), "body" varchar(32672), "msg" varchar(32672))
    PARAMETER STYLE JAVA
    LANGUAGE JAVA
    MODIFIES SQL DATA
    EXTERNAL NAME 'tigase.muc.repository.derby.StoredProcedures.tigMucMamUpdateMessage';
-- QUERY END: