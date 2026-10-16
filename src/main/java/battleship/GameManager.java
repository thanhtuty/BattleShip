package battleship;

/** Quy tắc kết thúc ván đấu: thắng khi bàn tàu đối phương đã chìm hết. */
public final class GameManager {
    public boolean isOver(Board board){return board.defeated();}
    public String winnerAfterAttack(String attacker,Board defenderBoard){return isOver(defenderBoard)?attacker:null;}
}
