package bench;

class Dispatcher {
    private final Handler handler = new FileHandler();

    void dispatch() {
        handler.handle();
    }
}
