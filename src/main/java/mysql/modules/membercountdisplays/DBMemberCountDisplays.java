package mysql.modules.membercountdisplays;

import mysql.DBMapCache;
import mysql.DBDataLoad;
import mysql.DBMain;

import java.sql.SQLException;
import java.util.HashMap;

public class DBMemberCountDisplays extends DBMapCache<Long, MemberCountBean> {

    private static final DBMemberCountDisplays ourInstance = new DBMemberCountDisplays();

    public static DBMemberCountDisplays getInstance() {
        return ourInstance;
    }

    private DBMemberCountDisplays() {
    }

    @Override
    protected MemberCountBean load(Long serverId) throws Exception {
        MemberCountBean memberCountBean = new MemberCountBean(
                serverId,
                getMemberCountBeanSlot(serverId)
        );

        memberCountBean.getMemberCountBeanSlots()
                .addMapAddListener(slot -> addMemberCountBeanSlot(serverId, slot))
                .addMapRemoveListener(slot -> removeMemberCountBeanSlot(serverId, slot));

        return memberCountBean;
    }

    @Override
    protected void save(MemberCountBean memberCountBean) {
    }

    private HashMap<Long, MemberCountDisplaySlot> getMemberCountBeanSlot(long serverId) {
        return new DBDataLoad<MemberCountDisplaySlot>("MemberCountDisplays", "vcId, name", "serverId = ?",
                preparedStatement -> preparedStatement.setLong(1, serverId)
        ).getHashMap(MemberCountDisplaySlot::getVoiceChannelId, resultSet -> new MemberCountDisplaySlot(serverId, resultSet.getLong(1), resultSet.getString(2)));
    }

    private void addMemberCountBeanSlot(long serverId, MemberCountDisplaySlot memberCountDisplaySlot) {
        DBMain.getInstance().asyncUpdate("REPLACE INTO MemberCountDisplays (serverId, vcId, name) VALUES (?, ?, ?);", preparedStatement -> {
            preparedStatement.setLong(1, serverId);
            preparedStatement.setLong(2, memberCountDisplaySlot.getVoiceChannelId());
            preparedStatement.setString(3, memberCountDisplaySlot.getMask());
        });
    }

    private void removeMemberCountBeanSlot(long serverId, MemberCountDisplaySlot memberCountDisplaySlot) {
        DBMain.getInstance().asyncUpdate("DELETE FROM MemberCountDisplays WHERE serverId = ? AND vcId = ?;", preparedStatement -> {
            preparedStatement.setLong(1, serverId);
            preparedStatement.setLong(2, memberCountDisplaySlot.getVoiceChannelId());
        });
    }

}
