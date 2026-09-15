package bench;

class Caller {
    private final Over over = new Over();

    void callInt() {
        over.take(1);
    }

    void callString() {
        over.take("text");
    }
}
