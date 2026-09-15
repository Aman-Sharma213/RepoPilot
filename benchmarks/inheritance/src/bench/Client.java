package bench;

class Client {
    private final Base base = new Child();

    void run() {
        base.process();
    }
}
