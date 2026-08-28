package br.com.controlegastos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ControleGastosApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControleGastosApplication.class, args);
    }
}
