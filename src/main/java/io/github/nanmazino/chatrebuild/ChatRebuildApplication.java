package io.github.nanmazino.chatrebuild;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChatRebuildApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatRebuildApplication.class, args);
    }

}
