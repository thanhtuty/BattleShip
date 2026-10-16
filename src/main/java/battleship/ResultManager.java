package battleship;

import java.io.IOException;

/** Cập nhật kết quả cuối trận và thống kê hai người qua MatchManager/DAO. */
public final class ResultManager {
    private final MatchManager matches;
    public ResultManager(MatchManager matches){this.matches=matches;}
    public void saveWinShot(String id,int winner,int loser,String winnerBoard,String loserBoard,
                            int shooter,int row,int col,String result)throws IOException{
        matches.finish(id,winner,loser,"ALL_SUNK",winnerBoard,loserBoard,shooter,row,col,result);
    }
    public void saveForfeit(String id,int winner,int loser,String reason,String winnerBoard,String loserBoard)throws IOException{
        matches.finish(id,winner,loser,reason,winnerBoard,loserBoard,null,-1,-1,"");
    }
}
