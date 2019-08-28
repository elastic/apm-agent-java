package co.elastic.apm.agent.error.logging.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/slf4j")
public class Slf4jController {

    private static final Logger logger = LoggerFactory.getLogger(JavaUtilLoggingController.class);

    @GetMapping("/error")
    public ResponseEntity logError() {
        logger.debug("hello from me");
        try {
            throw new RuntimeException("some business exception");
        } catch (Exception e) {
            logger.error("exception captured", e);
        }
        return new ResponseEntity(HttpStatus.OK);
    }
}
