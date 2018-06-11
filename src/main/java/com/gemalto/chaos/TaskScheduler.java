package com.gemalto.chaos;

import com.gemalto.chaos.container.Container;
import com.gemalto.chaos.notification.NotificationMethods;
import com.gemalto.chaos.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Component
public class TaskScheduler {
    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    @Autowired(required = false)
    private List<Platform> platforms;

    @Autowired
    private List<NotificationMethods> notificationMethods;


    @Scheduled(cron = "${schedule:0 0 * * * *}")
    public void chaosSchedule() {
        log.info("The time is now {}", dateFormat.format(new Date()));
        log.info("This is the list of platforms: {}", platforms);
        for (Platform platform : platforms) {
            try {
                List<Container> containers = platform.getRoster();
                if (containers != null && !containers.isEmpty()) {
                    for (Container container : containers) {
                        platform.destroy(container);
                        sendNotification("Destroyed container " + container);
                    }
                }
            } catch (Exception e) {
                log.error("Execution failed while processing {}", platform);
            }
        }
    }

    private void sendNotification(String event) {
        for (NotificationMethods notif : notificationMethods) {
            notif.logEvent(event);
        }
    }
}
