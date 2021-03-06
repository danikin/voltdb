/* This file is part of VoltDB.
 * Copyright (C) 2008-2017 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.procedures;

import org.voltdb.*;
import java.util.Calendar;
import java.util.TimeZone;

@ProcInfo(
    singlePartition = false
)

public class RecordHitMulti extends VoltProcedure {
    // check if player already archived today
    public final SQLStmt checkArchived = new SQLStmt("select playerId from visit_archived where playerId = ? and gameId = ? and socialId = ? and clientId = ? and visitTime >= ? and visitTime <= ?;");

    // get player visitTime from unarchived (if they are in there)
    public final SQLStmt checkUnArchived = new SQLStmt("select visitTime from visit_unarchived where playerId = ? and gameId = ? and socialId = ? and clientId = ? and visitTime >= ? and visitTime <= ?;");

    // update player visitTime in unarchived
    public final SQLStmt updateUnArchived = new SQLStmt("update visit_unarchived set visitTime = ? where playerId = ? and gameId = ? and socialId = ? and clientId = ? and visitTime >= ? and visitTime <= ?;");

    // insert player into unarchived
    public final SQLStmt insertUnArchived = new SQLStmt("insert into visit_unarchived (playerId, gameId, socialId, clientId, visitTime) values (?, ?, ?, ?, ?);");

    private final TimeZone tz = TimeZone.getTimeZone("US/Eastern");
    private final Calendar calTemp = Calendar.getInstance(tz);

    public VoltTable[] run(
            long playerId,
            long gameId,
            long socialId,
            long clientId,
            long visitTimeMillis,
            long called_time_milliseconds
    ) {
        calTemp.setTimeInMillis(visitTimeMillis);
        calTemp.set(Calendar.HOUR_OF_DAY, 0);
        calTemp.set(Calendar.MINUTE, 0);
        calTemp.set(Calendar.SECOND, 0);
        calTemp.set(Calendar.MILLISECOND, 0);

        long visitTimeStartOfDayMillis = calTemp.getTimeInMillis();

        calTemp.set(Calendar.HOUR_OF_DAY, 23);
        calTemp.set(Calendar.MINUTE, 59);
        calTemp.set(Calendar.SECOND, 59);
        calTemp.set(Calendar.MILLISECOND, 999);

        long visitTimeEndOfDayMillis = calTemp.getTimeInMillis();

        voltQueueSQL(checkArchived, playerId, gameId, socialId, clientId, visitTimeStartOfDayMillis, visitTimeEndOfDayMillis);
        voltQueueSQL(checkUnArchived, playerId, gameId, socialId, clientId, visitTimeStartOfDayMillis, visitTimeEndOfDayMillis);

        VoltTable results1[] = voltExecuteSQL();
        int sqlStatements = 0;

        if (results1[0].getRowCount() == 0) {
            // user is not yet archived

            if (results1[1].getRowCount() == 1) {
                // update unarchived row if older timestamp
                if (results1[1].fetchRow(0).getLong(0) > visitTimeMillis) {
                    voltQueueSQL(updateUnArchived, visitTimeMillis, playerId, gameId, socialId, clientId, visitTimeStartOfDayMillis, visitTimeEndOfDayMillis);
                    sqlStatements++;
                }
            } else {
                // insert unarchived row
                voltQueueSQL(insertUnArchived, playerId, gameId, socialId, clientId, visitTimeMillis);
                sqlStatements++;
            }

            if (sqlStatements > 0) {
                VoltTable results2[] = voltExecuteSQL(true);
            }
        }

        VoltTable vtLoad = new VoltTable(new VoltTable.ColumnInfo("called_time_milliseconds",VoltType.BIGINT));
        Object row[] = new Object[1];
        row[0] = called_time_milliseconds;
        vtLoad.addRow(row);

        final VoltTable[] vtReturn = {vtLoad};

        return vtReturn;
    }
}
