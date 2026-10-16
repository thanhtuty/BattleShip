package battleship;

import java.sql.*;
import java.util.*;

/** Truy vấn tài khoản qua JDBC PreparedStatement. */
public final class UserDAO {
    public record Credential(User user,byte[] salt,byte[] passwordHash) {}
    private final DbConfig db;
    public UserDAO(DbConfig db){this.db=db;}
    public void insert(String username,byte[] salt,byte[] hash) throws SQLException {
        String sql="INSERT INTO dbo.GameUsers(Username,PasswordSalt,PasswordHash) VALUES(?,?,?)";
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,username);p.setBytes(2,salt);p.setBytes(3,hash);p.executeUpdate();
        }
    }
    public Credential findCredential(String username) throws SQLException {
        String sql="SELECT UserId,Username,PasswordSalt,PasswordHash,TotalPoints,Wins,Losses,TotalGames FROM dbo.GameUsers WHERE Username=?";
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,username);
            try(ResultSet r=p.executeQuery()){return r.next()?readCredential(r):null;}
        }
    }
    public User find(String username) throws SQLException {
        Credential credential=findCredential(username);return credential==null?null:credential.user();
    }
    public List<User> ranking() throws SQLException {
        List<User> result=new ArrayList<>();
        String sql="SELECT TOP (100) UserId,Username,PasswordSalt,PasswordHash,TotalPoints,Wins,Losses,TotalGames FROM dbo.GameUsers ORDER BY TotalPoints DESC,Wins DESC,Username ASC";
        try(Connection c=db.open();PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()) {
            while(r.next())result.add(readCredential(r).user());
        }
        return result;
    }
    static Credential readCredential(ResultSet r)throws SQLException {
        User u=new User(r.getInt("UserId"),r.getString("Username"),r.getInt("TotalPoints"),r.getInt("Wins"),r.getInt("Losses"),r.getInt("TotalGames"));
        return new Credential(u,r.getBytes("PasswordSalt"),r.getBytes("PasswordHash"));
    }
}
