public class UserRunnable implements Runnable {
    @Override
    public void run() {
        try {
            System.out.println("New thread working");
Thread.sleep(10000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
