package battleship;

import java.sql.*;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Cấu hình SQL Server đọc từ file riêng của từng máy, không commit credentials lên GitHub. */
public record DbConfig(String url, String username, String password) {
    public static DbConfig load() {
        Path file=Path.of("config","db.properties");
        if(!Files.isRegularFile(file))
            throw new IllegalStateException("Thiếu config/db.properties. Hãy copy config/db.properties.example thành config/db.properties rồi điền thông tin SQL Server.");
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(file)){p.load(in);}
        catch(IOException e){throw new IllegalStateException("Không đọc được config/db.properties: "+e.getMessage(),e);}
        String url=p.getProperty("db.url","").trim();
        String user=p.getProperty("db.username","").trim();
        String password=p.getProperty("db.password","");
        if(url.isEmpty()||user.isEmpty()||password.isEmpty())
            throw new IllegalStateException("Điền đủ db.url, db.username và db.password trong config/db.properties.");
        return new DbConfig(url,user,password);
    }
    public Connection open() throws SQLException {
        Properties properties=new Properties();
        properties.setProperty("user",username);
        properties.setProperty("password",password);
        properties.setProperty("loginTimeout","8");
        return DriverManager.getConnection(url,properties);
    }
}
