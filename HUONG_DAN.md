# BattleShip Online — hướng dẫn cài từ đầu

## 1. Project cần gì?

- JDK 17 trở lên.
- NetBeans với Maven (NetBeans 24 đã phù hợp).
- SQL Server và SQL Server Management Studio (SSMS).
- JDBC Driver được Maven tải tự động qua `pom.xml`.

Kiến trúc: hai Client Java Swing trao đổi TCP với một Server Java. Chỉ Server kết nối SQL Server. Server không mở cổng game nếu database không kết nối được.

## 2. Mở project

1. Giải nén thư mục `BattleShip`.
2. NetBeans chọn **File → Open Project** và mở đúng thư mục có `pom.xml`.
3. Chờ Maven tải thư viện, rồi nhấp phải project → **Clean and Build**.
4. Kết quả cần thấy `BUILD SUCCESS`.

## 3. Tạo database và SQL login (làm một lần trên mỗi máy chạy Server)

1. Mở SSMS và kết nối đúng SQL Server instance đang có trên máy.
2. Mở `database/01_BattleShipDB.sql`, nhấn **Execute**. Script tạo database `BattleShipDB`, các bảng và chỉ mục.
3. SQL Server cần bật **SQL Server and Windows Authentication mode (Mixed Mode)**. Trong SSMS: nhấp phải tên server → **Properties → Security** → chọn Mixed Mode → OK; sau đó restart dịch vụ SQL Server.
4. Mở `database/02_CreateLogin.sql`, nhấn **Execute** bằng tài khoản SQL Server quản trị. Script tạo/reset login `battleship_app` với mật khẩu mẫu `Change_This_Password_123!` và cấp quyền đọc/ghi database.

> Nếu đổi mật khẩu trong script SQL, hãy dùng cùng mật khẩu trong `config/db.properties`.

## 4. Cấu hình kết nối — mỗi thành viên làm riêng

Không cần khai báo Windows Environment Variables.

1. Trong thư mục project, chạy `setup-config.bat`.
2. File `config/db.properties` mở bằng Notepad. Điền cấu hình phù hợp instance của máy đó.
3. Với SQL Server Express tên `SQLEXPRESS`, dùng mẫu:

```properties
db.url=jdbc:sqlserver://localhost;instanceName=SQLEXPRESS;databaseName=BattleShipDB;encrypt=true;trustServerCertificate=true
db.username=battleship_app
db.password=Change_This_Password_123!
```

Nếu đã cấu hình SQL Server nghe ở TCP port cố định, ví dụ 1433, đổi `db.url` thành:

```properties
db.url=jdbc:sqlserver://localhost:1433;databaseName=BattleShipDB;encrypt=true;trustServerCertificate=true
```

4. Lưu file. `config/db.properties` đã nằm trong `.gitignore`, nên thông tin riêng không được đưa lên GitHub.

Nếu dùng instance `SQLEXPRESS` và JDBC báo timeout UDP 1434, bật dịch vụ **SQL Server Browser** hoặc cấu hình một TCP port cố định rồi dùng URL dạng port ở trên.

## 5. Chạy chương trình

1. Đảm bảo SQL Server đang chạy và file `config/db.properties` đã đúng.
2. Trong NetBeans, Clean and Build nếu mới sửa code.
3. Chạy `Server.java` bằng **Run File**. Output phải hiện `BattleShip server cổng 5000`.
4. Chạy `Client.java` hai lần. Ở cả hai Client, nhập IP `127.0.0.1`, cổng `5000`, rồi bấm **Kết nối**.
5. Đăng ký hai tài khoản game riêng, đăng nhập, chọn người chơi trong Lobby và mời đấu.

Server khởi tạo `Store`, thử kết nối database, rồi mới tạo `ServerSocket`. Nếu database lỗi, Server dừng ngay; vì thế Client sẽ không thể kết nối vào cổng 5000. Đây là chủ ý để không tạo tài khoản giả trong RAM.

## 6. Kiểm tra dữ liệu tài khoản trong SQL Server

Sau khi đăng ký thành công ở Client, chạy trong SSMS:

```sql
USE BattleShipDB;
SELECT UserId, Username, TotalPoints, Wins, Losses, TotalGames
FROM dbo.GameUsers;
```

Tên đăng nhập sẽ xuất hiện trong `GameUsers`. Mật khẩu game không lưu dạng chữ thường; database lưu salt và PBKDF2 hash. Nếu database ngắt kết nối, đăng ký/đăng nhập sẽ báo lỗi từ Server và không được xem là thành công.

Các bảng chính:

- `GameUsers`: tài khoản game và thống kê.
- `GameMatches`: thông tin và kết quả trận.
- `MatchPlayers`: người chơi, trạng thái Ready, trạng thái bàn.
- `GameEvents`: sự kiện đặt tàu, bắn, timeout, kết thúc.

## 7. Chơi trên hai máy

- Cài SQL Server và chạy database scripts chỉ trên máy chạy Server.
- Máy Server chạy `Server.java` và giữ cửa sổ này mở.
- Cho phép TCP inbound port 5000 trong Windows Defender Firewall trên mạng Private.
- Client trên máy khác nhập IPv4 LAN của máy Server (xem bằng `ipconfig`) và cổng `5000`. Không nhập `127.0.0.1` trên máy Client khác.

## 8. Chức năng và quy tắc game

- Bàn 6×6; 4 tàu dài 2, 3, 4 và 5 ô; đặt ngang/dọc, không được chồng hoặc vượt bàn.
- Mời, đồng ý/từ chối, Ready hai phía, bắt đầu trận; lượt tối đa 15 giây.
- Bắn trượt thì chuyển lượt; bắn trúng hoặc đánh chìm một tàu thì người bắn được bắn tiếp. Chỉ hiện thông báo khi có người đánh chìm hẳn một tàu.
- Kết thúc khi toàn bộ tàu của một người bị đánh chìm; cập nhật điểm, thắng/thua, tổng trận, Ranking và History.
- Server giữ luật và trạng thái trận; Client không được tự quyết định kết quả. Tàu đối thủ chưa bị bắn được che khỏi Client.
- Hộp thông báo căn giữa, tự đóng sau 3 giây hoặc đóng bằng OK/X.

## 9. Đưa project lên GitHub

Commit các file source, `pom.xml`, `database/*.sql`, `config/db.properties.example`, `.gitignore` và tài liệu. Không commit `config/db.properties`, mật khẩu thật hoặc thư mục `target`. Thành viên mới clone project, chạy hai SQL script, tạo file cấu hình riêng bằng `setup-config.bat`, rồi Clean and Build.

## 10. Chẩn đoán lỗi

- **`Thiếu config/db.properties`**: chạy `setup-config.bat`.
- **Timeout `named instance SQLEXPRESS` / UDP 1434**: SQL Server Browser chưa chạy/UDP bị chặn; bật Browser hoặc dùng TCP port cố định trong URL.
- **`Login failed for user battleship_app`**: xác nhận Mixed Mode, chạy `02_CreateLogin.sql`, và kiểm tra mật khẩu trong file cấu hình.
- **`Cannot open database BattleShipDB`**: chạy `01_BattleShipDB.sql` trên đúng instance SQL Server.
- **Client báo Connection refused**: xem Output của Server. Chỉ kết nối Client sau khi Server in `BattleShip server cổng 5000`.
- **`Lệnh không hỗ trợ: LOGOUT` hoặc `RESET_PLACE`**: đã chạy lệch phiên bản. Dừng Server cũ, Clean and Build, rồi chạy cả Client/Server từ cùng thư mục project.

## 11. Gợi ý ghi vào báo cáo

Nhóm có thể mô tả kiến trúc Client–Server, giao thức TCP, JDBC, các bảng database, PBKDF2, luật bàn 6×6 và kiểm thử các chức năng. Chỉ ghi các kiểm thử đã chạy thật; nên chụp ảnh đăng ký rồi đối chiếu bản ghi `GameUsers`, trận đấu, bảng xếp hạng và lịch sử trong SSMS.
