package com.hmwssb.works.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> home() {
        String html = "<!doctype html><html><head><meta charset='utf-8'><title>Works</title></head>"
                + "<body><h1>Works API</h1>"
                + "<p>Available endpoint: <a href=\"/api/items/search?q=demo\">/api/items/search</a></p>"
                + "</body></html>";
        return ResponseEntity.ok(html);
    }
}
