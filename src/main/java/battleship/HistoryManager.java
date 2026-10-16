package battleship;

import java.io.IOException;
import java.sql.SQLException;

/** Tra cứu kết quả trận của một người chơi. */
public final class HistoryManager {
    private final GameEventDAO events;
    public HistoryManager(GameEventDAO events){this.events=events;}
    public String getHistory(String username)throws IOException {
        try{return events.history(username);}catch(SQLException e){throw UserManager.dbError(e);}
    }
}
