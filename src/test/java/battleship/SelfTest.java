package battleship;

import java.util.*;

/** Kiểm thử luật game không cần kết nối SQL Server. */
public final class SelfTest {
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    private static void invalid(Runnable action){
        try{action.run();throw new AssertionError("Phải từ chối dữ liệu sai");}
        catch(IllegalArgumentException expected){}
    }
    public static void main(String[] args){
        String[] message={"","","Tiếng Việt\nA\tB","YES"};
        check(Arrays.equals(message,Wire.unpack(Wire.pack(message))),"Wire không round trip");
        Board board=Board.parse("0,0,H;1,0,H;2,0,H;3,0,H");
        check(board.ready()&&board.view(true).chars().filter(c->c=='1').count()==14,"Đội tàu 2+3+4+5 sai");
        check(board.view(false).equals("0".repeat(36)),"Lộ vị trí tàu");
        invalid(()->Board.parse("0,5,H;1,0,H;2,0,H;3,0,H"));
        invalid(()->Board.parse("0,0,H;0,1,H;2,0,H;3,0,H"));
        invalid(()->Board.parse("0,0,X;1,0,H;2,0,H;3,0,H"));
        invalid(()->Board.parse("0,0,H"));
        invalid(()->board.shoot(-1,0));
        check(board.shoot(5,5).equals("MISS"),"Bắn trượt");
        invalid(()->board.shoot(5,5));
        Board before=board.copy();
        for(int r=0;r<4;r++)for(int c=0;c<Board.FLEET[r];c++){
            String result=board.shoot(r,c);
            check(result.equals(c==Board.FLEET[r]-1?"SUNK-"+Board.FLEET[r]:"HIT"),"Trúng/chìm sai");
        }
        check(board.defeated(),"Chưa kết thúc khi chìm hết tàu");
        check(!before.defeated()&&before.view(true).contains("1"),"Bản sao Board không độc lập");
        for(int i=0;i<100;i++)check(Board.parse(Board.randomPlacement(new Random(i))).ready(),"Xếp ngẫu nhiên sai");
        check(TurnManager.next("alice","alice","bob").equals("bob"),"Đổi lượt timeout sai");
        check(TurnManager.afterShot("alice","bob","MISS").equals("bob"),"Bắn trượt phải chuyển lượt");
        check(TurnManager.afterShot("alice","bob","HIT").equals("alice"),"Bắn trúng phải giữ lượt");
        check(TurnManager.afterShot("alice","bob","SUNK-3").equals("alice"),"Đánh chìm tàu phải giữ lượt");
        System.out.println("PASS: Wire, Board 6x6, 4 ships, secrecy, hit/sunk, duplicate shot, random placement, turn order");
    }
}
