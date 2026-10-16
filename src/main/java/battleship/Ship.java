package battleship;

/** Một tàu có chiều dài cố định và đếm số khoang đã trúng. */
public final class Ship {
    private final int id,length;private int hits;
    public Ship(int id,int length){this.id=id;this.length=length;}
    public int id(){return id;}
    public int length(){return length;}
    public int hits(){return hits;}
    public boolean hit(){if(hits<length)hits++;return isSunk();}
    public boolean isSunk(){return hits==length;}
}
