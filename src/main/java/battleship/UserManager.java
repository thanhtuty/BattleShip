package battleship;

import java.io.IOException;
import java.security.*;
import java.sql.SQLException;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Nghiệp vụ đăng ký, đăng nhập và thống kê tài khoản. */
public final class UserManager {
    private static final int ITERATIONS=120000;
    private final UserDAO dao;
    public UserManager(UserDAO dao){this.dao=dao;}
    public void register(String username,String password)throws IOException {
        validate(username,password);
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        try{dao.insert(username,salt,hash(password,salt));}
        catch(SQLException e){if("23000".equals(e.getSQLState()))throw new IllegalArgumentException("Tên tài khoản đã tồn tại");throw dbError(e);}
    }
    public boolean authenticate(String username,String password)throws IOException {
        if(password==null||password.length()>128)return false;
        try {
            UserDAO.Credential c=dao.findCredential(username);
            return c!=null&&MessageDigest.isEqual(c.passwordHash(),hash(password,c.salt()));
        }catch(SQLException e){throw dbError(e);}
    }
    public User find(String username)throws IOException {
        try{return dao.find(username);}catch(SQLException e){throw dbError(e);}
    }
    public List<User> ranking()throws IOException {
        try{return dao.ranking();}catch(SQLException e){throw dbError(e);}
    }
    private static void validate(String name,String password) {
        if(name==null||!name.matches("[a-zA-Z0-9_]{3,20}"))throw new IllegalArgumentException("Tên tài khoản gồm 3–20 chữ, số hoặc dấu _");
        if(password==null||password.length()<6||password.length()>128)throw new IllegalArgumentException("Mật khẩu dài 6–128 ký tự");
    }
    private static byte[] hash(String password,byte[] salt) {
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,ITERATIONS,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        catch(GeneralSecurityException e){throw new IllegalStateException(e);}
        finally{spec.clearPassword();}
    }
    static IOException dbError(SQLException e){return new IOException("Lỗi SQL Server: "+e.getMessage(),e);}
}
