package cm.kametud.requestservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Ancien import Boot 3, devenu inutile car la classe principale est déjà
// dans le package racine cm.kametud.requestservice :
// import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "cm.kametud.requestservice")
// Ancienne annotation Boot 3 :
// @EntityScan("cm.kametud.requestservice.model")
@EnableJpaRepositories("cm.kametud.requestservice.repository")
public class RequestServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RequestServiceApplication.class, args);
    }
}
