package battleship;

import java.io.IOException;
import java.sql.SQLException;

/** Nghiệp vụ lưu vòng đời trận; luật lượt vẫn do server kiểm tra. */
public final class MatchManager {
    private final MatchDAO dao;
    public MatchManager(MatchDAO dao){this.dao=dao;}
    public void create(String id,int a,int b)throws IOException {run(()->dao.create(id,a,b));}
    public void placeShip(String id,int userId,String board,int row,int col,boolean horizontal,int length)throws IOException {
        run(()->dao.placeShip(id,userId,board,row,col,horizontal,length));
    }
    public void clearPlacement(String id,int userId)throws IOException {run(()->dao.clearPlacement(id,userId));}
    public void ready(String id,int userId,String board)throws IOException {run(()->dao.ready(id,userId,board));}
    public void start(String id,int firstUser,long deadline)throws IOException {run(()->dao.start(id,firstUser,deadline));}
    public void shot(String id,int shooter,int target,String board,int row,int col,String result,int nextTurn,long deadline)throws IOException {
        run(()->dao.shot(id,shooter,target,board,row,col,result,nextTurn,deadline));
    }
    public void timeout(String id,int next,long deadline)throws IOException {run(()->dao.turnTimeout(id,next,deadline));}
    public void finish(String id,int winner,int loser,String reason,String winnerBoard,String loserBoard,
                       Integer shooter,int row,int col,String result)throws IOException {
        run(()->dao.finish(id,winner,loser,reason,winnerBoard,loserBoard,shooter,row,col,result));
    }
    public void close(String id,String phase,String reason)throws IOException {
        run(()->{if("FINISHED".equals(phase))dao.closeFinished(id,reason);else dao.cancel(id,reason);});
    }
    private static void run(SqlAction action)throws IOException {try{action.run();}catch(SQLException e){throw UserManager.dbError(e);}}
    @FunctionalInterface private interface SqlAction {void run()throws SQLException;}
}
