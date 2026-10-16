package battleship;

/** Chọn người đi trước, chuyển lượt và tạo deadline 15 giây. */
public final class TurnManager {
    private TurnManager(){}
    public static String chooseFirst(String a,String b){return java.util.concurrent.ThreadLocalRandom.current().nextBoolean()?a:b;}
    public static String next(String current,String a,String b){return current.equals(a)?b:a;}
    public static long deadline(){return System.currentTimeMillis()+Server.TURN_MS;}
}
