package battleship;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/** Service façade: chuyển nghiệp vụ tài khoản, trận, xếp hạng và lịch sử xuống các DAO. */
public final class Store implements AutoCloseable {
    public static final int WIN_POINTS=10;
    private final DbConfig db;
    private final UserDAO userDAO;
    private final UserManager userManager;
    private final GameEventDAO eventDAO;
    private final MatchManager matchManager;
    private final RankingManager rankingManager;
    private final ResultManager resultManager;
    private final HistoryManager historyManager;

    public Store(DbConfig db)throws IOException {
        this.db=db; userDAO=new UserDAO(db); userManager=new UserManager(userDAO);
        eventDAO=new GameEventDAO(db); matchManager=new MatchManager(new MatchDAO(db,eventDAO));
        rankingManager=new RankingManager(userManager); resultManager=new ResultManager(matchManager); historyManager=new HistoryManager(eventDAO);
        verifyConnection();
    }
    public void verifyConnection()throws IOException {
        try(var c=db.open();var p=c.prepareStatement("SELECT DB_NAME()");var r=p.executeQuery()) {
            if(!r.next()||!"BattleShipDB".equalsIgnoreCase(r.getString(1)))
                throw new IOException("BATTLESHIP_DB_URL phải kết nối tới cơ sở dữ liệu BattleShipDB");
        }catch(SQLException e){throw UserManager.dbError(e);}
    }
    public void register(String name,String password)throws IOException{userManager.register(name,password);}
    public boolean authenticate(String name,String password)throws IOException{return userManager.authenticate(name,password);}
    public User account(String name)throws IOException{return userManager.find(name);}
    public List<User> ranking()throws IOException{return rankingManager.getRanking();}
    public String history(String name)throws IOException{return historyManager.getHistory(name);}
    public void createMatch(String id,int a,int b)throws IOException{matchManager.create(id,a,b);}
    public void placeShip(String id,int userId,String board,int row,int col,boolean horizontal,int length)throws IOException{
        matchManager.placeShip(id,userId,board,row,col,horizontal,length);
    }
    public void clearPlacement(String id,int userId)throws IOException{matchManager.clearPlacement(id,userId);}
    public void markReady(String id,int userId,String board)throws IOException{matchManager.ready(id,userId,board);}
    public void startMatch(String id,int firstUser,long deadline)throws IOException{matchManager.start(id,firstUser,deadline);}
    public void recordShot(String id,int shooter,int target,String board,int row,int col,String result,int nextTurn,long deadline)throws IOException{
        matchManager.shot(id,shooter,target,board,row,col,result,nextTurn,deadline);
    }
    public void recordWinningShot(String id,int winner,int loser,String winnerBoard,String loserBoard,
                                  int shooter,int row,int col,String result)throws IOException{
        resultManager.saveWinShot(id,winner,loser,winnerBoard,loserBoard,shooter,row,col,result);
    }
    public void recordForfeit(String id,int winner,int loser,String reason,String winnerBoard,String loserBoard)throws IOException{
        resultManager.saveForfeit(id,winner,loser,reason,winnerBoard,loserBoard);
    }
    public void timeout(String id,int nextTurn,long deadline)throws IOException{matchManager.timeout(id,nextTurn,deadline);}
    public void closeMatch(String id,String phase,String reason)throws IOException{matchManager.close(id,phase,reason);}
    @Override public void close(){}
}
