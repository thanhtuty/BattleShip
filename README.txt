BATTLESHIP ONLINE — HƯỚNG DẪN NHANH CHO NHÓM

Yêu cầu: JDK 17+, NetBeans có Maven, SQL Server và SSMS.
Mỗi thành viên clone cùng mã nguồn nhưng cấu hình SQL Server riêng trong config/db.properties. File này đã được .gitignore, không đưa mật khẩu lên GitHub.

CÀI ĐẶT LẦN ĐẦU
1. Clone/tải project và mở thư mục BattleShip có pom.xml bằng NetBeans.
2. Mở SSMS, kết nối SQL Server local bằng tài khoản quản trị. Chạy lần lượt:
   database/01_BattleShipDB.sql
   database/02_CreateLogin.sql
   Script 02 tạo/reset SQL login mẫu battleship_app / Change_This_Password_123!.
3. Chạy setup-config.bat. File config/db.properties mở ra; sửa db.url theo máy của bạn.
   SQL Server Express SQLEXPRESS:
   jdbc:sqlserver://localhost;instanceName=SQLEXPRESS;databaseName=BattleShipDB;encrypt=true;trustServerCertificate=true
   SQL Server TCP cổng 1433:
   jdbc:sqlserver://localhost:1433;databaseName=BattleShipDB;encrypt=true;trustServerCertificate=true
   Sửa db.username/db.password nếu bạn đã đổi login.
4. NetBeans: nhấp phải project > Clean and Build, chờ BUILD SUCCESS.
5. Chạy run-server.bat hoặc chạy battleship.Server. Chỉ khi SQL kết nối thành công Server mới mở cổng game 5000.
6. Chạy run-client.bat hai lần (hoặc chạy battleship.Client hai lần). Nhập 127.0.0.1 và 5000 để thử trên cùng máy.

TÀI KHOẢN GAME
Đăng ký và đăng nhập chỉ gửi request đến Server. Server chỉ khởi động sau khi xác thực kết nối SQL Server thành công. Tài khoản game được INSERT vào dbo.GameUsers, mật khẩu lưu dạng PBKDF2 hash và salt. Nếu database không kết nối hoặc INSERT thất bại, đăng ký/đăng nhập báo lỗi; chương trình không có chế độ lưu tài khoản trong RAM.

KHI LÀM NHÓM / GITHUB
- Commit mã nguồn, pom.xml, SQL scripts và config/db.properties.example.
- Không commit config/db.properties (được .gitignore) và không gửi mật khẩu SQL lên GitHub.
- Thành viên mới chạy SQL scripts một lần, tạo db.properties riêng, rồi Clean and Build.
- Để chơi qua LAN, chỉ máy chạy Server cần SQL Server. Client ở máy khác nhập IPv4 của máy Server và cổng 5000; mở TCP 5000 trên Windows Firewall Private.

LỖI THƯỜNG GẶP
- Server báo thiếu config/db.properties: chạy setup-config.bat.
- Timeout khi tìm SQLEXPRESS: bật SQL Server Browser, hoặc cấu hình TCP/IP port cố định rồi dùng URL cổng như ví dụ trên.
- Login failed: chạy database/02_CreateLogin.sql và bảo đảm mật khẩu trong db.properties trùng.
- Connection refused ở Client: Server chưa in “BattleShip server cổng 5000”; đọc lỗi SQL trong cửa sổ Server trước.
- Lệnh không hỗ trợ (LOGOUT/RESET_PLACE): dừng Server cũ; Clean and Build, rồi chạy Client và Server từ cùng bản project.
