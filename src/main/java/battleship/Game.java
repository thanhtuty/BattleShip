package battleship;

import java.util.*;

/** Trạng thái một ván đấu trên server, bao gồm hai bàn cờ và cờ Ready. */
public final class Game {
    public final String id=UUID.randomUUID().toString();
    public final String playerA,playerB;
    public String status="SETUP",activeTurn="";
    public long deadline=System.currentTimeMillis()+Server.SETUP_MS;
    private final Map<String,Board> boards=new HashMap<>();
    private final Set<String> readyPlayers=new HashSet<>();
    public Game(String playerA,String playerB){this.playerA=playerA;this.playerB=playerB;}
    public String other(String user){return user.equals(playerA)?playerB:playerA;}
    public Board board(String user){return boards.get(user);}
    public Board createBoard(String user){return boards.computeIfAbsent(user,k->new Board());}
    public void setBoard(String user,Board board){boards.put(user,board);}
    public void clearBoard(String user){boards.remove(user);readyPlayers.remove(user);}
    public void markReady(String user){readyPlayers.add(user);}
    public boolean isReady(String user){return readyPlayers.contains(user);}
    public int readyCount(){return readyPlayers.size();}
    public boolean isPlayer(String user){return playerA.equals(user)||playerB.equals(user);}
}
