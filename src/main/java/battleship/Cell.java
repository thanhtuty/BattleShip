package battleship;

/** Một ô trên bàn cờ: chứa tối đa một tàu và ghi nhận đã bị bắn chưa. */
public final class Cell {
    private Ship ship;
    private boolean shot;
    public Ship ship(){return ship;}
    public boolean isShot(){return shot;}
    void place(Ship ship){this.ship=ship;}
    void markShot(){shot=true;}
    void restore(Ship ship,boolean shot){this.ship=ship;this.shot=shot;}
}
