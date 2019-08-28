package co.elastic.apm.agent.error.logging.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/java-util-logging")
public class JavaUtilLoggingController {

    private static final Logger logger = Logger.getLogger(JavaUtilLoggingController.class.getName());

    @GetMapping("/error")
    public ResponseEntity logError() throws IOException {
        try {
            throw new RuntimeException("some business util exception");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "call exception", e);
        }
        return new ResponseEntity(HttpStatus.OK);
    }

}
