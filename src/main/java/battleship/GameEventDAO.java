package battleship;

import java.sql.*;

/** Lưu nhật ký sự kiện và đọc lịch sử đấu từ SQL Server. */
public final class GameEventDAO {
    private final DbConfig db;
    public GameEventDAO(DbConfig db){this.db=db;}
    void insert(Connection c,String matchId,Integer userId,String type,String detail)throws SQLException {
        String sql="INSERT INTO dbo.GameEvents(MatchId,UserId,EventType,Details,CreatedAt) VALUES(TRY_CONVERT(uniqueidentifier,?),?,?,?,SYSUTCDATETIME())";
        try(PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,matchId);
            if(userId==null)p.setNull(2,Types.INTEGER);else p.setInt(2,userId);
            p.setString(3,type);p.setString(4,detail);p.executeUpdate();
        }
    }
    public String history(String username)throws SQLException {
        String sql="""
            SELECT TOP (30) m.CreatedAt,m.FinishedAt,w.Username AS WinnerName,l.Username AS LoserName,m.EndReason,m.MatchId
            FROM dbo.GameMatches m
            JOIN dbo.MatchPlayers wp ON wp.MatchId=m.MatchId AND wp.UserId=m.WinnerUserId
            JOIN dbo.GameUsers w ON w.UserId=wp.UserId
            JOIN dbo.MatchPlayers lp ON lp.MatchId=m.MatchId AND lp.UserId<>wp.UserId
            JOIN dbo.GameUsers l ON l.UserId=lp.UserId
            WHERE m.Status='FINISHED' AND (w.Username=? OR l.Username=?)
            ORDER BY m.FinishedAt DESC
            """;
        StringBuilder out=new StringBuilder("Bắt đầu UTC | Kết thúc UTC | Thắng | Thua | Lý do\n");
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,username);p.setString(2,username);
            try(ResultSet r=p.executeQuery()) {
                while(r.next())out.append(r.getTimestamp("CreatedAt")).append(" | ").append(r.getTimestamp("FinishedAt")).append(" | ")
                    .append(r.getString("WinnerName")).append(" | ").append(r.getString("LoserName")).append(" | ")
                    .append(r.getString("EndReason")).append('\n');
            }
        }
        return out.toString();
    }
}
