package battleship;

import java.io.IOException;
import java.util.List;

/** Sắp xếp bảng xếp hạng theo tổng điểm rồi số trận thắng (truy vấn SQL). */
public final class RankingManager {
    private final UserManager users;
    public RankingManager(UserManager users){this.users=users;}
    public List<User> getRanking()throws IOException{return users.ranking();}
}
