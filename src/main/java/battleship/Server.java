package battleship;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Server nhiều client: kiểm tra mọi thao tác và là nơi duy nhất áp dụng luật game. */
public final class Server implements AutoCloseable {
    public static final long TURN_MS=15000, INVITE_MS=30000, SETUP_MS=120000, REMATCH_MS=30000;
    private final Store store;
    private final ServerSocket listener;
    private final Set<Session> connections=ConcurrentHashMap.newKeySet();
    private final Map<String,Session> online=new TreeMap<>();
    private final List<Invite> invites=new ArrayList<>();
    private final Set<RuntimeMatch> matches=new HashSet<>();
    private final ScheduledExecutorService clock=Executors.newSingleThreadScheduledExecutor();
    private final GameManager gameManager=new GameManager();
    private volatile boolean closing;

    private static final class Invite {
        final Session from,to; final long deadline=System.currentTimeMillis()+INVITE_MS;
        Invite(Session from,Session to){this.from=from;this.to=to;}
    }
    /** Kết nối của hai người chơi gắn với đối tượng Game lưu trạng thái ván. */
    private static final class RuntimeMatch {
        final Game game; final Session a,b; final Set<Session> again=new HashSet<>();
        RuntimeMatch(Session a,Session b){this.a=a;this.b=b;game=new Game(a.name,b.name);}
        Session other(Session s){return s==a?b:a;}
    }
    private final class Session implements Runnable {
        final Socket socket;
        final BlockingQueue<String> outgoing=new ArrayBlockingQueue<>(128);
        volatile boolean closed;
        String name; int userId; User account; RuntimeMatch match;
        Session(Socket socket)throws IOException{this.socket=socket;socket.setSoTimeout(70000);socket.setTcpNoDelay(true);}
        void send(String... fields){if(!closed&&!outgoing.offer(Wire.pack(fields)))closeSocket();}
        void closeSocket(){closed=true;try{socket.close();}catch(IOException ignored){}}
        void writeLoop(){
            try(BufferedWriter out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8))){
                while(!closed){String line=outgoing.poll(1,TimeUnit.SECONDS);if(line!=null){out.write(line);out.newLine();out.flush();}}
            }catch(IOException|InterruptedException e){closeSocket();}
        }
        @Override public void run(){
            Thread writer=new Thread(this::writeLoop,"client-sender");writer.setDaemon(true);writer.start();
            try(BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8))){
                send("INFO","Kết nối server thành công. Hãy đăng ký hoặc đăng nhập.");
                String line;
                while(!closed&&(line=Wire.read(in))!=null){
                    try{handle(this,Wire.unpack(line));}
                    catch(IllegalArgumentException e){send("ERROR",e.getMessage()==null?"Dữ liệu không hợp lệ":e.getMessage());}
                    catch(IOException e){System.err.println("Lỗi SQL Server: "+e);send("ERROR","Không thực hiện được thao tác trên cơ sở dữ liệu. Kiểm tra kết nối SQL Server.");}
                }
            }catch(IOException e){if(!closing)System.out.println("Client rời kết nối: "+name);}
            finally{closeSocket();disconnect(this);connections.remove(this);}
        }
    }

    public Server(int port,DbConfig config)throws IOException {
        store=new Store(config);
        try{listener=new ServerSocket(port);}catch(IOException e){store.close();throw e;}
        clock.scheduleAtFixedRate(this::tick,100,100,TimeUnit.MILLISECONDS);
    }
    public int port(){return listener.getLocalPort();}
    public void run()throws IOException {
        System.out.println("BattleShip server cổng "+port());
        while(!closing){
            Socket socket;
            try{socket=listener.accept();}catch(SocketException e){if(closing)break;throw e;}
            if(connections.size()>=100){socket.close();continue;}
            try{Session s=new Session(socket);connections.add(s);Thread t=new Thread(s,"client-reader");t.setDaemon(true);t.start();}
            catch(IOException e){socket.close();}
        }
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
    private static void fields(String[] a,int n){require(a.length==n,"Sai số trường thông điệp");}

    /** Khóa server ngăn hai yêu cầu đồng thời làm sai lượt hoặc vòng đời trận. */
    private synchronized void handle(Session s,String[] a)throws IOException {
        if(closing||s.closed)return;
        String cmd=a[0];
        if(cmd.equals("PING")){fields(a,1);s.send("PONG");return;}
        if(cmd.equals("REGISTER")||cmd.equals("LOGIN")){
            fields(a,3);require(s.name==null,"Bạn đã đăng nhập");
            if(cmd.equals("REGISTER")){store.register(a[1],a[2]);s.send("REGISTERED","Tạo tài khoản thành công.");}
            else{
                require(store.authenticate(a[1],a[2]),"Sai tài khoản hoặc mật khẩu");
                require(!online.containsKey(a[1]),"Tài khoản này đang online");
                User u=store.account(a[1]);require(u!=null,"Không tìm thấy tài khoản");
                s.account=u;s.userId=u.id();s.name=u.username();online.put(s.name,s);
                s.send("LOGGED",s.name);lobby();
            }
            return;
        }
        require(s.name!=null,"Cần đăng nhập trước");
        switch(cmd){
            case "LOBBY"->{fields(a,1);lobby();}
            case "RANK"->{
                fields(a,1);StringBuilder text=new StringBuilder("Tên | Điểm | Thắng | Thua | Tổng trận\n");
                for(User u:store.ranking())text.append(u.username()).append(" | ").append(u.totalPoints()).append(" | ")
                    .append(u.wins()).append(" | ").append(u.losses()).append(" | ").append(u.totalGames()).append('\n');
                s.send("RANK",text.toString());
            }
            case "HISTORY"->{fields(a,1);s.send("HISTORY",store.history(s.name));}
            case "INVITE"->{
                fields(a,2);Session target=online.get(a[1]);
                require(target!=null&&target!=s,"Đối thủ không hợp lệ hoặc đã offline");
                require(free(s)&&free(target),"Một người đang bận hoặc đang có lời mời");
                invites.add(new Invite(s,target));s.send("INFO","Đã mời "+target.name+"; chờ tối đa 30 giây.");
                target.send("INVITED",s.name);lobby();
            }
            case "ANSWER"->{
                fields(a,3);require(a[2].equals("YES")||a[2].equals("NO"),"Phản hồi không hợp lệ");
                Invite invite=invites.stream().filter(i->i.to==s&&i.from.name.equals(a[1])).findFirst().orElse(null);
                require(invite!=null,"Lời mời đã hết hạn hoặc bị hủy");invites.remove(invite);
                if(System.currentTimeMillis()>=invite.deadline){invite.from.send("INFO","Lời mời hết hạn");s.send("INFO","Lời mời hết hạn");}
                else if(a[2].equals("YES"))create(invite.from,s);
                else invite.from.send("INFO",s.name+" từ chối lời mời.");
                lobby();
            }
            case "PLACE"->{
                fields(a,4);RuntimeMatch m=current(s,"SETUP");
                require(System.currentTimeMillis()<m.game.deadline,"Thời gian xếp tàu đã hết");
                require(!m.game.isReady(s.name),"Bạn đã Ready, không thể đổi vị trí tàu");
                require(a[3].equals("H")||a[3].equals("V"),"Hướng tàu phải là H hoặc V");
                int row=Integer.parseInt(a[1]),col=Integer.parseInt(a[2]);boolean horizontal=a[3].equals("H");
                Board board=m.game.createBoard(s.name),previous=board.copy();
                int shipIndex=board.placed();
                board.place(row,col,horizontal);
                int length=Board.FLEET[shipIndex];
                try{store.placeShip(m.game.id,s.userId,board.view(true),row,col,horizontal,length);}
                catch(IOException e){if(previous.placed()==0)m.game.clearBoard(s.name);else m.game.setBoard(s.name,previous);throw e;}
                s.send("PLACE_OK",Integer.toString(board.placed()),Integer.toString(length));state(m);
            }
            case "RESET_PLACE"->{
                fields(a,1);RuntimeMatch m=current(s,"SETUP");require(!m.game.isReady(s.name),"Bạn đã Ready, không thể xếp lại");
                store.clearPlacement(m.game.id,s.userId);m.game.clearBoard(s.name);s.send("PLACE_CLEARED");state(m);
            }
            case "READY"->{
                fields(a,1);RuntimeMatch m=current(s,"SETUP");
                require(System.currentTimeMillis()<m.game.deadline,"Thời gian xếp tàu đã hết");
                require(!m.game.isReady(s.name),"Bạn đã xác nhận sẵn sàng");
                Board board=m.game.board(s.name);
                require(board!=null&&board.ready(),"Hãy đặt đủ 4 tàu trước khi bấm Sẵn sàng");
                store.markReady(m.game.id,s.userId,board.view(true));
                m.game.markReady(s.name);s.send("READY_OK");
                if(m.game.readyCount()==2){
                    String first=TurnManager.chooseFirst(m.game.playerA,m.game.playerB);
                    long deadline=TurnManager.deadline();
                    store.startMatch(m.game.id,online.get(first).userId,deadline);
                    m.game.status="PLAYING";m.game.activeTurn=first;m.game.deadline=deadline;
                    m.a.send("START",first,Long.toString(TURN_MS));m.b.send("START",first,Long.toString(TURN_MS));lobby();
                }
                state(m);
            }
            case "SHOT"->{
                fields(a,3);RuntimeMatch m=current(s,"PLAYING");
                if(System.currentTimeMillis()>=m.game.deadline){timeout(m);throw new IllegalArgumentException("Hết giờ lượt trước; đã chuyển lượt");}
                require(m.game.activeTurn.equals(s.name),"Chưa đến lượt bạn");
                int row=Integer.parseInt(a[1]),col=Integer.parseInt(a[2]);
                Session target=m.other(s);Board targetBoard=m.game.board(target.name),previous=targetBoard.copy();
                String result=targetBoard.shoot(row,col);
                boolean won=gameManager.isOver(targetBoard);
                String next=won?s.name:TurnManager.next(s.name,m.game.playerA,m.game.playerB);
                long deadline=won?System.currentTimeMillis()+REMATCH_MS:TurnManager.deadline();
                try{
                    if(won)store.recordWinningShot(m.game.id,s.userId,target.userId,
                        m.game.board(s.name).view(true),targetBoard.view(true),s.userId,row,col,result);
                    else store.recordShot(m.game.id,s.userId,target.userId,targetBoard.view(true),row,col,result,
                        online.get(next).userId,deadline);
                }catch(IOException e){m.game.setBoard(target.name,previous);throw e;}
                s.send("SHOT",s.name,a[1],a[2],result);
                target.send("SHOT",s.name,a[1],a[2],result);
                if(won)finishState(m,s,"ALL_SUNK",true);
                else{m.game.activeTurn=next;m.game.deadline=deadline;state(m);}
            }
            case "LEAVE"->{
                fields(a,1);require(s.match!=null,"Bạn chưa ở trong trận");RuntimeMatch m=s.match;
                if(m.game.status.equals("PLAYING"))finish(m,m.other(s),"LEAVE");
                end(m,s.name+" đã rời trận.");
            }
            case "LOGOUT"->{
                fields(a,1);
                if(s.match!=null){
                    RuntimeMatch m=s.match;
                    if(m.game.status.equals("PLAYING"))finish(m,m.other(s),"LOGOUT");
                    end(m,s.name+" đã đăng xuất.");
                }
                online.remove(s.name);s.send("LOGGED_OUT");s.name=null;s.userId=0;s.account=null;lobby();
            }
            case "REMATCH"->{
                fields(a,2);require(a[1].equals("YES")||a[1].equals("NO"),"Phản hồi không hợp lệ");
                RuntimeMatch m=current(s,"FINISHED");require(System.currentTimeMillis()<m.game.deadline,"Thời gian chơi lại đã hết");
                if(a[1].equals("NO"))end(m,s.name+" không chơi lại.");
                else{
                    m.again.add(s);s.send("INFO","Đã đồng ý chơi lại; chờ đối thủ.");
                    if(m.again.size()==2){
                        store.closeMatch(m.game.id,"FINISHED","Cả hai người đồng ý chơi lại.");
                        matches.remove(m);m.a.match=null;m.b.match=null;create(m.a,m.b);lobby();
                    }
                }
            }
            default->throw new IllegalArgumentException("Lệnh không hỗ trợ: "+cmd);
        }
    }
    private RuntimeMatch current(Session s,String phase){
        require(s.match!=null&&s.match.game.status.equals(phase),"Trạng thái trận không phù hợp với thao tác");return s.match;
    }
    private boolean free(Session s){return s.match==null&&invites.stream().noneMatch(i->i.from==s||i.to==s);}
    private void create(Session a,Session b)throws IOException {
        RuntimeMatch m=new RuntimeMatch(a,b);store.createMatch(m.game.id,a.userId,b.userId);
        a.match=m;b.match=m;matches.add(m);
        a.send("MATCH",m.game.id,b.name,Long.toString(m.game.deadline));b.send("MATCH",m.game.id,a.name,Long.toString(m.game.deadline));state(m);
    }
    private void state(RuntimeMatch m){
        for(Session s:List.of(m.a,m.b)){
            Board own=m.game.board(s.name),enemy=m.game.board(m.other(s).name);
            s.send("STATE",m.game.status,own==null?"":own.view(true),enemy==null?"":enemy.view(false),
                m.game.activeTurn,Long.toString(m.game.deadline));
        }
    }
    /** Nhường thua/thoát/mất kết nối; việc cập nhật hai thống kê dùng một SQL transaction. */
    private void finish(RuntimeMatch m,Session winner,String reason){
        if(!m.game.status.equals("PLAYING"))return;
        Session loser=m.other(winner);boolean saved=true;
        try{store.recordForfeit(m.game.id,winner.userId,loser.userId,reason,
            m.game.board(winner.name).view(true),m.game.board(loser.name).view(true));}
        catch(IOException e){saved=false;System.err.println("Không lưu được kết quả: "+e);}
        finishState(m,winner,reason,saved);
    }
    private void finishState(RuntimeMatch m,Session winner,String reason,boolean saved){
        m.game.status="FINISHED";m.game.activeTurn="";m.game.deadline=System.currentTimeMillis()+REMATCH_MS;
        if(saved){refreshAccount(m.a);refreshAccount(m.b);}
        state(m);
        m.a.send("OVER",winner.name,reason,saved?"SAVED":"UNSAVED");
        m.b.send("OVER",winner.name,reason,saved?"SAVED":"UNSAVED");lobby();
    }
    private void refreshAccount(Session s){try{s.account=store.account(s.name);}catch(IOException e){System.err.println(e);}}
    private void end(RuntimeMatch m,String message){
        try{store.closeMatch(m.game.id,m.game.status,message);}
        catch(IOException e){System.err.println("Không lưu được trạng thái kết thúc phòng: "+e);}
        matches.remove(m);m.a.match=null;m.b.match=null;
        m.a.send("END",message);m.b.send("END",message);lobby();
    }
    private void timeout(RuntimeMatch m)throws IOException {
        String next=TurnManager.next(m.game.activeTurn,m.game.playerA,m.game.playerB);
        long deadline=TurnManager.deadline();
        store.timeout(m.game.id,online.get(next).userId,deadline);
        m.game.activeTurn=next;m.game.deadline=deadline;
        m.a.send("TIMEOUT",next);m.b.send("TIMEOUT",next);state(m);
    }
    private synchronized void tick(){
        if(closing)return;
        long now=System.currentTimeMillis();boolean changed=false;
        for(Iterator<Invite> it=invites.iterator();it.hasNext();){
            Invite i=it.next();if(now>=i.deadline){it.remove();i.from.send("INFO","Lời mời hết hạn");i.to.send("INFO","Lời mời hết hạn");changed=true;}
        }
        for(RuntimeMatch m:new ArrayList<>(matches))if(now>=m.game.deadline){
            if(m.game.status.equals("PLAYING")){
                try{timeout(m);}catch(IOException e){m.game.deadline=System.currentTimeMillis()+1000;System.err.println("Không lưu được chuyển lượt: "+e);}
            }else end(m,m.game.status.equals("SETUP")?"Hết 120 giây xếp tàu; hủy trận.":"Hết thời gian chọn chơi lại.");
        }
        if(changed)lobby();
    }
    private void lobby(){
        StringBuilder rows=new StringBuilder();
        for(Session s:online.values()){
            int points=s.account==null?0:s.account.totalPoints();
            rows.append(s.name).append(',').append(points).append(',')
                .append(s.match!=null?(s.match.game.status.equals("PLAYING")?"Đang chơi":"Trong phòng"):free(s)?"Rỗi":"Chờ lời mời").append('\n');
        }
        for(Session s:online.values())s.send("LOBBY",rows.toString());
    }
    private synchronized void disconnect(Session s){
        if(s.name==null||online.get(s.name)!=s)return;
        online.remove(s.name);
        invites.removeIf(i->{if(i.from==s||i.to==s){(i.from==s?i.to:i.from).send("INFO","Lời mời đã hủy vì đối thủ mất kết nối.");return true;}return false;});
        if(s.match!=null){RuntimeMatch m=s.match;if(!closing&&m.game.status.equals("PLAYING"))finish(m,m.other(s),"DISCONNECT");end(m,s.name+" mất kết nối.");}
        if(!closing)lobby();
    }
    @Override public synchronized void close()throws IOException {
        if(closing)return;closing=true;clock.shutdownNow();listener.close();connections.forEach(Session::closeSocket);store.close();
    }
    public static void main(String[] args)throws Exception {
        int port=args.length>0&&!args[0].isBlank()?Integer.parseInt(args[0]):5000;
        DbConfig config=DbConfig.load();
        Server server=new Server(port,config);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{try{server.close();}catch(IOException e){e.printStackTrace();}}));
        System.out.println("SQL Server: "+config.url());
        server.run();
    }
}
