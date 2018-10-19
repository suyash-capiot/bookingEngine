package com.coxandkings.travel.bookingengine.eticket.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
 
import javax.servlet.ServletContext;

import com.coxandkings.travel.bookingengine.eticket.EticketTemplate;
import  com.coxandkings.travel.bookingengine.eticket.controller.MediaTypeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
 
@RestController
@RequestMapping(path = "/api/v1")
public class EticketDownloadController {
 
    private static final String DIRECTORY = "/tmp/ETicket";
    @Autowired
    private ServletContext servletContext;
    
    @Autowired
	private EticketTemplate bookingID;
 
    // http://localhost:8080/download1?fileName=abc.zip
    // Using ResponseEntity<InputStreamResource>
    @GetMapping("/eticket/download/{bookingID}")
    public ResponseEntity<InputStreamResource> downloadFile1(
    		@PathVariable("bookingID") String fileName) throws IOException {
    	
        MediaType mediaType = MediaTypeUtils.getMediaTypeForFileName(this.servletContext, fileName.concat(".pdf"));
         
        File file = new File(DIRECTORY + "/" + fileName.concat(".pdf"));
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
 
        return ResponseEntity.ok()
                // Content-Disposition
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + file.getName())
                // Content-Type
                .contentType(mediaType)
                // Contet-Length
                .contentLength(file.length()) //
                .body(resource);
    }
 
}