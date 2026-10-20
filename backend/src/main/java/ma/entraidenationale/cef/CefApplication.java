package ma.entraidenationale.cef;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CefApplication {

    public static void main(String[] args) {
        SpringApplication.run(CefApplication.class, args);
    }
}
