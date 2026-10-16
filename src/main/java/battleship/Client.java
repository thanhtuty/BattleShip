package battleship;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Java Swing thuần: đọc/ghi socket ở luồng nền, cập nhật giao diện trên EDT. */
public final class Client extends JFrame {
    private final JTextField host = new JTextField("127.0.0.1", 11), port = new JTextField("5000", 5), user = new JTextField(10);
    private final JPasswordField password = new JPasswordField(10);
    private final JButton connect = new JButton("Kết nối"), login = new JButton("Đăng nhập"), register = new JButton("Đăng ký"), logout = new JButton("Đăng xuất"), invite = new JButton("Mời người đã chọn");
    private final JButton random = new JButton("Xếp ngẫu nhiên"), reset = new JButton("Xếp lại"), ready = new JButton("Sẵn sàng"), leave = new JButton("Rời trận"), yes = new JButton("Chơi lại"), no = new JButton("Về sảnh");
    private final JComboBox<String> direction = new JComboBox<>(new String[]{"Ngang", "Dọc"});
    private final JLabel status = new JLabel("Chưa kết nối"), timerLabel = new JLabel(" "), shipLabel = new JLabel(" "), opponentLabel = new JLabel("Bàn đối thủ");
    private final JTextArea log = new JTextArea(7, 60);
    private final DefaultTableModel playersModel = new DefaultTableModel(new Object[]{"Tên", "Điểm", "Trạng thái"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable players = new JTable(playersModel);
    private final JButton[][] ownButtons = new JButton[6][6], enemyButtons = new JButton[6][6];
    private Board draft = new Board();
    private final List<String> placements = new ArrayList<>();
    private String name = "", opponent = "", phase = "LOBBY", turn = "", ownState = "", enemyState = "";
    private boolean readySent, attackPending, connecting;
    private int pendingPlaces;
    private long deadline;
    private volatile Connection connection;
    private static final Color SEA = new Color(224, 241, 255), SHIP = new Color(92, 115, 142), HIT = new Color(221, 75, 65), MISS = new Color(175, 213, 237);
    private final class Connection {
        final Socket socket;
        final BlockingQueue<String> output = new ArrayBlockingQueue<>(100);
        volatile boolean closed;
        Connection(String address, int portNumber) throws IOException {
            socket = new Socket();
            try { socket.connect(new InetSocketAddress(address, portNumber), 5000); socket.setTcpNoDelay(true); socket.setSoTimeout(70000); }
            catch (IOException e) { socket.close(); throw e; }
        }
        void start() {
            Thread reader = new Thread(this::readLoop, "network-reader"); reader.setDaemon(true); reader.start();
            Thread writer = new Thread(this::writeLoop, "network-writer"); writer.setDaemon(true); writer.start();
        }
        void stop() { closed = true; try { socket.close(); } catch (IOException ignored) {} }
        void writeLoop() {
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                while (!closed) {
                    String line = output.poll(1, TimeUnit.SECONDS);
                    if (line != null) { out.write(line); out.newLine(); out.flush(); }
                }
            } catch (IOException | InterruptedException e) { stop(); }
        }
        void readLoop() {
            String reason = "Server đóng kết nối";
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (!closed && (line = Wire.read(in)) != null) {
                    String[] fields = Wire.unpack(line);
                    SwingUtilities.invokeLater(() -> { if (connection == this) receive(fields); });
                }
            } catch (IOException | IllegalArgumentException e) { reason = "Mất kết nối: " + e.getMessage(); }
            finally {
                stop(); final String message = reason;
                SwingUtilities.invokeLater(() -> {
                    if (connection == this) {
                        connection = null; name = ""; phase = "LOBBY"; deadline = 0; playersModel.setRowCount(0);
                        append(message); refresh();
                    }
                });
            }
        }
    }
    public Client() {
        super("BattleShip • Java TCP");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { if (connection != null) connection.stop(); dispose(); System.exit(0); }
        });
        JPanel top = new JPanel(new GridLayout(3, 1));
        JPanel network = new JPanel(new FlowLayout(FlowLayout.LEFT));
        network.add(new JLabel("Server IP")); network.add(host); network.add(new JLabel("Cổng")); network.add(port); network.add(connect);
        JPanel auth = new JPanel(new FlowLayout(FlowLayout.LEFT));
        auth.add(new JLabel("Tài khoản")); auth.add(user); auth.add(new JLabel("Mật khẩu")); auth.add(password); auth.add(register); auth.add(login); auth.add(logout);
        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT)); info.add(status); info.add(timerLabel); top.add(network); top.add(auth); top.add(info); add(top, BorderLayout.NORTH);
        players.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel lobby = new JPanel(new BorderLayout(5, 5)); lobby.setBorder(BorderFactory.createTitledBorder("Người chơi online")); lobby.setPreferredSize(new Dimension(245, 450));
        lobby.add(new JScrollPane(players), BorderLayout.CENTER);
        JPanel lobbyActions = new JPanel(new GridLayout(4, 1, 3, 3));
        JButton reload = new JButton("Làm mới"), ranking = new JButton("Bảng xếp hạng"), history = new JButton("Lịch sử của tôi");
        lobbyActions.add(invite); lobbyActions.add(reload); lobbyActions.add(ranking); lobbyActions.add(history); lobby.add(lobbyActions, BorderLayout.SOUTH);
        JPanel boards = new JPanel(new GridLayout(1, 2, 14, 0));
        JPanel my = new JPanel(new BorderLayout()); my.add(new JLabel("Bàn của bạn — bấm ô để đặt tàu", SwingConstants.CENTER), BorderLayout.NORTH); my.add(grid(ownButtons, true));
        JPanel other = new JPanel(new BorderLayout()); opponentLabel.setHorizontalAlignment(SwingConstants.CENTER); other.add(opponentLabel, BorderLayout.NORTH); other.add(grid(enemyButtons, false));
        boards.add(my); boards.add(other);
        JPanel actions = new JPanel(new GridLayout(2, 1));
        JPanel setupActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        setupActions.add(shipLabel); setupActions.add(direction); setupActions.add(random); setupActions.add(reset); setupActions.add(ready);
        JPanel roomActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        roomActions.add(leave); roomActions.add(yes); roomActions.add(no);
        actions.add(setupActions); actions.add(roomActions);
        JPanel play = new JPanel(new BorderLayout(5, 5)); play.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5)); play.add(boards, BorderLayout.CENTER); play.add(actions, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, lobby, play); split.setResizeWeight(0.22); add(split, BorderLayout.CENTER);
        log.setEditable(false); log.setLineWrap(true); log.setWrapStyleWord(true);
        JPanel bottom = new JPanel(new BorderLayout()); bottom.add(new JLabel("Màu: xanh đậm = tàu của bạn; đỏ × = trúng; xanh nhạt • = trượt. Tàu được phép chạm nhau."), BorderLayout.NORTH); bottom.add(new JScrollPane(log)); add(bottom, BorderLayout.SOUTH);
        connect.addActionListener(e -> connect());
        register.addActionListener(e -> send("REGISTER", user.getText().trim(), new String(password.getPassword())));
        login.addActionListener(e -> send("LOGIN", user.getText().trim(), new String(password.getPassword())));
        invite.addActionListener(e -> {
            int row = players.getSelectedRow();
            if (row < 0) { append("Chọn một người chơi trong danh sách trước."); return; }
            send("INVITE", playersModel.getValueAt(row, 0).toString());
        });
        reload.addActionListener(e -> send("LOBBY")); ranking.addActionListener(e -> send("RANK")); history.addActionListener(e -> send("HISTORY"));
        reset.addActionListener(e -> {
            draft=new Board();placements.clear();ownState="";pendingPlaces=0;readySent=false;
            send("RESET_PLACE");refresh();
        });
        random.addActionListener(e -> {
            String encoded=Board.randomPlacement(new Random());
            draft=Board.parse(encoded);placements.clear();placements.addAll(Arrays.asList(encoded.split(";")));
            ownState="";pendingPlaces=Board.FLEET.length;readySent=false;
            send("RESET_PLACE");
            for(String placement:placements){
                String[] part=placement.split(",");
                send("PLACE",part[0],part[1],part[2]);
            }
            refresh();
        });
        ready.addActionListener(e -> { readySent=true;send("READY");refresh(); });
        logout.addActionListener(e -> send("LOGOUT"));
        leave.addActionListener(e -> send("LEAVE")); yes.addActionListener(e -> send("REMATCH", "YES")); no.addActionListener(e -> send("REMATCH", "NO"));
        new javax.swing.Timer(200, e -> updateClock()).start();
        new javax.swing.Timer(15000, e -> { if (connection != null) send("PING"); }).start();
        setSize(1220, 800); setMinimumSize(new Dimension(1050, 700)); setLocationRelativeTo(null); refresh();
    }
    private JPanel grid(JButton[][] buttons, boolean own) {
        JPanel grid = new JPanel(new GridLayout(7, 7, 2, 2));
        grid.add(new JLabel("")); for (int c = 0; c < Board.SIZE; c++) grid.add(new JLabel(Integer.toString(c + 1), SwingConstants.CENTER));
        for (int r = 0; r < Board.SIZE; r++) {
            grid.add(new JLabel(String.valueOf((char) ('A' + r)), SwingConstants.CENTER));
            for (int c = 0; c < Board.SIZE; c++) {
                final int row = r, col = c; JButton b = new JButton(); buttons[r][c] = b;
                b.setMargin(new Insets(0, 0, 0, 0)); b.setOpaque(true); b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
                b.setToolTipText("" + (char) ('A' + r) + (c + 1));
                b.addActionListener(e -> {
                    if (own) {
                        try {
                            boolean h = direction.getSelectedIndex() == 0;
                            draft.place(row,col,h);
                            String orientation=h?"H":"V";
                            placements.add(row+","+col+","+orientation);ownState="";pendingPlaces++;
                            send("PLACE",Integer.toString(row),Integer.toString(col),orientation);refresh();
                        } catch (IllegalArgumentException ex) { append(ex.getMessage()); }
                    } else { attackPending = true; send("SHOT", Integer.toString(row), Integer.toString(col)); refresh(); }
                }); grid.add(b);
            }
        }
        return grid;
    }
    private void connect() {
        final String address = host.getText().trim(); final int number;
        try { number = Integer.parseInt(port.getText()); if (number < 1 || number > 65535) throw new NumberFormatException(); }
        catch (NumberFormatException e) { append("Cổng phải là số từ 1 đến 65535"); return; }
        connecting = true; refresh();
        new SwingWorker<Connection, Void>() {
            @Override protected Connection doInBackground() throws Exception { return new Connection(address, number); }
            @Override protected void done() {
                connecting = false;
                try { connection = get(); connection.start(); append("Đã kết nối " + address + ":" + number); }
                catch (Exception e) { append("Không kết nối được. Kiểm tra server, IP, cổng và firewall. " + e.getMessage()); }
                refresh();
            }
        }.execute();
    }
    private void send(String... fields) {
        Connection c = connection;
        if (c == null || c.closed) { append("Chưa kết nối server"); return; }
        if (!c.output.offer(Wire.pack(fields))) { append("Hàng đợi mạng đầy. Đóng kết nối."); c.stop(); }
    }
    private void receive(String[] a) {
        switch (a[0]) {
            case "PONG" -> { return; }
            case "INFO", "REGISTERED" -> append(a[1]);
            case "ERROR" -> {
                append("Lỗi: "+a[1]);attackPending=false;
                if(phase.equals("SETUP")&&pendingPlaces>0){
                    draft=new Board();placements.clear();ownState="";pendingPlaces=0;readySent=false;send("RESET_PLACE");
                }else if(phase.equals("SETUP"))readySent=false;
            }
            case "LOGGED" -> { name=a[1];password.setText("");setTitle("BattleShip — "+name);append("Đăng nhập thành công: "+name); }
            case "LOGGED_OUT" -> { name="";opponent="";phase="LOBBY";deadline=0;turn="";ownState="";enemyState="";password.setText("");setTitle("BattleShip — Client");append("Đã đăng xuất."); }
            case "LOBBY" -> {
                String selected = players.getSelectedRow() >= 0 ? playersModel.getValueAt(players.getSelectedRow(), 0).toString() : "";
                playersModel.setRowCount(0);
                for (String line : a[1].split("\n")) if (!line.isEmpty()) playersModel.addRow(line.split(",", -1));
                for (int r = 0; r < playersModel.getRowCount(); r++) if (playersModel.getValueAt(r, 0).equals(selected)) players.setRowSelectionInterval(r, r);
            }
            case "INVITED" -> {
                int choice = JOptionPane.showConfirmDialog(this, a[1] + " mời bạn thi đấu. Đồng ý?", "Lời mời — tối đa 30 giây", JOptionPane.YES_NO_OPTION);
                send("ANSWER", a[1], choice == JOptionPane.YES_OPTION ? "YES" : "NO");
            }
            case "MATCH" -> {
                opponent=a[2];phase="SETUP";turn="";deadline=Long.parseLong(a[3]);readySent=false;attackPending=false;pendingPlaces=0;
                draft=new Board();placements.clear();ownState="";enemyState="";
                append("Trận mới với " + opponent + ". Xếp 4 tàu dài 2, 3, 4, 5 ô trên bàn 6×6.");
            }
            case "PLACE_OK" -> { pendingPlaces=Math.max(0,pendingPlaces-1); }
            case "PLACE_CLEARED" -> { ownState=""; }
            case "READY_OK" -> { readySent = true; append("Server đã nhận đội tàu. Chờ đối thủ."); }
            case "START" -> { phase = "PLAYING"; turn = a[1]; deadline = System.currentTimeMillis() + Long.parseLong(a[2]); append("TRẬN ĐẤU BẮT ĐẦU! Người đi trước: " + turn + (name.equals(turn) ? " — bạn đi trước." : ".") + " Mỗi lượt tối đa 15 giây."); }
            case "TIMEOUT" -> append("Hết giờ. Chuyển lượt cho " + a[1] + ".");
            case "STATE" -> { phase = a[1]; ownState = a[2]; enemyState = a[3]; turn = a[4]; deadline = Long.parseLong(a[5]); attackPending = false; }
            case "SHOT" -> {
                String result = a[4].equals("MISS") ? "trượt" : a[4].equals("HIT") ? "trúng tàu" : "đánh chìm tàu dài " + a[4].substring("SUNK-".length()) + " ô";
                append(a[1] + " bắn " + (char) ('A' + Integer.parseInt(a[2])) + (Integer.parseInt(a[3]) + 1) + ": " + result);
            }
            case "OVER" -> {
                append((name.equals(a[1]) ? "BẠN LÀ NGƯỜI CHIẾN THẮNG!" : "Bạn đã thua.") + " Người thắng: " + a[1] + ". Lý do: " + a[2] + ". " + (a[3].equals("SAVED") ? "Kết quả đã lưu vào SQL Server." : "CẢNH BÁO: chưa lưu được kết quả vào SQL Server."));
            }
            case "END" -> { phase = "LOBBY"; deadline = 0; turn = ""; append(a[1]); }
            case "RANK", "HISTORY" -> {
                JTextArea text = new JTextArea(a[1], 20, 65); text.setEditable(false); text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
                JOptionPane.showMessageDialog(this, new JScrollPane(text), a[0].equals("RANK") ? "Bảng xếp hạng — tối đa 100 người" : "30 trận gần nhất của bạn", JOptionPane.INFORMATION_MESSAGE);
            }
            default -> append("Server gửi lệnh không nhận biết: " + a[0]);
        }
        refresh();
    }
    private void refresh() {
        boolean connected = connection != null && !connection.closed, logged = connected && !name.isEmpty();
        connect.setEnabled(!connected && !connecting); host.setEnabled(!connected && !connecting); port.setEnabled(!connected && !connecting);
        user.setEnabled(!logged); password.setEnabled(!logged); login.setEnabled(connected && !logged); register.setEnabled(connected && !logged);
        invite.setEnabled(logged && phase.equals("LOBBY"));
        boolean setup = logged && phase.equals("SETUP") && !readySent;
        boolean canEdit = setup && pendingPlaces == 0;
        random.setEnabled(canEdit);reset.setEnabled(canEdit);direction.setEnabled(canEdit);
        ready.setEnabled(setup&&draft.ready()&&pendingPlaces==0);logout.setEnabled(logged);
        leave.setEnabled(logged && !phase.equals("LOBBY")); yes.setEnabled(logged && phase.equals("FINISHED")); no.setEnabled(logged && phase.equals("FINISHED"));
        shipLabel.setText(setup?(draft.ready()?(pendingPlaces>0?"Đang lưu vị trí tàu lên server…":"Đã xếp đủ 4 tàu"):
            "Đặt tàu "+(draft.placed()+1)+", dài "+Board.FLEET[draft.placed()]+" ô"):" ");
        status.setText(!connected ? (connecting ? "Đang kết nối…" : "Chưa kết nối") : !logged ? "Đã kết nối — hãy đăng nhập" : name + " | " + switch (phase) { case "SETUP" -> "Xếp tàu"; case "PLAYING" -> name.equals(turn) ? "ĐẾN LƯỢT BẠN" : "Lượt của " + turn; case "FINISHED" -> "Trận kết thúc"; default -> "Sảnh"; });
        opponentLabel.setText(opponent.isEmpty() ? "Bàn đối thủ" : "Bàn của " + opponent);
        String own = phase.equals("SETUP") && ownState.isEmpty() ? draft.view(true) : ownState;
        for (int r = 0; r < Board.SIZE; r++) for (int c = 0; c < Board.SIZE; c++) {
            char x = own.length() == Board.SIZE * Board.SIZE ? own.charAt(r * Board.SIZE + c) : '0';
            char y = enemyState.length() == Board.SIZE * Board.SIZE ? enemyState.charAt(r * Board.SIZE + c) : '0';
            paint(ownButtons[r][c], x); paint(enemyButtons[r][c], y);
            ownButtons[r][c].setEnabled(canEdit && !draft.ready());
            enemyButtons[r][c].setEnabled(logged && phase.equals("PLAYING") && name.equals(turn) && !attackPending && y == '0');
        }
        updateClock();
    }
    private static void paint(JButton b, char value) {
        b.setBackground(switch (value) { case '1' -> SHIP; case '2' -> MISS; case '3' -> HIT; default -> SEA; });
        b.setText(value == '3' ? "×" : value == '2' ? "•" : ""); b.setForeground(Color.BLACK);
    }
    private void updateClock() {
        long seconds = Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000);
        timerLabel.setText(deadline == 0 ? " " : " | Còn " + seconds + " giây");
    }
    private void append(String message) {
        log.append(message + "\n");
        log.setCaretPosition(log.getDocument().getLength());
        showCenterAlert(message);
    }
    /** Mọi thông báo ngắn xuất hiện giữa cửa sổ; OK, nút X hoặc hết 3 giây sẽ đóng. */
    private void showCenterAlert(String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showCenterAlert(message));
            return;
        }
        int type = message.startsWith("Lỗi:") || message.startsWith("Không kết nối")
                ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE;
        JOptionPane pane = new JOptionPane(message, type, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{"OK"}, "OK");
        JDialog dialog = pane.createDialog(this, "Thông báo — BattleShip");
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        javax.swing.Timer timer = new javax.swing.Timer(3000, e -> dialog.dispose());
        timer.setRepeats(false);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { timer.stop(); }
        });
        dialog.setLocationRelativeTo(this);
        timer.start();
        dialog.setVisible(true);
        timer.stop();
    }
    public static void main(String[] args) {
        // Giữ Metal để màu ô vẫn nhìn rõ khi nút bị disable.
        SwingUtilities.invokeLater(() -> new Client().setVisible(true));
    }
}
