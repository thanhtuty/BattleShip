package battleship;

/** Thông tin tài khoản dùng trong Lobby, Ranking và kết quả. */
public record User(int id,String username,int totalPoints,int wins,int losses,int totalGames) {}
