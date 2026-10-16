package battleship;

import java.util.*;

/** Luật bàn cờ 6x6. Thứ tự đặt tàu: dài 2, 3, 4, 5 ô. */
public final class Board {
    public static final int SIZE=6;
    public static final int[] FLEET={2,3,4,5};
    private final Cell[][] cells=new Cell[SIZE][SIZE];
    private final List<Ship> fleet=new ArrayList<>();

    public Board(){for(int r=0;r<SIZE;r++)for(int c=0;c<SIZE;c++)cells[r][c]=new Cell();}
    public void place(int row,int col,boolean horizontal){
        if(fleet.size()>=FLEET.length)throw new IllegalArgumentException("Đã đặt đủ 4 tàu");
        int length=FLEET[fleet.size()];
        for(int k=0;k<length;k++){
            int r=row+(horizontal?0:k),c=col+(horizontal?k:0);
            if(r<0||r>=SIZE||c<0||c>=SIZE)throw new IllegalArgumentException("Tàu vượt khỏi bàn cờ 6x6");
            if(cells[r][c].ship()!=null)throw new IllegalArgumentException("Tàu bị chồng lên tàu khác");
        }
        Ship ship=new Ship(fleet.size()+1,length);
        for(int k=0;k<length;k++)cells[row+(horizontal?0:k)][col+(horizontal?k:0)].place(ship);
        fleet.add(ship);
    }
    public int placed(){return fleet.size();}
    public boolean ready(){return fleet.size()==FLEET.length;}
    /** Trả về MISS, HIT hoặc SUNK-<chiều_dài_tàu>. */
    public String shoot(int row,int col){
        if(row<0||row>=SIZE||col<0||col>=SIZE)throw new IllegalArgumentException("Ô bắn nằm ngoài bàn cờ");
        Cell cell=cells[row][col];
        if(cell.isShot())throw new IllegalArgumentException("Ô này đã bị bắn");
        cell.markShot();Ship ship=cell.ship();
        if(ship==null)return "MISS";
        return ship.hit()?"SUNK-"+ship.length():"HIT";
    }
    public boolean defeated(){return ready()&&fleet.stream().allMatch(Ship::isSunk);}
    /** Mã ô: 0 biển, 1 tàu chưa trúng (chủ mới thấy), 2 trượt, 3 trúng. */
    public String view(boolean owner){
        StringBuilder out=new StringBuilder(SIZE*SIZE);
        for(int r=0;r<SIZE;r++)for(int c=0;c<SIZE;c++){
            Cell cell=cells[r][c];
            out.append(cell.isShot()?(cell.ship()==null?'2':'3'):(owner&&cell.ship()!=null?'1':'0'));
        }
        return out.toString();
    }
    /** Tạo bản sao để khôi phục bàn nếu giao dịch SQL Server lỗi giữa lượt. */
    public Board copy(){
        Board copy=new Board();
        Map<Integer,Ship> ships=new HashMap<>();
        for(int i=0;i<fleet.size();i++){
            Ship old=fleet.get(i),fresh=new Ship(old.id(),old.length());
            for(int h=0;h<old.hits();h++)fresh.hit();
            copy.fleet.add(fresh);ships.put(fresh.id(),fresh);
        }
        for(int r=0;r<SIZE;r++)for(int c=0;c<SIZE;c++){
            Cell old=cells[r][c],fresh=copy.cells[r][c];
            if(old.ship()!=null)fresh.place(ships.get(old.ship().id()));
            if(old.isShot())fresh.markShot();
        }
        return copy;
    }
    /** Dữ liệu xếp tàu r,c,H;r,c,V;... đúng thứ tự chiều dài 2,3,4,5. */
    public static Board parse(String encoded){
        String[] list=encoded.split(";",-1);
        if(list.length!=FLEET.length)throw new IllegalArgumentException("Cần xếp đủ 4 tàu");
        Board board=new Board();
        for(String item:list){
            String[] p=item.split(",",-1);
            if(p.length!=3||!(p[2].equals("H")||p[2].equals("V")))throw new IllegalArgumentException("Dữ liệu xếp tàu không hợp lệ");
            try{board.place(Integer.parseInt(p[0]),Integer.parseInt(p[1]),p[2].equals("H"));}
            catch(NumberFormatException e){throw new IllegalArgumentException("Tọa độ tàu phải là số");}
        }
        return board;
    }
    public static String randomPlacement(Random random){
        Board board=new Board();List<String> list=new ArrayList<>();
        while(!board.ready()){
            int r=random.nextInt(SIZE),c=random.nextInt(SIZE);boolean horizontal=random.nextBoolean();
            try{board.place(r,c,horizontal);list.add(r+","+c+","+(horizontal?"H":"V"));}
            catch(IllegalArgumentException ignored){}
        }
        return String.join(";",list);
    }
}
