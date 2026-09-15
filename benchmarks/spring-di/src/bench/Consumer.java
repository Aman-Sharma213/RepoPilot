package bench;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Consumer {
    @Autowired
    private Api api;

    public void run() {
        api.doWork();
    }
}
