# Kịch bản kiểm thử tích hợp trên máy có SQL Server

Project hiện dùng SQL Server; script kiểm thử cũ dùng giao thức và cơ sở dữ liệu file của phiên bản trước nên không còn áp dụng. Hãy chạy SQL scripts theo `database/README.txt`, sau đó mở hai Client và một Server.

1. Đăng ký hai tài khoản, đăng xuất/đăng nhập lại; thử sai mật khẩu và username đang online.
2. Từ Lobby gửi lời mời; thử từ chối, rồi mời lại và chấp nhận.
3. Đặt tàu ra ngoài bàn hoặc chồng tàu; server phải từ chối. Đặt đủ tàu dài 2, 3, 4, 5 và Ready hai bên.
4. Kiểm tra alert bắt đầu trận chỉ rõ người đi trước. Bắn sai lượt, bắn lặp lại, bắn trượt, trúng và đánh chìm tàu.
5. Chờ quá 15 giây để kiểm tra đổi lượt; đánh chìm toàn bộ tàu để kiểm tra thông báo thắng/thua.
6. Mở Ranking và History; đối chiếu với `GameUsers`, `GameMatches`, `MatchPlayers`, `GameEvents` trong SSMS.
7. Thử chơi lại, thoát trận, đóng Client khi đang chơi và kiểm tra trạng thái đối thủ.
8. Khởi động lại Server để xác nhận tài khoản, điểm và lịch sử vẫn được lưu.

Ghi phiên bản JDK, NetBeans, SQL Server, kết quả quan sát và ảnh chụp vào báo cáo. Không đánh dấu test SQL là đạt trước khi chạy thực tế.
