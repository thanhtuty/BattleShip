package battleship;

import java.sql.*;
import java.util.UUID;

/** Lưu trận, người chơi, bàn cờ, lượt và kết quả bằng các giao dịch SQL. */
public final class MatchDAO {
    private final DbConfig db; private final GameEventDAO events;
    public MatchDAO(DbConfig db,GameEventDAO events){this.db=db;this.events=events;}
    public void create(String matchId,int a,int b)throws SQLException {
        try(Connection c=db.open()) {
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("INSERT INTO dbo.GameMatches(MatchId,Status,CreatedAt) VALUES(TRY_CONVERT(uniqueidentifier,?),'SETUP',SYSUTCDATETIME())")){p.setString(1,matchId);p.executeUpdate();}
                insertPlayer(c,matchId,a,1);insertPlayer(c,matchId,b,2);
                events.insert(c,matchId,null,"MATCH_CREATED","Trận được tạo");
                c.commit();
            }catch(SQLException e){c.rollback();throw e;}
        }
    }
    private static void insertPlayer(Connection c,String matchId,int userId,int order)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO dbo.MatchPlayers(MatchId,UserId,PlayerOrder,IsReady,BoardState,ShotsTaken) VALUES(TRY_CONVERT(uniqueidentifier,?),?,?,0,REPLICATE('0',36),0)")){
            p.setString(1,matchId);p.setInt(2,userId);p.setInt(3,order);p.executeUpdate();
        }
    }
    public void placeShip(String matchId,int userId,String board,int row,int col,boolean horizontal,int length)throws SQLException {
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("UPDATE dbo.MatchPlayers SET BoardState=? WHERE MatchId=TRY_CONVERT(uniqueidentifier,?) AND UserId=? AND IsReady=0")){
                    p.setString(1,board);p.setString(2,matchId);p.setInt(3,userId);
                    if(p.executeUpdate()!=1)throw new SQLException("Không thể lưu vị trí tàu");
                }
                events.insert(c,matchId,userId,"SHIP_PLACED",row+","+col+","+(horizontal?"H":"V")+","+length);
                c.commit();
            }catch(SQLException e){c.rollback();throw e;}
        }
    }
    public void clearPlacement(String matchId,int userId)throws SQLException {
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("UPDATE dbo.MatchPlayers SET BoardState=REPLICATE('0',36) WHERE MatchId=TRY_CONVERT(uniqueidentifier,?) AND UserId=? AND IsReady=0")){
                    p.setString(1,matchId);p.setInt(2,userId);
                    if(p.executeUpdate()!=1)throw new SQLException("Không thể xếp lại tàu sau khi Ready");
                }
                events.insert(c,matchId,userId,"PLACEMENT_RESET","Người chơi xóa vị trí tàu để xếp lại");
                c.commit();
            }catch(SQLException e){c.rollback();throw e;}
        }
    }
    public void ready(String matchId,int userId,String board)throws SQLException {
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("UPDATE dbo.MatchPlayers SET IsReady=1,BoardState=? WHERE MatchId=TRY_CONVERT(uniqueidentifier,?) AND UserId=? AND IsReady=0")){
                    p.setString(1,board);p.setString(2,matchId);p.setInt(3,userId);
                    if(p.executeUpdate()!=1)throw new SQLException("Không thể cập nhật trạng thái Ready của người chơi");
                }
                events.insert(c,matchId,userId,"READY","Người chơi đã xếp tàu và sẵn sàng");
                c.commit();
            }catch(SQLException e){c.rollback();throw e;}
        }
    }
    public void start(String matchId,int firstUserId,long deadline)throws SQLException {
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("""
                    UPDATE dbo.GameMatches SET Status='PLAYING',StartedAt=SYSUTCDATETIME(),
                    ActiveTurnUserId=?,TurnDeadlineAt=DATEADD(millisecond,?,SYSUTCDATETIME())
                    WHERE MatchId=TRY_CONVERT(uniqueidentifier,?) AND Status='SETUP'
                    AND (SELECT COUNT(*) FROM dbo.MatchPlayers WHERE MatchId=TRY_CONVERT(uniqueidentifier,?) AND IsReady=1)=2
                    """)){
                    p.setInt(1,firstUserId);p.setInt(2,remainingMillis(deadline));p.setString(3,matchId);p.setString(4,matchId);
                    if(p.executeUpdate()!=1)throw new SQLException("Trận chưa đủ hai người sẵn sàng");
                }
                events.insert(c,matchId,firstUserId,"GAME_STARTED","Người chơi đi trước: "+firstUserId);
                c.commit();
            }catch(SQLException e){c.rollback();throw e;}
        }
    }
    public void shot(String matchId,int shooterId,int targetId,String targetBoard,int row,int col,String result,int nextTurnId,long deadline)throws SQLException {
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("UPDATE dbo.MatchPlayers SET BoardState=?,ShotsTaken=ShotsTaken+1 WHERE MatchId=TRY_CONVERT(uniqueidentifier,?) AND UserId=?")){
                    p.setString(1,targetBoard);p.setString(2,matchId);p.setInt(3,targetId);
                    if(p.executeUpdate()!=1)throw new SQLException("Không tìm thấy bàn cờ mục tiêu");
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE dbo.GameMatches SET ActiveTurnUserId=?,TurnDeadlineAt=DATEADD(millisecond,?,SYSUTCDATETIME()) WHERE MatchId=TRY_CONVERT(uniqueidentifier,?) AND Status='PLAYING'")){
                    p.setInt(1,nextTurnId);p.setInt(2,remainingMillis(deadline));p.setString(3,matchId);if(p.executeUpdate()!=1)throw new SQLException("Trận đấu không còn hoạt động");
                }
                events.insert(c,matchId,shooterId,"ATTACK",row+","+col+","+result);
                c.commit();
            }catch(SQLException e){c.rollback();throw e;}
        }
    }
    public void turnTimeout(String matchId,int nextUserId,long deadline)throws SQLException {
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("UPDATE dbo.GameMatches SET ActiveTurnUserId=?,TurnDeadlineAt=DATEADD(millisecond,?,SYSUTCDATETIME()) WHERE MatchId=TRY_CONVERT(uniqueidentifier,?) AND Status='PLAYING'")){
                    p.setInt(1,nextUserId);p.setInt(2,remainingMillis(deadline));p.setString(3,matchId);if(p.executeUpdate()!=1)throw new SQLException("Trận đấu không còn hoạt động");
                }
                events.insert(c,matchId,nextUserId,"TURN_TIMEOUT","Hết giờ; chuyển lượt");
                c.commit();
            }catch(SQLException e){c.rollback();throw e;}
        }
    }
    public void finish(String matchId,int winnerId,int loserId,String reason,String winnerBoard,String loserBoard,Integer shooter,int row,int col,String shotResult)throws SQLException {
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("UPDATE dbo.GameMatches SET Status='FINISHED',FinishedAt=SYSUTCDATETIME(),WinnerUserId=?,EndReason=?,ActiveTurnUserId=NULL,TurnDeadlineAt=NULL WHERE MatchId=TRY_CONVERT(uniqueidentifier,?) AND Status='PLAYING'")){
                    p.setInt(1,winnerId);p.setString(2,reason);p.setString(3,matchId);
                    if(p.executeUpdate()!=1)throw new SQLException("Trận đã kết thúc hoặc không còn hoạt động");
                }
                try(PreparedStatement p=c.prepareStatement("""
                    UPDATE dbo.MatchPlayers SET BoardState=?,IsReady=1
                    WHERE MatchId=TRY_CONVERT(uniqueidentifier,?) AND UserId=?
                    """)){
                    p.setString(1,winnerBoard);p.setString(2,matchId);p.setInt(3,winnerId);p.executeUpdate();
                    p.setString(1,loserBoard);p.setString(2,matchId);p.setInt(3,loserId);p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE dbo.GameUsers SET TotalPoints=TotalPoints+10,Wins=Wins+1,TotalGames=TotalGames+1 WHERE UserId=?")){
                    p.setInt(1,winnerId);if(p.executeUpdate()!=1)throw new SQLException("Không cập nhật được thống kê người thắng");
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE dbo.GameUsers SET Losses=Losses+1,TotalGames=TotalGames+1 WHERE UserId=?")){
                    p.setInt(1,loserId);if(p.executeUpdate()!=1)throw new SQLException("Không cập nhật được thống kê người thua");
                }
                if(shooter!=null)events.insert(c,matchId,shooter,"ATTACK",row+","+col+","+shotResult);
                events.insert(c,matchId,winnerId,"GAME_RESULT","Winner="+winnerId+";Loser="+loserId+";Reason="+reason);
                c.commit();
            }catch(SQLException e){c.rollback();throw e;}
        }
    }
    private static int remainingMillis(long deadline){return (int)Math.max(0,Math.min(Integer.MAX_VALUE,deadline-System.currentTimeMillis()));}
    public void closeFinished(String matchId,String reason)throws SQLException {
        try(Connection c=db.open()){events.insert(c,matchId,null,"ROOM_CLOSED",reason);}
    }
    public void cancel(String matchId,String reason)throws SQLException {
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("UPDATE dbo.GameMatches SET Status='CANCELLED',FinishedAt=SYSUTCDATETIME(),EndReason=? WHERE MatchId=TRY_CONVERT(uniqueidentifier,?) AND Status='SETUP'")){
                    p.setString(1,reason);p.setString(2,matchId);p.executeUpdate();
                }
                events.insert(c,matchId,null,"MATCH_ENDED",reason);c.commit();
            }catch(SQLException e){c.rollback();throw e;}
        }
    }
}
