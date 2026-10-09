package ar.edu.itba.dps.certification.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point. Scans only {@code ...certification.app}, never the core. */
@SpringBootApplication
public class CertiflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(CertiflowApplication.class, args);
    }
}
