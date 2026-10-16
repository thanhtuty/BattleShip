package battleship;

/** Chọn người đi trước, chuyển lượt và tạo deadline 15 giây. */
public final class TurnManager {
    private TurnManager(){}
    public static String chooseFirst(String a,String b){return java.util.concurrent.ThreadLocalRandom.current().nextBoolean()?a:b;}
    public static String next(String current,String a,String b){return current.equals(a)?b:a;}
    /** Khi bắn trúng (kể cả đánh chìm một tàu), người bắn giữ lượt; bắn trượt thì chuyển lượt. */
    public static String afterShot(String shooter,String opponent,String result){return "MISS".equals(result)?opponent:shooter;}
    public static long deadline(){return System.currentTimeMillis()+Server.TURN_MS;}
}
